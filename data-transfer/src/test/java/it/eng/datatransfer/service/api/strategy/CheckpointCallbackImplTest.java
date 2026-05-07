package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.model.TransferArtifactState;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CheckpointCallbackImplTest {

    @Mock
    private TransferArtifactStateRepository repository;

    private TransferArtifactState state;

    private static final String TRANSFER_ID = "transfer-123";
    private static final long RANGE_START = 0L;

    @BeforeEach
    void setUp() {
        state = TransferArtifactState.Builder.newInstance()
                .id(TRANSFER_ID)
                .downloadedBytes(0)
                .build();
        lenient().when(repository.save(any(TransferArtifactState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("onUploadStarted always flushes immediately and persists the upload ID")
    void onUploadStarted_alwaysFlushes() {
        CheckpointCallbackImpl callback = new CheckpointCallbackImpl(state, RANGE_START, repository);

        callback.onUploadStarted("upload-id-1");

        verify(repository, times(1)).save(any(TransferArtifactState.class));
        assertEquals("upload-id-1", state.getUploadId());
    }

    @Test
    @DisplayName("onPartCompleted does not flush before FLUSH_EVERY_N_PARTS parts")
    void onPartCompleted_doesNotFlushBeforeThreshold() {
        CheckpointCallbackImpl callback = new CheckpointCallbackImpl(state, RANGE_START, repository);
        int n = CheckpointCallbackImpl.FLUSH_EVERY_N_PARTS;

        for (int i = 1; i < n; i++) {
            callback.onPartCompleted(i, "etag-" + i, (long) i * 10_000_000);
        }

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("onPartCompleted flushes exactly once when FLUSH_EVERY_N_PARTS parts complete")
    void onPartCompleted_flushesOnBoundary() {
        CheckpointCallbackImpl callback = new CheckpointCallbackImpl(state, RANGE_START, repository);
        int n = CheckpointCallbackImpl.FLUSH_EVERY_N_PARTS;

        for (int i = 1; i <= n; i++) {
            callback.onPartCompleted(i, "etag-" + i, (long) i * 10_000_000);
        }

        verify(repository, times(1)).save(any(TransferArtifactState.class));
    }

    @Test
    @DisplayName("flush() is a no-op when no parts have arrived since last flush")
    void flush_isNoOp_whenNoPendingParts() {
        CheckpointCallbackImpl callback = new CheckpointCallbackImpl(state, RANGE_START, repository);
        int n = CheckpointCallbackImpl.FLUSH_EVERY_N_PARTS;

        for (int i = 1; i <= n; i++) {
            callback.onPartCompleted(i, "etag-" + i, (long) i * 10_000_000);
        }
        verify(repository, times(1)).save(any()); // one periodic flush

        callback.flush(); // partsSinceLastFlush == 0 → no extra write
        verify(repository, times(1)).save(any()); // still 1
    }

    @Test
    @DisplayName("flush() writes remaining parts that did not hit the periodic boundary")
    void flush_writesRemainingParts() {
        CheckpointCallbackImpl callback = new CheckpointCallbackImpl(state, RANGE_START, repository);

        // 2 parts (< FLUSH_EVERY_N_PARTS=4) — no periodic flush
        callback.onPartCompleted(1, "etag-1", 10_000_000L);
        callback.onPartCompleted(2, "etag-2", 20_000_000L);
        verify(repository, never()).save(any());

        callback.flush();
        verify(repository, times(1)).save(any(TransferArtifactState.class));
    }

    @Test
    @DisplayName("downloadedBytes never regresses on out-of-order part completions")
    void onPartCompleted_neverRegressesDownloadedBytes() {
        CheckpointCallbackImpl callback = new CheckpointCallbackImpl(state, RANGE_START, repository);

        callback.onPartCompleted(3, "etag-3", 30_000_000L); // arrives first
        callback.onPartCompleted(1, "etag-1", 10_000_000L); // arrives second (out of order)
        callback.flush();

        assertEquals(30_000_000L, state.getDownloadedBytes());
    }

    @Test
    @DisplayName("rangeStart offset is added to totalBytesUploaded when computing downloadedBytes")
    void onPartCompleted_addsRangeStartToOffset() {
        long rangeStart = 50_000_000L;
        CheckpointCallbackImpl callback = new CheckpointCallbackImpl(state, rangeStart, repository);

        callback.onPartCompleted(1, "etag-1", 10_000_000L);
        callback.flush();

        assertEquals(60_000_000L, state.getDownloadedBytes());
    }

    @Test
    @DisplayName("Concurrent onPartCompleted calls from 4 threads do not throw or lose the flush")
    void onPartCompleted_concurrentCalls_noExceptionAndCorrectFlush() throws InterruptedException {
        CheckpointCallbackImpl callback = new CheckpointCallbackImpl(state, RANGE_START, repository);
        int threads = CheckpointCallbackImpl.FLUSH_EVERY_N_PARTS;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Throwable> errors = new ArrayList<>();

        for (int i = 1; i <= threads; i++) {
            final int partNum = i;
            pool.submit(() -> {
                try {
                    start.await();
                    callback.onPartCompleted(partNum, "etag-" + partNum,
                            (long) partNum * 10_000_000);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "Threads did not finish in time");
        pool.shutdown();

        assertTrue(errors.isEmpty(), "Unexpected exceptions from concurrent callbacks: " + errors);
        // All 4 parts completed → exactly 1 periodic flush
        verify(repository, times(1)).save(any(TransferArtifactState.class));
    }

    @Test
    @DisplayName("doFlush retries once when OptimisticLockingFailureException is thrown")
    void flush_retriesOnOptimisticLockException() {
        TransferArtifactState freshState = TransferArtifactState.Builder.newInstance()
                .id(TRANSFER_ID)
                .downloadedBytes(0)
                .build();
        when(repository.save(any(TransferArtifactState.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findById(TRANSFER_ID)).thenReturn(Optional.of(freshState));

        CheckpointCallbackImpl callback = new CheckpointCallbackImpl(state, RANGE_START, repository);
        callback.onPartCompleted(1, "etag-1", 10_000_000L);
        assertDoesNotThrow(callback::flush);

        verify(repository, times(2)).save(any(TransferArtifactState.class));
        verify(repository, times(1)).findById(TRANSFER_ID);
        assertEquals(10_000_000L, freshState.getDownloadedBytes());
    }

    @Test
    @DisplayName("doFlush throws IllegalStateException when document is missing on retry re-read")
    void flush_throwsIllegalStateException_whenDocumentMissingOnRetry() {
        when(repository.save(any(TransferArtifactState.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"));
        when(repository.findById(TRANSFER_ID)).thenReturn(Optional.empty());

        CheckpointCallbackImpl callback = new CheckpointCallbackImpl(state, RANGE_START, repository);
        callback.onPartCompleted(1, "etag-1", 10_000_000L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, callback::flush);
        assertTrue(ex.getMessage().contains("Checkpoint missing during flush: " + TRANSFER_ID));
    }
}
