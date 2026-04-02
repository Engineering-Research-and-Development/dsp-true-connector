package it.eng.tools.auth;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;

import it.eng.tools.auth.keycloak.KeycloakAuthenticationProperties;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;
import lombok.extern.slf4j.Slf4j;

/**
 * Cache for outbound authentication tokens with automatic expiration handling.
 * Supports the Keycloak OAuth2 client credentials flow.
 */
@Slf4j
@Component
public class AuthenticationCache {

	public static final String DUMMY_TOKEN_VALUE = "DummyTokenValue";

	private final List<AuthProvider> authenticationProviders;
	private final KeycloakAuthenticationProperties keycloakProperties;

	private volatile String cachedToken;
	private volatile LocalDateTime expirationTime;

	@Autowired(required = false)
	public AuthenticationCache(List<AuthProvider> authenticationProviders,
	                           @Autowired(required = false) KeycloakAuthenticationProperties keycloakProperties) {
		this.authenticationProviders = authenticationProviders;
		this.keycloakProperties = keycloakProperties;
	}

	/**
	 * Retrieves an authentication token, either from cache or by fetching a new one.
	 *
	 * @return the authentication token, or a dummy token if no provider is configured
	 */
	public String getToken() {
		log.info("Requesting outbound authentication token");

		AuthProvider authProvider = selectAuthenticationProvider();
		log.info("Selected authentication provider: {}", authProvider == null ? "none" : authProvider.getClass().getSimpleName());

		boolean tokenCachingEnabled = isTokenCachingEnabled();
		log.info("Token caching enabled: {}", tokenCachingEnabled);

		if (authProvider == null) {
			log.info("No authentication provider configured - continuing with dummy token");
			return DUMMY_TOKEN_VALUE;
		}

		if (tokenCachingEnabled) {
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
							// Setting to default values since the JWT token was not correct
							cachedToken = null;
							expirationTime = null;
						}
					}
				}
				return cachedToken;
			}
		} else {
			// Always fetch a fresh token
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
	 * Selects the active {@link AuthProvider}.
	 *
	 * @return the Keycloak provider when configured, or {@code null} if none is available
	 */
	private AuthProvider selectAuthenticationProvider() {
		if (authenticationProviders == null || authenticationProviders.isEmpty()) {
			return null;
		}
		if (keycloakProperties != null) {
			return authenticationProviders.stream()
					.filter(KeycloakAuthenticationService.class::isInstance)
					.findFirst()
					.orElse(null);
		}
		return null;
	}

	/**
	 * Checks if token caching is enabled based on the active provider configuration.
	 *
	 * @return true if caching is enabled, false otherwise
	 */
	private boolean isTokenCachingEnabled() {
		if (keycloakProperties != null) {
			return keycloakProperties.isTokenCaching();
		}
		return false;
	}
}
