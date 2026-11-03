package it.eng.tools.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Generic, key-scoped cache for machine-to-machine JWT access tokens used by outbound credential flows:
 * the {@code INTERNAL} mode connector token and the {@code KEYCLOAK} mode connector token (see {@code
 * CredentialUtils#getConnectorCredentials()}).
 *
 * <p>This cache has no knowledge of Keycloak or any specific token source: callers identify a cache
 * slot with an arbitrary {@code cacheKey} and supply a {@link Supplier} that fetches a fresh token on a
 * cache miss. Multiple independent M2M identities can therefore share one cache instance without
 * colliding, as long as their keys differ.
 *
 * <p>Cached tokens are proactively treated as stale {@value #REFRESH_BEFORE_EXPIRY_SECONDS} seconds
 * before their actual {@code exp} claim, so an in-flight request is unlikely to present a token
 * that expires between being read from the cache and reaching its destination.
 */
@Slf4j
@Component
public class M2mTokenCache {

    // Refresh this many seconds before actual JWT expiry, to avoid handing out a token that is
    // about to expire mid-flight.
    private static final long REFRESH_BEFORE_EXPIRY_SECONDS = 30L;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // One lock per cache key, so a slow fetch (network I/O in KEYCLOAK mode) only blocks callers of
    // the same key. The set of keys is small and fixed, so locks are never removed.
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * Returns the cached token for {@code cacheKey} if still fresh, otherwise invokes
     * {@code fetcher} to mint a new one, caches it, and returns it.
     *
     * <p>When {@code fetcher} returns {@code null} or throws, the slot is emptied and the exception,
     * if any, propagates: a failed fetch never leaves a token behind.
     *
     * <p>Concurrent misses on the same key run {@code fetcher} once; fetches for different keys do not
     * block each other.
     *
     * @param cacheKey the cache slot identifier (for example {@code "connector-m2m"})
     * @param fetcher  supplies a fresh token on a cache miss or stale entry; may return
     *                 {@code null} if no token could be minted
     * @return the cached or freshly fetched token, or {@code null} if {@code fetcher} returned
     *         {@code null}
     */
    public String getOrFetch(String cacheKey, Supplier<String> fetcher) {
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && LocalDateTime.now().isBefore(cached.refreshAt())) {
            return cached.token();
        }
        synchronized (locks.computeIfAbsent(cacheKey, key -> new Object())) {
            cached = cache.get(cacheKey);
            if (cached != null && LocalDateTime.now().isBefore(cached.refreshAt())) {
                return cached.token();
            }
            log.debug("Fetching new M2M token for cache key '{}'", cacheKey);
            String token;
            try {
                token = fetcher.get();
            } catch (RuntimeException e) {
                cache.remove(cacheKey);
                throw e;
            }
            if (token == null) {
                cache.remove(cacheKey);
                return null;
            }
            LocalDateTime refreshAt = decodeRefreshAt(cacheKey, token);
            if (refreshAt == null) {
                // Could not decode an expiry; don't cache an entry that can never expire safely,
                // but still hand back the freshly minted token for this one call.
                cache.remove(cacheKey);
            } else {
                cache.put(cacheKey, new CacheEntry(token, refreshAt));
            }
            return token;
        }
    }

    /**
     * Evicts the cached token for {@code cacheKey}, if any, forcing the next {@link
     * #getOrFetch(String, Supplier)} call for that key to fetch a fresh token.
     *
     * @param cacheKey the cache slot identifier to evict
     */
    public void invalidate(String cacheKey) {
        cache.remove(cacheKey);
    }

    private LocalDateTime decodeRefreshAt(String cacheKey, String token) {
        try {
            Date expiresAt = JWT.decode(token).getExpiresAt();
            if (expiresAt == null) {
                log.warn("Token minted for cache key '{}' has no 'exp' claim; not caching it", cacheKey);
                return null;
            }
            LocalDateTime expiry = expiresAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return expiry.minusSeconds(REFRESH_BEFORE_EXPIRY_SECONDS);
        } catch (JWTDecodeException e) {
            log.warn("Could not decode expiry of token minted for cache key '{}': {}", cacheKey, e.getMessage());
            return null;
        }
    }

    // Pairs the cached token with the point in time at which it should be treated as stale
    // (its expiry minus the proactive-refresh buffer), rather than its literal expiry.
    private record CacheEntry(String token, LocalDateTime refreshAt) {
    }
}
