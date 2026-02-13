package it.eng.tools.auth;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;

import it.eng.tools.auth.daps.DapsAuthenticationProperties;
import it.eng.tools.auth.daps.DapsAuthenticationService;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationProperties;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;
import lombok.extern.slf4j.Slf4j;

/**
 * Cache for authentication tokens with automatic expiration handling.
 * Supports multiple authentication providers (Keycloak, DAPS/Omejdn).
 */
@Slf4j
@Component
public class AuthenticationCache {

	public static final String DUMMY_TOKEN_VALUE = "DummyTokenValue";

	private final List<AuthProvider> authenticationProviders;
	private final DapsAuthenticationProperties dapsProperties;
	private final KeycloakAuthenticationProperties keycloakProperties;

	private String cachedToken;
	private LocalDateTime expirationTime;

	@Autowired(required = false)
	public AuthenticationCache(List<AuthProvider> authenticationProviders,
	                           @Autowired(required = false) DapsAuthenticationProperties dapsProperties,
	                           @Autowired(required = false) KeycloakAuthenticationProperties keycloakProperties) {
		this.authenticationProviders = authenticationProviders;
		this.dapsProperties = dapsProperties;
		this.keycloakProperties = keycloakProperties;
	}

	/**
	 * Retrieves an authentication token, either from cache or by fetching a new one.
	 *
	 * @return the authentication token, or a dummy token if no provider is configured
	 */
	public String getToken() {
		log.info("Requesting token");
		log.info("Available authentication providers: {}", authenticationProviders == null ? "null" : authenticationProviders.size());
		log.info("DAPS properties configured: {}", dapsProperties != null);
		log.info("Keycloak properties configured: {}", keycloakProperties != null);

		AuthProvider authProvider = selectAuthenticationProvider();
		log.info("Selected authentication provider: {}", authProvider == null ? "null" : authProvider.getClass().getSimpleName());

		boolean tokenCachingEnabled = isTokenCachingEnabled();
		log.info("Token caching enabled: {}", tokenCachingEnabled);

		if (authProvider == null) {
			log.info("No authentication provider configured - continuing with dummy token");
			return DUMMY_TOKEN_VALUE;
		}

		if (tokenCachingEnabled) {
			//Checking if cached token is still valid
			synchronized (this) {
				if (cachedToken == null || LocalDateTime.now().isAfter(expirationTime)) {
					log.info("Fetching new token");
					cachedToken = authProvider.fetchToken();
					if (cachedToken != null) {
						try {
							expirationTime = JWT.decode(cachedToken).getExpiresAt()
									.toInstant()
									.atZone(ZoneId.systemDefault())
								    .toLocalDateTime();
						} catch (JWTDecodeException e) {
							log.error("Could not get token expiration time {}", e.getMessage());
							//Setting to default values since the JWT token was not correct
							cachedToken = null;
							expirationTime = null;
						}
					}
				}
			}
			return cachedToken;
		} else {
			//Always new token
			return authProvider.fetchToken();
		}
	}

	/**
	 * Validates an authentication token using the active provider.
	 *
	 * @param token the token to validate
	 * @return true if the token is valid, false otherwise
	 */
	public boolean validateToken(String token) {
		AuthProvider authProvider = selectAuthenticationProvider();
		if (authProvider == null) {
			log.warn("No authentication provider available for token validation");
			return false;
		}
		return authProvider.validateToken(token);
	}

	/**
	 * Selects the appropriate AuthProvider based on configuration.
	 * Priority: Keycloak > DAPS/Omejdn
	 *
	 * @return the selected AuthProvider, or null if none is configured
	 */
	private AuthProvider selectAuthenticationProvider() {
		if (authenticationProviders == null || authenticationProviders.isEmpty()) {
			return null;
		}

		// Prefer Keycloak if configured
		if (keycloakProperties != null) {
			return authenticationProviders.stream()
					.filter(s -> s instanceof KeycloakAuthenticationService)
					.findFirst()
					.orElse(null);
		}

		// Fall back to DAPS/Omejdn
		if (dapsProperties != null && dapsProperties.isEnabledDapsInteraction()) {
			return authenticationProviders.stream()
					.filter(s -> s instanceof DapsAuthenticationService)
					.findFirst()
					.orElse(null);
		}

		return null;
	}

	/**
	 * Checks if token caching is enabled based on the active service.
	 *
	 * @return true if caching is enabled, false otherwise
	 */
	private boolean isTokenCachingEnabled() {
		if (keycloakProperties != null) {
			return keycloakProperties.isTokenCaching();
		}
		if (dapsProperties != null) {
			return dapsProperties.isTokenCaching();
		}
		return false;
	}
}




