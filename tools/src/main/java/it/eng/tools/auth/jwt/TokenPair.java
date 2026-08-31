package it.eng.tools.auth.jwt;

/**
 * Pair of tokens issued for a successful authentication.
 *
 * @param accessToken            the signed access token
 * @param refreshToken           the signed refresh token
 * @param accessExpiresInSeconds the access token's remaining time-to-live, in seconds, suitable
 *                                for direct use in an HTTP response body (for example,
 *                                {@code expiresIn})
 */
public record TokenPair(String accessToken, String refreshToken, long accessExpiresInSeconds) {
}
