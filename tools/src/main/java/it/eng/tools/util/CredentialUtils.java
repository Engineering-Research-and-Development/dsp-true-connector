package it.eng.tools.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Utility class for getting connector credentials.
 *
 * <p>This implementation provides two authentication modes:
 * <ul>
 *   <li>VP JWT authentication (when DCP is enabled via dcp.vp.enabled=true)</li>
 *   <li>Basic authentication (fallback mode)</li>
 * </ul>
 *
 * <p>When VP JWT is enabled, this service:
 * <ul>
 *   <li>Resolves the target system's DID from the forwardTo URL</li>
 *   <li>Fetches the DID document from /.well-known/did.json</li>
 *   <li>Caches DID documents in memory to reduce network calls</li>
 *   <li>Extracts the DID from the document</li>
 *   <li>Calls SelfIssuedIdTokenService to generate JWT tokens</li>
 * </ul>
 */
@Component
public class CredentialUtils {

	private static final Logger logger = LoggerFactory.getLogger(CredentialUtils.class);

	private final OkHttpClient okHttpClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	// DCP services for JWT token generation
	private final SelfIssuedIdTokenService selfIssuedIdTokenService;
	private final BaseDidDocumentConfiguration didDocumentConfig;

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

	@Autowired
	public CredentialUtils(OkHttpClient okHttpClient,
	                       SelfIssuedIdTokenService selfIssuedIdTokenService,
	                       BaseDidDocumentConfiguration didDocumentConfig) {
		this.okHttpClient = okHttpClient;
		this.selfIssuedIdTokenService = selfIssuedIdTokenService;
		this.didDocumentConfig = didDocumentConfig;
	}



	/**
	 * Get connector credentials for protocol communication.
	 *
	 * <p>Automatically uses VP JWT if DCP services are available and enabled,
	 * otherwise returns basic authentication credentials.
	 *
	 * @return Authorization header value ("Bearer {VP_JWT}" or "Basic {base64}")
	 */
	public String getConnectorCredentials() {
		return getConnectorCredentials(null);
	}

	/**
	 * Get connector credentials for protocol communication to a specific target.
	 *
	 * <p>This method performs the following steps when VP JWT is enabled:
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
	 * @param targetUrl The full URL where the request will be sent (e.g., "https://verifier.com/catalog/request")
	 * @return Authorization header value ("Bearer {JWT}" or "Basic {base64}")
	 */
	public String getConnectorCredentials(String targetUrl) {
		// Try to use VP JWT if DCP services are available and enabled
		if (vpEnabled && selfIssuedIdTokenService != null && didDocumentConfig != null) {
			try {
				String audienceDid = null;

				// If targetUrl is provided, resolve the DID from the DID document
				if (targetUrl != null && !targetUrl.isBlank()) {
					audienceDid = resolveDidFromUrl(targetUrl);

					if (audienceDid == null) {
						logger.warn("Failed to resolve DID from target URL: {} - falling back to basic auth", targetUrl);
						return getFallbackCredentials();
					}
				} else {
					logger.warn("No target URL provided for VP JWT generation - falling back to basic auth");
					return getFallbackCredentials();
				}

				// Parse scopes from configuration
				String[] scopes = parseScopes(vpScope);

				// Generate JWT token using SelfIssuedIdTokenService
				logger.debug("Generating VP JWT token for audience DID: {}", audienceDid);
				String token = selfIssuedIdTokenService.createStsCompatibleToken(
					audienceDid,
					didDocumentConfig.getDidDocumentConfig(),
					scopes
				);

				if (token != null && !token.isBlank()) {
					logger.info("Successfully generated VP JWT token for audience: {}", audienceDid);
					return "Bearer " + token;
				} else {
					logger.warn("VP JWT generation returned null or empty - falling back to basic auth");
				}

			} catch (Exception e) {
				logger.error("Failed to generate VP JWT - falling back to basic auth: {}", e.getMessage(), e);
			}
		}

		// Fallback to basic authentication
		return getFallbackCredentials();
	}


	/**
	 * Parses scopes from configuration string.
	 *
	 * @param vpScope Comma-separated scope string
	 * @return Array of scopes
	 */
	private String[] parseScopes(String vpScope) {
		if (vpScope == null || vpScope.trim().isEmpty()) {
			logger.debug("No scopes configured - token will grant access to all presentations");
			return new String[0];
		}

		String[] scopes = vpScope.split(",");
		for (int i = 0; i < scopes.length; i++) {
			scopes[i] = scopes[i].trim();
		}

		logger.debug("Configured scopes: {}", String.join(", ", scopes));
		return scopes;
	}

	/**
	 * Returns fallback credentials (basic auth).
	 *
	 * @return Basic authentication credentials
	 */
	private String getFallbackCredentials() {
		logger.debug("Using basic authentication for connector credentials");
		return okhttp3.Credentials.basic("connector@mail.com", "password");
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
			// Step 1: Normalize the URL to get base URL
			String baseUrl = normalizeUrl(targetUrl);

			// Step 2: Construct DID document URL
			String didDocumentUrl = baseUrl + "/.well-known/did.json";

			// Step 3: Check cache first
			CachedDidDocument cached = didDocumentCache.get(didDocumentUrl);
			Instant now = Instant.now();

			if (cached != null && cached.expiresAt.isAfter(now)) {
				logger.debug("Using cached DID for URL {}: {}", didDocumentUrl, cached.did);
				return cached.did;
			}

			// Step 4: Fetch DID document from remote system
			logger.debug("Fetching DID document from: {}", didDocumentUrl);
			String did = fetchDidFromDocument(didDocumentUrl);

			if (did != null) {
				// Cache the result
				Instant expiresAt = now.plusSeconds(cacheTtlSeconds);
				didDocumentCache.put(didDocumentUrl, new CachedDidDocument(did, expiresAt));
				logger.info("Successfully resolved and cached DID from {}: {}", didDocumentUrl, did);
				return did;
			} else {
				logger.warn("DID document fetched but 'id' field is missing or null");
				return null;
			}

		} catch (Exception e) {
			logger.error("Failed to resolve DID from URL {}: {}", targetUrl, e.getMessage(), e);
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
			} else {
				return scheme + "://" + host;
			}
		} catch (Exception e) {
			logger.error("Failed to normalize URL {}: {}", url, e.getMessage());
			// Fallback: try simple string manipulation
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
				logger.warn("Failed to fetch DID document from {}: HTTP {}", didDocumentUrl, response.code());
				return null;
			}

			if (response.body() == null) {
				logger.warn("DID document response body is null for URL: {}", didDocumentUrl);
				return null;
			}

			String responseBody = response.body().string();
			logger.debug("DID document fetched successfully from {}", didDocumentUrl);

			// Parse JSON and extract "id" field
			Map<String, Object> didDocument = objectMapper.readValue(responseBody, Map.class);
			String did = (String) didDocument.get("id");

			if (did == null || did.isBlank()) {
				logger.warn("DID document does not contain an 'id' field or it is empty");
				return null;
			}

			return did;

		} catch (IOException e) {
			logger.error("Error fetching DID document from {}: {}", didDocumentUrl, e.getMessage());
			return null;
		}
	}

	public String getAPICredentials() {
		// get from users or from property file instead hardcoded
		 return okhttp3.Credentials.basic("admin@mail.com", "password");
	}
}
