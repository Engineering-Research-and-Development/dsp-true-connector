package it.eng.tools.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.tools.auth.ConnectorCredentialProvider;
import it.eng.tools.auth.M2mTokenCache;
import it.eng.tools.auth.M2mTokenUnavailableException;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Utility class for retrieving authentication credentials.
 *
 * <p>Connector-to-connector credentials come from one of these sources:
 * <ul>
 *   <li>DCP self-issued ID token (when {@code dcp.vp.enabled=true} and a target URL is given): the
 *       target's DID is resolved from {@code {baseUrl}/.well-known/did.json} (cached in memory) and
 *       used as the audience of a token from {@link SelfIssuedIdTokenService}.</li>
 *   <li>{@code INTERNAL} mode: a locally minted protocol JWT from {@link ConnectorCredentialProvider}.</li>
 *   <li>{@code KEYCLOAK} mode: the {@code platform} realm client credentials token from
 *       {@link KeycloakAuthenticationService}.</li>
 * </ul>
 * When DCP is disabled, or the DCP token cannot be produced, the provider-based credentials are used.
 */
@Slf4j
@Component
public class CredentialUtils {

	/** {@link M2mTokenCache} slot for the connector-to-connector M2M token (see {@link #getConnectorCredentials()}). */
	static final String CONNECTOR_M2M_CACHE_KEY = "connector-m2m";

	private final OkHttpClient okHttpClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	// DCP services for self-issued ID token generation
	private final SelfIssuedIdTokenService selfIssuedIdTokenService;
	private final BaseDidDocumentConfiguration didDocumentConfig;

	private final M2mTokenCache m2mTokenCache;
	private final ObjectProvider<ConnectorCredentialProvider> connectorCredentialProvider;
	private final ObjectProvider<KeycloakAuthenticationService> keycloakAuthenticationService;

	/**
	 * Cached DID document entry with expiry time.
	 */
	private record CachedDidDocument(String did, Instant expiresAt) { }

	/**
	 * In-memory cache for DID documents.
	 * Key: DID document URL (e.g., https://example.com/.well-known/did.json)
	 * Value: Cached DID and expiry time
	 */
	private final ConcurrentMap<String, CachedDidDocument> didDocumentCache = new ConcurrentHashMap<>();

	/**
	 * Cache TTL in seconds. Default: 300 seconds (5 minutes).
	 */
	@Value("${dcp.did.cache.ttl:300}")
	private long cacheTtlSeconds = 300;

	@Value("${server.ssl.enabled:false}")
	private boolean sslEnabled;

	@Value("${dcp.vp.enabled:false}")
	private boolean vpEnabled;

	@Value("${dcp.vp.scope:}")
	private String vpScope;

	/**
	 * Constructs the credential utils.
	 *
	 * @param okHttpClient                  HTTP client used to fetch DID documents (DCP)
	 * @param selfIssuedIdTokenService      DCP service that issues self-issued ID tokens
	 * @param didDocumentConfig             DCP DID document configuration of this connector
	 * @param m2mTokenCache                 the cache holding INTERNAL-mode M2M JWTs
	 * @param connectorCredentialProvider   optional issuer of the locally minted connector-to-connector
	 *                                      token in INTERNAL mode; empty in KEYCLOAK/DISABLED mode
	 * @param keycloakAuthenticationService optional outbound Keycloak client; present in KEYCLOAK mode only
	 */
	@Autowired
	public CredentialUtils(OkHttpClient okHttpClient,
			SelfIssuedIdTokenService selfIssuedIdTokenService,
			BaseDidDocumentConfiguration didDocumentConfig,
			M2mTokenCache m2mTokenCache,
			ObjectProvider<ConnectorCredentialProvider> connectorCredentialProvider,
			ObjectProvider<KeycloakAuthenticationService> keycloakAuthenticationService) {
		this.okHttpClient = okHttpClient;
		this.selfIssuedIdTokenService = selfIssuedIdTokenService;
		this.didDocumentConfig = didDocumentConfig;
		this.m2mTokenCache = m2mTokenCache;
		this.connectorCredentialProvider = connectorCredentialProvider;
		this.keycloakAuthenticationService = keycloakAuthenticationService;
	}

	/**
	 * Retrieves connector credentials for connector-to-connector communication.
	 *
	 * <p>In {@code INTERNAL} mode, issues (and caches, via {@link M2mTokenCache}) a locally minted
	 * protocol JWT from {@link ConnectorCredentialProvider}: it carries only the {@code CONNECTOR} role,
	 * with no tenant, user or email, and is signed with {@code application.security.jwt.secret}. In {@code KEYCLOAK} mode, returns the client credentials token of
	 * the {@code platform} realm from {@link KeycloakAuthenticationService#getConnectorToken()}. If
	 * neither is available, returns {@code null} and the request is sent without credentials.
	 *
	 * @return the Authorization header value, or {@code null} when no credential source is configured
	 * @throws M2mTokenUnavailableException in {@code KEYCLOAK} mode, when no token could be obtained
	 */
	public String getConnectorCredentials() {
		ConnectorCredentialProvider provider = connectorCredentialProvider.getIfAvailable();
		if (provider != null) {
			String token = m2mTokenCache.getOrFetch(CONNECTOR_M2M_CACHE_KEY, provider::issueConnectorToken);
			if (token != null) {
				return "Bearer " + token;
			}
		}
		KeycloakAuthenticationService keycloak = keycloakAuthenticationService.getIfAvailable();
		if (keycloak != null) {
			return "Bearer " + keycloak.getConnectorToken();
		}
		log.info("getConnectorCredentials() - No valid token available");
		return null;
	}

	/**
	 * Retrieves connector credentials for protocol communication to a specific target.
	 *
	 * <p>When DCP is enabled ({@code dcp.vp.enabled=true}):
	 * <ol>
	 *   <li>Normalizes the target URL (strip paths, keep only base URL)</li>
	 *   <li>Fetches DID document from {baseUrl}/.well-known/did.json (with caching)</li>
	 *   <li>Extracts the DID from the document's "id" field</li>
	 *   <li>Calls SelfIssuedIdTokenService.createStsCompatibleToken with the audience DID</li>
	 * </ol>
	 *
	 * <p>Example flow:
	 * <ul>
	 *   <li>Input: https://verifier.com:8080/catalog/request</li>
	 *   <li>Normalized: https://verifier.com:8080</li>
	 *   <li>DID document URL: https://verifier.com:8080/.well-known/did.json</li>
	 *   <li>Extract DID: did:web:verifier.com:8080 (from document's "id" field)</li>
	 *   <li>Generate JWT with audience = did:web:verifier.com:8080</li>
	 * </ul>
	 *
	 * <p>DID documents are cached in memory with configurable TTL (default 5 minutes)
	 * to reduce network overhead. The cache key is the DID document URL.
	 *
	 * <p>When DCP is disabled, or the DCP token cannot be produced, falls back to
	 * {@link #getConnectorCredentials()}.
	 *
	 * @param targetUrl The full URL where the request will be sent (e.g., "https://verifier.com/catalog/request")
	 * @return Authorization header value, or {@code null} when no credential source is configured
	 * @throws M2mTokenUnavailableException in {@code KEYCLOAK} mode, when the fallback token could not be obtained
	 */
	public String getConnectorCredentials(String targetUrl) {
		if (vpEnabled && selfIssuedIdTokenService != null && didDocumentConfig != null) {
			try {
				if (targetUrl == null || targetUrl.isBlank()) {
					log.warn("No target URL provided for VP JWT generation - falling back to provider credentials");
					return getConnectorCredentials();
				}

				String audienceDid = resolveDidFromUrl(targetUrl);
				if (audienceDid == null) {
					log.warn("Failed to resolve DID from target URL: {} - falling back to provider credentials", targetUrl);
					return getConnectorCredentials();
				}

				String[] scopes = parseScopes(vpScope);

				log.debug("Generating VP JWT token for audience DID: {}", audienceDid);
				String token = selfIssuedIdTokenService.createStsCompatibleToken(
						audienceDid,
						didDocumentConfig.getDidDocumentConfig(),
						scopes
				);

				if (token != null && !token.isBlank()) {
					log.info("Successfully generated VP JWT token for audience: {}", audienceDid);
					return "Bearer " + token;
				}
				log.warn("VP JWT generation returned null or empty - falling back to provider credentials");
			} catch (Exception e) {
				log.error("Failed to generate VP JWT - falling back to provider credentials: {}", e.getMessage(), e);
			}
		}
		return getConnectorCredentials();
	}

	/**
	 * Evicts the cached connector-to-connector tokens (see {@link #getConnectorCredentials()}): the
	 * {@code INTERNAL} mode slot and, in {@code KEYCLOAK} mode, the outbound Keycloak entry. The next call
	 * obtains a fresh token. Invoked after a downstream call receives an HTTP 401, so a stale/rotated
	 * secret or expired-early token is not retried indefinitely.
	 */
	public void invalidateCachedCredentials() {
		m2mTokenCache.invalidate(CONNECTOR_M2M_CACHE_KEY);
		KeycloakAuthenticationService keycloak = keycloakAuthenticationService.getIfAvailable();
		if (keycloak != null) {
			keycloak.invalidateConnectorToken();
		}
	}

	/**
	 * Parses scopes from configuration string.
	 *
	 * @param vpScope Comma-separated scope string
	 * @return Array of scopes
	 */
	private String[] parseScopes(String vpScope) {
		if (vpScope == null || vpScope.trim().isEmpty()) {
			log.debug("No scopes configured - token will grant access to all presentations");
			return new String[0];
		}

		String[] scopes = vpScope.split(",");
		for (int i = 0; i < scopes.length; i++) {
			scopes[i] = scopes[i].trim();
		}

		log.debug("Configured scopes: {}", String.join(", ", scopes));
		return scopes;
	}

	/**
	 * Resolves the DID from a target URL by fetching the DID document.
	 *
	 * <p>Process:
	 * <ol>
	 *   <li>Normalize the URL (e.g., https://example.com:8080/path → https://example.com:8080)</li>
	 *   <li>Fetch DID document from {baseUrl}/.well-known/did.json</li>
	 *   <li>Extract and return the "id" field from the document</li>
	 * </ol>
	 *
	 * <p>DID documents are cached with TTL to minimize network calls.
	 *
	 * @param targetUrl The target URL (e.g., "https://verifier.com:8080/catalog/request")
	 * @return The DID (e.g., "did:web:verifier.com:8080") or null if resolution fails
	 */
	private String resolveDidFromUrl(String targetUrl) {
		try {
			String baseUrl = normalizeUrl(targetUrl);
			String didDocumentUrl = baseUrl + "/.well-known/did.json";

			CachedDidDocument cached = didDocumentCache.get(didDocumentUrl);
			Instant now = Instant.now();

			if (cached != null && cached.expiresAt.isAfter(now)) {
				log.debug("Using cached DID for URL {}: {}", didDocumentUrl, cached.did);
				return cached.did;
			}

			log.debug("Fetching DID document from: {}", didDocumentUrl);
			String did = fetchDidFromDocument(didDocumentUrl);

			if (did != null) {
				Instant expiresAt = now.plusSeconds(cacheTtlSeconds);
				didDocumentCache.put(didDocumentUrl, new CachedDidDocument(did, expiresAt));
				log.info("Successfully resolved and cached DID from {}: {}", didDocumentUrl, did);
				return did;
			}
			log.warn("DID document fetched but 'id' field is missing or null");
			return null;
		} catch (Exception e) {
			log.error("Failed to resolve DID from URL {}: {}", targetUrl, e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Normalizes a URL to extract the base URL (protocol + host + port).
	 *
	 * <p>Examples:
	 * <ul>
	 *   <li>https://example.com:8080/catalog/request → https://example.com:8080</li>
	 *   <li>http://localhost/dsp → http://localhost</li>
	 * </ul>
	 *
	 * @param url The full URL
	 * @return The base URL (protocol + host + port)
	 */
	private String normalizeUrl(String url) {
		try {
			URI uri = URI.create(url);
			String scheme = uri.getScheme() != null ? uri.getScheme() : (sslEnabled ? "https" : "http");
			String host = uri.getHost();
			int port = uri.getPort();

			if (port != -1 && port != 80 && port != 443) {
				return scheme + "://" + host + ":" + port;
			}
			return scheme + "://" + host;
		} catch (Exception e) {
			log.error("Failed to normalize URL {}: {}", url, e.getMessage());
			int pathStart = url.indexOf('/', url.indexOf("://") + 3);
			if (pathStart > 0) {
				return url.substring(0, pathStart);
			}
			return url;
		}
	}

	/**
	 * Fetches the DID from a DID document URL.
	 *
	 * @param didDocumentUrl The DID document URL (e.g., https://example.com/.well-known/did.json)
	 * @return The DID extracted from the document's "id" field, or null if fetch fails
	 */
	private String fetchDidFromDocument(String didDocumentUrl) {
		Request request = new Request.Builder()
				.url(didDocumentUrl)
				.get()
				.build();

		try (Response response = okHttpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				log.warn("Failed to fetch DID document from {}: HTTP {}", didDocumentUrl, response.code());
				return null;
			}

			if (response.body() == null) {
				log.warn("DID document response body is null for URL: {}", didDocumentUrl);
				return null;
			}

			String responseBody = response.body().string();
			log.debug("DID document fetched successfully from {}", didDocumentUrl);

			@SuppressWarnings("unchecked")
			Map<String, Object> didDocument = objectMapper.readValue(responseBody, Map.class);
			String did = (String) didDocument.get("id");

			if (did == null || did.isBlank()) {
				log.warn("DID document does not contain an 'id' field or it is empty");
				return null;
			}

			return did;
		} catch (IOException e) {
			log.error("Error fetching DID document from {}: {}", didDocumentUrl, e.getMessage());
			return null;
		}
	}
}
