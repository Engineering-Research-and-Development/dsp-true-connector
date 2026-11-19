package it.eng.dcp.common.service.sts;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory JTI replay cache backed by a ConcurrentHashMap. Not distributed.
 *
 * <p>An entry is kept until the expiry instant it was inserted with and purged lazily after it; a
 * second insert of the same JTI before that instant is a replay.
 */
@Component
public class InMemoryJtiReplayCache implements JtiReplayCache {

    private final Map<String, Instant> store = new ConcurrentHashMap<>();

    @Override
    public void checkAndPut(String jti, Instant expiry) {
        Objects.requireNonNull(jti, "jti");
        Objects.requireNonNull(expiry, "expiry");
        Instant now = Instant.now();
        // purge expired entries lazily
        store.entrySet().removeIf(e -> e.getValue().isBefore(now) || e.getValue().equals(now));

        Instant prev = store.putIfAbsent(jti, expiry);
        if (prev != null) {
            // if previous expiry is in future, it's a replay
            if (prev.isAfter(now)) {
                throw new IllegalStateException("Replay detected for jti: " + jti);
            } else {
                // previous entry expired, replace
                store.put(jti, expiry);
            }
        }
    }
}