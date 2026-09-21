package it.eng.connector.service;

/**
 * Unified login/refresh/logout contract for the {@code /api/v1/auth/*} admin-zone endpoints.
 *
 * <p>Implementations authenticate against whichever authentication mode is active
 * (for example {@code INTERNAL}, backed by MongoDB {@code User} credentials) and issue/rotate
 * opaque refresh-token identifiers alongside signed JWT access tokens.
 */
public interface AuthService {

	/**
	 * Authenticates the given credentials and issues a new access/refresh token pair.
	 *
	 * @param email    the user's email address (used as the username)
	 * @param password the user's plaintext password
	 * @return the issued {@link AuthTokens}
	 * @throws org.springframework.security.core.AuthenticationException if the credentials are
	 *                                                                    invalid or the account is
	 *                                                                    disabled, expired, or
	 *                                                                    locked
	 */
	AuthTokens login(String email, String password);

	/**
	 * Rotates a valid refresh token id and mints a fresh access token for its owning subject.
	 *
	 * @param refreshTokenId the refresh token id presented for rotation
	 * @return the newly issued {@link AuthTokens}
	 * @throws org.springframework.security.authentication.BadCredentialsException if
	 *                                                                              {@code refreshTokenId}
	 *                                                                              is unknown,
	 *                                                                              expired, or
	 *                                                                              already rotated
	 *                                                                              or revoked
	 */
	AuthTokens refresh(String refreshTokenId);

	/**
	 * Revokes a refresh token id. Idempotent: revoking an already-revoked or unknown id does not
	 * throw, matching typical identity-provider logout semantics.
	 *
	 * @param refreshTokenId the refresh token id to revoke
	 */
	void logout(String refreshTokenId);

	/**
	 * Result of a successful login or refresh, ready for the {@code AuthController} layer to
	 * shape into its HTTP response body (for example {@code {access_token, refresh_token,
	 * token_type, expires_in}}).
	 *
	 * @param accessToken     the signed JWT access token
	 * @param refreshToken    the opaque refresh token id (not a JWT) tracked by
	 *                        {@code RefreshTokenStore}
	 * @param expiresInSeconds the access token's remaining time-to-live, in seconds
	 */
	record AuthTokens(String accessToken, String refreshToken, long expiresInSeconds) {
	}
}
