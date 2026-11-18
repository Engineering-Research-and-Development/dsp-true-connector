package it.eng.tools.auth;

/**
 * Issues an access token for connector-to-connector (protocol) M2M calls made in {@code INTERNAL}
 * authentication mode.
 *
 * <p>Implemented by {@link it.eng.tools.auth.jwt.InternalProtocolTokenIssuer}, which mints the token
 * locally with the {@code INTERNAL} signing key; no user login or user store is involved. Consumed by
 * {@code CredentialUtils} via an optional {@code ObjectProvider}, which is empty outside
 * {@code INTERNAL} mode.
 */
public interface ConnectorCredentialProvider {

	/**
	 * Returns a signed JWT access token for the connector-to-connector principal.
	 *
	 * @return the issued access token, or {@code null} if a token could not be issued
	 */
	String issueConnectorToken();
}
