package it.eng.tools.auth.jwt;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InMemoryRefreshTokenStore}.
 */
class InMemoryRefreshTokenStoreTest {

    private InMemoryRefreshTokenStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryRefreshTokenStore();
    }

    @Test
    @DisplayName("Should issue a new valid token id for a subject")
    void issueReturnsNewValidTokenId() {
        String tokenId = store.issue("user-1");

        assertNotNull(tokenId);

        Optional<RefreshTokenRecord> rotated = store.rotate(tokenId);
        assertTrue(rotated.isPresent());
        assertEquals("user-1", rotated.get().subject());
    }

    @Test
    @DisplayName("Should rotate a valid token id, invalidating the old one and returning a new one")
    void rotateValidTokenInvalidatesOldAndReturnsNew() {
        String oldTokenId = store.issue("user-2");

        Optional<RefreshTokenRecord> rotated = store.rotate(oldTokenId);

        assertTrue(rotated.isPresent());
        assertNotEquals(oldTokenId, rotated.get().tokenId());
        assertEquals("user-2", rotated.get().subject());

        // The old token id must no longer be usable.
        assertTrue(store.rotate(oldTokenId).isEmpty());
    }

    @Test
    @DisplayName("Should return empty when rotating an already-rotated token id")
    void rotateAlreadyRotatedTokenReturnsEmpty() {
        String tokenId = store.issue("user-3");
        store.rotate(tokenId);

        assertTrue(store.rotate(tokenId).isEmpty());
    }

    @Test
    @DisplayName("Should return empty when rotating an unknown token id")
    void rotateUnknownTokenReturnsEmpty() {
        assertTrue(store.rotate("unknown-token-id").isEmpty());
    }

    @Test
    @DisplayName("Should return empty when rotating a token id after revocation")
    void rotateAfterRevokeReturnsEmpty() {
        String tokenId = store.issue("user-4");

        store.revoke(tokenId);

        assertTrue(store.rotate(tokenId).isEmpty());
    }

    @Test
    @DisplayName("Should allow exactly one success among concurrent rotate() calls on the same valid token id")
    void concurrentRotateOnSameTokenIdYieldsExactlyOneSuccess() throws Exception {
        String tokenId = store.issue("user-5");
        int concurrentCallers = 20;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentCallers);
        try {
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger();

            Callable<Optional<RefreshTokenRecord>> task = () -> {
                startLatch.await();
                return store.rotate(tokenId);
            };

            List<Future<Optional<RefreshTokenRecord>>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < concurrentCallers; i++) {
                futures.add(executor.submit(task));
            }

            startLatch.countDown();

            Set<String> newTokenIds = new HashSet<>();
            for (Future<Optional<RefreshTokenRecord>> future : futures) {
                Optional<RefreshTokenRecord> result = future.get(5, TimeUnit.SECONDS);
                if (result.isPresent()) {
                    successCount.incrementAndGet();
                    newTokenIds.add(result.get().tokenId());
                }
            }

            assertEquals(1, successCount.get(), "Exactly one concurrent rotate() call must succeed");
            assertEquals(1, newTokenIds.size(), "Exactly one new token id must have been produced");
        } finally {
            executor.shutdownNow();
        }
    }
}
