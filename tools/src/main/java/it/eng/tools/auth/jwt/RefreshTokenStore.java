package it.eng.tools.auth.jwt;

import java.util.Optional;

/**
 * Tracks currently valid refresh token identifiers for the unified {@code /api/v1/auth/*}
 * contract, supporting issuance, rotation, and revocation.
 *
 * <p>Implementations are expected to be thread-safe: concurrent {@link #rotate(String)} calls for
 * the same token id must not both succeed, and concurrent calls for different sessions must not
 * interfere with one another.
 *
 * <p>Only an opaque token identifier and non-sensitive metadata are tracked — never the raw JWT
 * string itself.
 */
public interface RefreshTokenStore {

    /**
     * Issues a new refresh token id for the given subject and marks it as valid.
     *
     * @param subject the subject (user id) the token id is issued for
     * @return the newly issued, valid token id
     */
    String issue(String subject);

    /**
     * Atomically invalidates {@code oldTokenId} and issues a new valid token id for the same
     * subject, when {@code oldTokenId} is currently valid.
     *
     * @param oldTokenId the token id presented for rotation
     * @return the new {@link RefreshTokenRecord} when {@code oldTokenId} was valid and has now
     *         been rotated; {@link Optional#empty()} when {@code oldTokenId} is unknown, expired,
     *         or already rotated
     */
    Optional<RefreshTokenRecord> rotate(String oldTokenId);

    /**
     * Marks the given token id as invalid. Subsequent {@link #rotate(String)} calls with this id
     * must return {@link Optional#empty()}.
     *
     * @param tokenId the token id to revoke
     */
    void revoke(String tokenId);
}
