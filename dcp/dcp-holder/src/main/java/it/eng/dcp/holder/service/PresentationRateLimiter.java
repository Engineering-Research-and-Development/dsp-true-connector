// ...existing code...
package it.eng.dcp.holder.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple token-bucket rate limiter keyed by holder DID.
 * Configurable per-bucket capacity and refill period (tokens per refill interval).
 * This is intentionally simple and in-memory; for production a distributed store (Redis) is recommended.
 */
@Service
public class PresentationRateLimiter {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    // defaults: capacity 5 tokens, refill 5 tokens per minute
    private final int capacity = 5;
    private final int refillTokens = 5;
    private final Duration refillPeriod = Duration.ofMinutes(1);

    /**
     * Try to consume a single token for the given holderDid. Returns true if allowed, false if rate limited.
     * @param holderDid the holder DID to rate limit
     * @return true if token consumed (allowed), false if rate limited
     */
    public boolean tryConsume(String holderDid) {
        if (holderDid == null || holderDid.isBlank()) return false;
        TokenBucket b = buckets.computeIfAbsent(holderDid, k -> new TokenBucket(capacity, refillTokens, refillPeriod));
        return b.tryConsume();
    }

    // simple token bucket implementation
    private static class TokenBucket {
        private final int capacity;
        private final int refillTokens;
        private final Duration refillPeriod;

        private double tokens;
        private Instant lastRefill;

        TokenBucket(int capacity, int refillTokens, Duration refillPeriod) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriod = refillPeriod;
            this.tokens = capacity;
            this.lastRefill = Instant.now();
        }

        synchronized boolean tryConsume() {
            refillIfNeeded();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refillIfNeeded() {
            Instant now = Instant.now();
            if (!now.isAfter(lastRefill)) return;
            long periods = now.minusMillis(lastRefill.toEpochMilli()).toEpochMilli() / Math.max(1, refillPeriod.toMillis());
            if (periods <= 0) return;
            double tokensToAdd = (double) periods * refillTokens;
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefill = lastRefill.plusMillis(periods * refillPeriod.toMillis());
        }
    }
}

