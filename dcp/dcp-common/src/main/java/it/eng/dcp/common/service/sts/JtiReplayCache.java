package it.eng.dcp.common.service.sts;

import java.time.Instant;

/**
 * Cache interface for preventing JWT replay attacks.
 *
 * <p>This cache stores JWT IDs (JTI) along with their expiry times to detect
 * and prevent replay attacks where an attacker reuses a previously captured token.
 *
 * <p>Implementations should:
 * <ul>
 *   <li>Store the JTI with its expiry time</li>
 *   <li>Throw an exception if a duplicate JTI is detected before expiry</li>
 *   <li>Automatically clean up expired JTIs to prevent memory leaks</li>
 *   <li>Be thread-safe for concurrent access</li>
 * </ul>
 *
 * <p>Common implementations:
 * <ul>
 *   <li>In-memory cache with scheduled cleanup (suitable for single instance)</li>
 *   <li>Redis cache (suitable for distributed deployments)</li>
 *   <li>Database-backed cache (for audit trail requirements)</li>
 * </ul>
 */
public interface JtiReplayCache {

    /**
     * Checks whether the given JTI is already seen and inserts it with the provided expiry.
     *
     * <p>This method must be atomic - check and insert should happen as a single operation
     * to prevent race conditions in concurrent scenarios.
     *
     * @param jti The JWT ID to check and insert
     * @param expiry The instant when this JTI should expire and be removed from the cache
     * @throws IllegalStateException if the JTI is already present and not expired (replay attack detected)
     */
    void checkAndPut(String jti, Instant expiry);
}

