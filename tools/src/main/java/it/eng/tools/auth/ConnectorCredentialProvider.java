package it.eng.tools.auth;

/**
 * Issues an access token for connector-to-connector (protocol) M2M calls made in {@code INTERNAL}
 * authentication mode.
 *
 * <p>Implemented in the {@code connector} module (which owns the Mongo-backed {@code User}/{@code
 * AuthService} types) and consumed here via an optional {@code ObjectProvider}, since the {@code
 * tools} module must never depend on {@code connector}. This mirrors the existing idiom used by
 * {@link AuthProvider} for the Keycloak-oriented {@link AuthenticationCache}.
 */
public interface ConnectorCredentialProvider {

	/**
	 * Authenticates as the connector-to-connector principal and returns a signed JWT access token.
	 *
	 * @return the issued access token, or {@code null} if a token could not be issued
	 */
	String issueConnectorToken();
}
