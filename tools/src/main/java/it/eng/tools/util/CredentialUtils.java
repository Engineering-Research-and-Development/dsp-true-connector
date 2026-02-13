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

	public CredentialUtils(AuthenticationCache authenticationCache) {
		this.authenticationCache = authenticationCache;
	}

	/**
	 * Retrieves connector credentials for connector-to-connector communication.
	 * Uses JWT token from DAPS or Keycloak.
	 *
	 * @return Bearer token authorization header
	 */
	public String getConnectorCredentials() {
		String token = authenticationCache.getToken();
		if (token == null || AuthenticationCache.DUMMY_TOKEN_VALUE.equals(token)) {
			// Fall back to basic auth if no token is available
			log.info("getConnectorCredentials() - No valid token available, falling back to Basic Auth");
			return okhttp3.Credentials.basic("connector@mail.com", "password");
		}
		return "Bearer " + token;
	}
	
	/**
	 * Retrieves API credentials for internal API calls.
	 * Uses JWT token from Keycloak when enabled, otherwise falls back to Basic auth.
	 *
	 * @return Authorization header (Bearer token or Basic auth)
	 */
	public String getAPICredentials() {
		log.info("getAPICredentials() - Requesting credentials for internal API call");
		String token = authenticationCache.getToken();
		log.info("getAPICredentials() - Token from cache: {}", token == null ? "null" :
			(AuthenticationCache.DUMMY_TOKEN_VALUE.equals(token) ? "DUMMY_TOKEN" : "JWT token (length: " + token.length() + ")"));

		if (token == null || AuthenticationCache.DUMMY_TOKEN_VALUE.equals(token)) {
			// Fall back to basic auth if no token is available (when Keycloak is disabled)
			log.warn("getAPICredentials() - No valid token available, falling back to Basic Auth");
			return okhttp3.Credentials.basic("admin@mail.com", "password");
		}
		log.info("getAPICredentials() - Using Bearer token for authentication");
		return "Bearer " + token;
	}
}
