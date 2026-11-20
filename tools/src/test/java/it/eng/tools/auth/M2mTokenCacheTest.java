package it.eng.tools.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link M2mTokenCache}.
 */
class M2mTokenCacheTest {

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;

    private M2mTokenCache cache;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        publicKey = (RSAPublicKey) kp.getPublic();
        privateKey = (RSAPrivateKey) kp.getPrivate();
        cache = new M2mTokenCache();
    }

    private static String tokenExpiringIn(long millisFromNow) {
        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        return JWT.create()
                .withSubject("test")
                .withExpiresAt(new Date(System.currentTimeMillis() + millisFromNow))
                .sign(algorithm);
    }

    @Test
    @DisplayName("Should call fetcher and cache token on a cache miss")
    void cacheMissFetchesAndCaches() {
        String token = tokenExpiringIn(60_000L);
        @SuppressWarnings("unchecked")
        Supplier<String> fetcher = mock(Supplier.class);
        when(fetcher.get()).thenReturn(token);

        String result = cache.getOrFetch("key1", fetcher);

        assertEquals(token, result);
        verify(fetcher, times(1)).get();
    }

    @Test
    @DisplayName("Should return cached token without invoking fetcher again when still fresh")
    void cacheHitSkipsFetcher() {
        String token = tokenExpiringIn(60_000L);
        @SuppressWarnings("unchecked")
        Supplier<String> fetcher = mock(Supplier.class);
        when(fetcher.get()).thenReturn(token);

        String first = cache.getOrFetch("key1", fetcher);
        String second = cache.getOrFetch("key1", fetcher);

        assertEquals(token, first);
        assertEquals(token, second);
        verify(fetcher, times(1)).get();
    }

    @Test
    @DisplayName("Should proactively refetch before actual expiry (within the refresh buffer)")
    void proactiveRefreshBeforeExpiry() {
        // Expires in 10s, well within the 30s proactive-refresh buffer, so the second call must
        // treat the cached token as already stale and fetch again.
        String firstToken = tokenExpiringIn(10_000L);
        String secondToken = tokenExpiringIn(60_000L);
        @SuppressWarnings("unchecked")
        Supplier<String> fetcher = mock(Supplier.class);
        when(fetcher.get()).thenReturn(firstToken, secondToken);

        String first = cache.getOrFetch("key1", fetcher);
        String second = cache.getOrFetch("key1", fetcher);

        assertEquals(firstToken, first);
        assertEquals(secondToken, second);
        verify(fetcher, times(2)).get();
    }

    @Test
    @DisplayName("Should not cache when fetcher returns null")
    void nullTokenNotCached() {
        @SuppressWarnings("unchecked")
        Supplier<String> fetcher = mock(Supplier.class);
        when(fetcher.get()).thenReturn(null);

        String result = cache.getOrFetch("key1", fetcher);

        assertNull(result);
        verify(fetcher, times(1)).get();
    }

    @Test
    @DisplayName("Should not cache an undecodable token, refetching on every call")
    void undecodableTokenNotCached() {
        @SuppressWarnings("unchecked")
        Supplier<String> fetcher = mock(Supplier.class);
        when(fetcher.get()).thenReturn("not-a-jwt");

        String first = cache.getOrFetch("key1", fetcher);
        String second = cache.getOrFetch("key1", fetcher);

        assertEquals("not-a-jwt", first);
        assertEquals("not-a-jwt", second);
        verify(fetcher, times(2)).get();
    }

    @Test
    @DisplayName("Should not cache a token with no 'exp' claim, refetching on every call")
    void tokenWithNoExpiryClaimNotCached() {
        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        String tokenWithoutExpiry = JWT.create().withSubject("test").sign(algorithm);
        @SuppressWarnings("unchecked")
        Supplier<String> fetcher = mock(Supplier.class);
        when(fetcher.get()).thenReturn(tokenWithoutExpiry);

        String first = cache.getOrFetch("key1", fetcher);
        String second = cache.getOrFetch("key1", fetcher);

        assertEquals(tokenWithoutExpiry, first);
        assertEquals(tokenWithoutExpiry, second);
        verify(fetcher, times(2)).get();
    }

    @Test
    @DisplayName("Should keep independent cache slots per cache key")
    void independentCacheKeys() {
        String tokenA = tokenExpiringIn(60_000L);
        String tokenB = tokenExpiringIn(60_000L);
        @SuppressWarnings("unchecked")
        Supplier<String> fetcherA = mock(Supplier.class);
        @SuppressWarnings("unchecked")
        Supplier<String> fetcherB = mock(Supplier.class);
        when(fetcherA.get()).thenReturn(tokenA);
        when(fetcherB.get()).thenReturn(tokenB);

        String resultA = cache.getOrFetch("connector-m2m", fetcherA);
        String resultB = cache.getOrFetch("internal-api", fetcherB);

        assertEquals(tokenA, resultA);
        assertEquals(tokenB, resultB);
        verify(fetcherA, times(1)).get();
        verify(fetcherB, times(1)).get();
    }

    @Test
    @DisplayName("Should refetch after invalidate() evicts the cached entry")
    void invalidateForcesRefetch() {
        String firstToken = tokenExpiringIn(60_000L);
        String secondToken = tokenExpiringIn(60_000L);
        @SuppressWarnings("unchecked")
        Supplier<String> fetcher = mock(Supplier.class);
        when(fetcher.get()).thenReturn(firstToken, secondToken);

        String first = cache.getOrFetch("key1", fetcher);
        cache.invalidate("key1");
        String second = cache.getOrFetch("key1", fetcher);

        assertEquals(firstToken, first);
        assertEquals(secondToken, second);
        verify(fetcher, times(2)).get();
    }

    @Test
    @DisplayName("Should be a no-op when invalidate() is called for an unknown key")
    void invalidateUnknownKeyIsNoop() {
        cache.invalidate("never-cached");
        // No exception expected; nothing further to assert.
    }

    @Test
    @DisplayName("Should empty the slot and rethrow when the fetcher throws for a stale entry")
    void fetcherExceptionEmptiesSlot() {
        String staleToken = tokenExpiringIn(10_000L);
        String freshToken = tokenExpiringIn(60_000L);
        @SuppressWarnings("unchecked")
        Supplier<String> fetcher = mock(Supplier.class);
        when(fetcher.get()).thenReturn(staleToken)
                .thenThrow(new IllegalStateException("token endpoint down"))
                .thenReturn(freshToken);

        cache.getOrFetch("key1", fetcher);
        assertThrows(IllegalStateException.class, () -> cache.getOrFetch("key1", fetcher));
        String result = cache.getOrFetch("key1", fetcher);

        assertEquals(freshToken, result);
        verify(fetcher, times(3)).get();
    }

    @Test
    @DisplayName("Should not block a fetch for one key while a slow fetch for another key is running")
    void slowFetchDoesNotBlockOtherKey() throws Exception {
        CountDownLatch slowFetchStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowFetch = new CountDownLatch(1);
        String tokenA = tokenExpiringIn(60_000L);
        String tokenB = tokenExpiringIn(60_000L);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> cache.getOrFetch("keyA", () -> {
            slowFetchStarted.countDown();
            try {
                releaseSlowFetch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return tokenA;
        }), executor);
        try {
            assertTrue(slowFetchStarted.await(5, TimeUnit.SECONDS));

            String result = CompletableFuture.supplyAsync(() -> cache.getOrFetch("keyB", () -> tokenB), executor)
                    .get(5, TimeUnit.SECONDS);

            assertEquals(tokenB, result);
        } finally {
            releaseSlowFetch.countDown();
            executor.shutdown();
        }
        assertEquals(tokenA, slow.get(5, TimeUnit.SECONDS));
    }
}
