package it.eng.tools.util;

import it.eng.tools.auth.ConnectorCredentialProvider;
import it.eng.tools.auth.M2mTokenCache;
import it.eng.tools.auth.M2mTokenUnavailableException;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Utility class for retrieving authentication credentials.
 */
@Slf4j
@Component
public class CredentialUtils {

	/** {@link M2mTokenCache} slot for the connector-to-connector M2M token (see {@link #getConnectorCredentials()}). */
	static final String CONNECTOR_M2M_CACHE_KEY = "connector-m2m";

	private final M2mTokenCache m2mTokenCache;
	private final ObjectProvider<ConnectorCredentialProvider> connectorCredentialProvider;
	private final ObjectProvider<KeycloakAuthenticationService> keycloakAuthenticationService;

	/**
	 * Constructs the credential utils.
	 *
	 * @param m2mTokenCache                 the cache holding INTERNAL-mode M2M JWTs
	 * @param connectorCredentialProvider   optional issuer of the locally minted connector-to-connector
	 *                                      token in INTERNAL mode; empty in KEYCLOAK/DISABLED mode
	 * @param keycloakAuthenticationService optional outbound Keycloak client; present in KEYCLOAK mode only
	 */
	public CredentialUtils(M2mTokenCache m2mTokenCache,
			ObjectProvider<ConnectorCredentialProvider> connectorCredentialProvider,
			ObjectProvider<KeycloakAuthenticationService> keycloakAuthenticationService) {
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
}
