package it.eng.tools.auth.jwt;

import java.time.Instant;

/**
 * Metadata for a currently valid refresh token tracked by a {@link RefreshTokenStore}.
 *
 * <p>Only an opaque token identifier and non-sensitive metadata are stored — never the raw JWT
 * string itself.
 *
 * @param tokenId   the opaque token identifier (for example, the JWT's {@code jti} claim)
 * @param subject   the subject (user id) the token was issued for
 * @param issuedAt  the instant the token id was issued or re-issued (on rotation)
 */
public record RefreshTokenRecord(String tokenId, String subject, Instant issuedAt) {
}
