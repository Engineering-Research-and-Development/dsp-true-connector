package it.eng.dcp.core;

import java.time.Instant;

/**
 * Cache to prevent JWT replay attacks. Implementations should throw an exception if a jti is already present and not expired.
 */
public interface JtiReplayCache {

    /**
     * Check whether the given jti is already seen and insert it with the provided expiry.
     * If the jti is already present and not expired, an IllegalStateException (or custom ReplayException) should be thrown.
     * @param jti JWT ID
     * @param expiry instant when the jti should expire
     */
    void checkAndPut(String jti, Instant expiry);
}

