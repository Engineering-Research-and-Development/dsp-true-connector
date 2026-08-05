package it.eng.tools.util;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import it.eng.tools.auth.AuthenticationCache;
import it.eng.tools.auth.ConnectorCredentialProvider;
import it.eng.tools.auth.M2mTokenCache;
import it.eng.tools.auth.internal.InternalServiceTokenIssuer;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for retrieving authentication credentials.
 */
@Slf4j
@Component
public class CredentialUtils {

	/** {@link M2mTokenCache} slot for the connector-to-connector M2M token (see {@link #getConnectorCredentials()}). */
	static final String CONNECTOR_M2M_CACHE_KEY = "connector-m2m";

	/** {@link M2mTokenCache} slot for the internal-service M2M token (see {@link #getAPICredentials()}). */
	static final String INTERNAL_API_CACHE_KEY = "internal-api";

	private final AuthenticationCache authenticationCache;
	private final M2mTokenCache m2mTokenCache;
	private final ObjectProvider<ConnectorCredentialProvider> connectorCredentialProvider;
	private final ObjectProvider<InternalServiceTokenIssuer> internalServiceTokenIssuer;

	/**
	 * Constructs the credential utils with the required authentication cache.
	 *
	 * @param authenticationCache         the cache holding the current Keycloak token
	 * @param m2mTokenCache               the cache holding INTERNAL-mode M2M JWTs
	 * @param connectorCredentialProvider optional {@code connector}-module adapter that issues a
	 *                                    connector-to-connector token in INTERNAL mode; empty in
	 *                                    KEYCLOAK/DISABLED mode
	 * @param internalServiceTokenIssuer  optional issuer that mints internal-service tokens in
	 *                                    INTERNAL mode; empty in KEYCLOAK/DISABLED mode
	 */
	public CredentialUtils(AuthenticationCache authenticationCache, M2mTokenCache m2mTokenCache,
			ObjectProvider<ConnectorCredentialProvider> connectorCredentialProvider,
			ObjectProvider<InternalServiceTokenIssuer> internalServiceTokenIssuer) {
		this.authenticationCache = authenticationCache;
		this.m2mTokenCache = m2mTokenCache;
		this.connectorCredentialProvider = connectorCredentialProvider;
		this.internalServiceTokenIssuer = internalServiceTokenIssuer;
	}

	/**
	 * Retrieves connector credentials for connector-to-connector communication.
	 *
	 * <p>In {@code INTERNAL} mode, issues (and caches, via {@link M2mTokenCache}) a JWT for the
	 * real seeded {@code connector@mail.com} CONNECTOR-role Mongo user via {@link
	 * ConnectorCredentialProvider}. In Keycloak mode, uses the existing {@link AuthenticationCache}
	 * flow. If neither is available, falls back to Basic Auth, matching legacy behaviour.
	 *
	 * @return the Authorization header value
	 */
	public String getConnectorCredentials() {
		ConnectorCredentialProvider provider = connectorCredentialProvider.getIfAvailable();
		if (provider != null) {
			String token = m2mTokenCache.getOrFetch(CONNECTOR_M2M_CACHE_KEY, provider::issueConnectorToken);
			if (token != null) {
				return "Bearer " + token;
			}
		}
		String token = authenticationCache.getToken("ROLE_CONNECTOR");
		if (token == null) {
			// Fall back to basic auth if no token is available
			log.info("getConnectorCredentials() - No valid token available");
			//TODO consider to move users from connector to tools module so user can be loaded from Mongo and not hardcoded.
			return null;
		}
		return "Bearer " + token;
	}
	
	/**
	 * Retrieves API credentials for internal API calls.
	 *
	 * <p>In {@code INTERNAL} mode, issues (and caches, via {@link M2mTokenCache}) a JWT via {@link
	 * InternalServiceTokenIssuer}, whose principal has {@code tenantId=null}, allowing {@code
	 * ApiTenantContextFilter} to honour the {@code X-Tenant-Id} request header for correct
	 * multi-tenant routing. Otherwise falls back to the existing Keycloak {@link
	 * AuthenticationCache} flow.
	 *
	 * @return the Authorization header value
	 */
	public String getAPICredentials() {
		log.info("getAPICredentials() - Requesting credentials for internal API call");
		InternalServiceTokenIssuer issuer = internalServiceTokenIssuer.getIfAvailable();
		if (issuer != null) {
			String token = m2mTokenCache.getOrFetch(INTERNAL_API_CACHE_KEY, issuer::issueInternalServiceToken);
			if (token != null) {
				log.info("getAPICredentials() - Using internal-service JWT for authentication");
				return "Bearer " + token;
			}
		}
		String token = authenticationCache.getToken("ROLE_ADMIN");
		log.info("getAPICredentials() - Token from cache: {}", token);
		return "Bearer " + token;
	}

	/**
	 * Evicts any cached {@code INTERNAL}-mode M2M tokens (both {@link #getConnectorCredentials()}
	 * and {@link #getAPICredentials()} slots), forcing the next call to each method to mint a
	 * fresh token. Invoked after a downstream call receives an HTTP 401, so a stale/rotated secret
	 * or expired-early token is not retried indefinitely.
	 *
	 * <p>Evicting both slots unconditionally (rather than trying to determine which one is stale)
	 * is deliberate: it is harmless (a slot that was not actually stale simply gets refetched once,
	 * on its next use) and keeps the calling code in {@code OkHttpRestClient} decoupled from which
	 * cache key backs which credential method.
	 */
	public void invalidateCachedCredentials() {
		m2mTokenCache.invalidate(CONNECTOR_M2M_CACHE_KEY);
		m2mTokenCache.invalidate(INTERNAL_API_CACHE_KEY);
	}
}
