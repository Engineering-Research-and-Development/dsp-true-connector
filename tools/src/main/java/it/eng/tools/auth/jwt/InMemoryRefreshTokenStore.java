package it.eng.tools.auth.jwt;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link ConcurrentHashMap}-backed, in-memory {@link RefreshTokenStore} implementation.
 *
 * <p>Scoped to a single application instance with no persistence layer, matching the in-memory
 * scope explicitly called out for this task: MongoDB persistence, rate limiting, and multi-factor
 * authentication are deferred to future hardening work.
 *
 * <p><b>No TTL-based eviction</b>: entries are never purged by a background process in this
 * implementation. This is intentional for the current scope (in-memory, non-persistent,
 * single-instance) rather than a bug, but it means the map grows with every {@link #issue(String)}
 * and {@link #rotate(String)} call whose resulting token id is never rotated or revoked. A future
 * hardening task should add expiry-based eviction (for example, driven by the JWT's own
 * {@code exp} claim) before this store is used at a scale where unbounded growth is a concern.
 */
@Slf4j
@Component
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final ConcurrentHashMap<String, RefreshTokenRecord> validTokens = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public String issue(String subject) {
        Objects.requireNonNull(subject, "subject must not be null");
        String tokenId = UUID.randomUUID().toString();
        validTokens.put(tokenId, new RefreshTokenRecord(tokenId, subject, Instant.now()));
        log.debug("Issued refresh token id for subject");
        return tokenId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses an atomic {@link ConcurrentHashMap#remove(Object)} on {@code oldTokenId} so that,
     * when two threads call this method concurrently with the same old token id, exactly one
     * removal succeeds (returns the previous value) and the other observes {@code null} — no
     * separate check-then-act step is needed to make rotation safe under concurrency.
     */
    @Override
    public Optional<RefreshTokenRecord> rotate(String oldTokenId) {
        if (oldTokenId == null) {
            return Optional.empty();
        }
        RefreshTokenRecord oldRecord = validTokens.remove(oldTokenId);
        if (oldRecord == null) {
            log.debug("Rotate requested for unknown, expired, or already-rotated token id");
            return Optional.empty();
        }
        String newTokenId = UUID.randomUUID().toString();
        RefreshTokenRecord newRecord = new RefreshTokenRecord(newTokenId, oldRecord.subject(), Instant.now());
        validTokens.put(newTokenId, newRecord);
        log.debug("Rotated refresh token id for subject");
        return Optional.of(newRecord);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revoke(String tokenId) {
        if (tokenId != null) {
            validTokens.remove(tokenId);
            log.debug("Revoked refresh token id");
        }
    }
}
