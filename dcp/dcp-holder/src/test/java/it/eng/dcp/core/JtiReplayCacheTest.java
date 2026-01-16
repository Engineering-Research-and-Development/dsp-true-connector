package it.eng.dcp.core;

import it.eng.dcp.common.service.sts.InMemoryJtiReplayCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JtiReplayCacheTest {

    @Test
    @DisplayName("checkAndPut allows first insert and rejects immediate replay")
    void checkAndPutAllowlFirstInsertAndRejectsReplay() {
        InMemoryJtiReplayCache cache = new InMemoryJtiReplayCache();
        String jti = "jti-1";
        Instant exp = Instant.now().plusSeconds(60);
        cache.checkAndPut(jti, exp);
        assertThrows(IllegalStateException.class, () -> cache.checkAndPut(jti, exp));
    }

    @Test
    @DisplayName("checkAndPut allows re-insert after expiry")
    void checkAndPutAllowsAfterExpiry() throws InterruptedException {
        InMemoryJtiReplayCache cache = new InMemoryJtiReplayCache();
        String jti = "jti-2";
        Instant exp = Instant.now().plusMillis(200);
        cache.checkAndPut(jti, exp);
        // wait for expiry
        Thread.sleep(300);
        Instant newExp = Instant.now().plusSeconds(60);
        cache.checkAndPut(jti, newExp); // should not throw
    }
}

