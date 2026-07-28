package it.eng.tools.util;

import org.springframework.stereotype.Component;

import it.eng.tools.auth.AuthenticationCache;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for retrieving authentication credentials.
 */
@Slf4j
@Component
public class CredentialUtils {

	private final AuthenticationCache authenticationCache;

	/**
	 * Constructs the credential utils with the required authentication cache.
	 *
	 * @param authenticationCache the cache holding the current Bearer token
	 */
	public CredentialUtils(AuthenticationCache authenticationCache) {
		this.authenticationCache = authenticationCache;
	}

	/**
	 * Retrieves connector credentials for connector-to-connector communication.
	 * Uses JWT token from Keycloak/INTERNAL/DISABLED.
	 *
	 * @return Bearer token authorization header
	 */
	public String getConnectorCredentials() {
		String token = authenticationCache.getToken("ROLE_CONNECTOR");
		if (token == null || AuthenticationCache.DUMMY_TOKEN_VALUE.equals(token)) {
			// Fall back to basic auth if no token is available
			log.info("getConnectorCredentials() - No valid token available, falling back to Basic Auth");
			//TODO consider to move users from connector to tools module so user can be loaded from Mongo and not hardcoded.
			return okhttp3.Credentials.basic("connector@mail.com", "password");
		}
		return "Bearer " + token;
	}
	
	/**
	 * Retrieves API credentials for internal API calls.
	 *
	 * <p>When a valid Bearer token is available (Keycloak mode), it is returned as an
	 * {@code Authorization: Bearer} header value. Otherwise, Basic Auth credentials are
	 * built using the {@code internal-service} username and the configured shared secret,
	 * which maps to {@link it.eng.connector.configuration.InternalServiceAuthenticationProvider}
	 * in INTERNAL mode. Using the internal-service account ensures the principal has
	 * {@code tenantId=null}, allowing {@code ApiTenantContextFilter} to honour the
	 * {@code X-Tenant-Id} request header for correct multi-tenant routing.
	 *
	 * @return the Authorization header value (Bearer token or Basic auth)
	 */
	public String getAPICredentials() {
		log.info("getAPICredentials() - Requesting credentials for internal API call");
		String token = authenticationCache.getToken("ROLE_ADMIN");
		log.info("getAPICredentials() - Token from cache: {}", token == null ? "null" :
			(AuthenticationCache.DUMMY_TOKEN_VALUE.equals(token) ? "DUMMY_TOKEN" : "JWT token (length: " + token.length() + ")"));

		log.info("getAPICredentials() - Using Bearer token for authentication");
		return "Bearer " + token;
	}
}
