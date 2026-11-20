package it.eng.dcp.common.service.sts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Lifetime of {@link InMemoryJtiReplayCache} entries.
 */
class JtiReplayCacheTest {

    @Test
    @DisplayName("an entry is kept until the given instant, not only until the token's exp")
    void entryKeptUntilGivenInstant() {
        InMemoryJtiReplayCache cache = new InMemoryJtiReplayCache();
        // Token exp already passed, but the token is still accepted inside the clock-skew window
        Instant expPlusSkew = Instant.now().plusSeconds(60);

        cache.checkAndPut("jti-skew", expPlusSkew);

        assertThrows(IllegalStateException.class, () -> cache.checkAndPut("jti-skew", expPlusSkew));
    }

    @Test
    @DisplayName("an entry is purged after the given instant and the jti can be stored again")
    void entryPurgedAfterGivenInstant() throws InterruptedException {
        InMemoryJtiReplayCache cache = new InMemoryJtiReplayCache();
        cache.checkAndPut("jti-old", Instant.now().plusMillis(100));
        Thread.sleep(200);

        assertDoesNotThrow(() -> cache.checkAndPut("jti-old", Instant.now().plusSeconds(60)));
    }
}
