# S3 Upload Checkpoint Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the per-part `findById` + `save` checkpoint pattern with an in-memory state object that flushes to MongoDB every 4 parts, fixing both an `OptimisticLockingFailureException` correctness bug and unnecessary DB round-trips.

**Architecture:** Extract the duplicated `CheckpointCallbackImpl` inner class from both strategies into a single shared package-private class. The callback holds `TransferArtifactState` in memory, serialises concurrent part callbacks via `synchronized`, flushes every `FLUSH_EVERY_N_PARTS = 4` parts, and exposes a `flush()` method called by the strategy after the upload future completes.

**Tech Stack:** Java 17, Spring Data MongoDB, JUnit 5, Mockito 5

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| **Create** | `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/CheckpointCallbackImpl.java` | Shared in-memory checkpoint callback |
| **Create** | `data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/CheckpointCallbackImplTest.java` | Unit tests for the shared callback |
| **Modify** | `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategy.java` | Remove inner class; pass state object; add final flush |
| **Modify** | `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategy.java` | Remove inner class; pass state object; add final flush |
| **Modify** | `data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategyTest.java` | Update class preload; assert final flush |
| **Modify** | `data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategyTest.java` | Update class preload; assert final flush |

---

## Task 1: Create `CheckpointCallbackImpl` with unit tests (TDD)

**Files:**
- Create: `data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/CheckpointCallbackImplTest.java`
- Create: `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/CheckpointCallbackImpl.java`

---

- [ ] **Step 1: Write the test class**

Create `data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/CheckpointCallbackImplTest.java`:

```java
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
        when(repository.save(any(TransferArtifactState.class)))
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
```

- [ ] **Step 2: Run the test to confirm it fails (class not found)**

```bash
mvn -pl data-transfer -am -Dtest=CheckpointCallbackImplTest test 2>&1 | tail -20
```

Expected: compilation error — `CheckpointCallbackImpl` does not exist yet.

- [ ] **Step 3: Create the `CheckpointCallbackImpl` class**

Create `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/CheckpointCallbackImpl.java`:

```java
package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.model.TransferArtifactState;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Thread-safe {@link UploadCheckpointCallback} that holds {@link TransferArtifactState}
 * in memory and flushes to MongoDB every {@link #FLUSH_EVERY_N_PARTS} parts.
 *
 * <p>Aligns the flush cadence with {@code S3AsyncUploadStrategy.MAX_PARALLEL_PARTS} so that
 * one checkpoint write is issued per parallel upload wave, eliminating the
 * per-part read-then-write pattern and the {@code OptimisticLockingFailureException}
 * race caused by concurrent callbacks.
 *
 * <p>All public methods are {@code synchronized} to serialise concurrent calls
 * from the parallel part-upload pool.
 */
@Slf4j
class CheckpointCallbackImpl implements UploadCheckpointCallback {

    /**
     * Flush cadence aligned with {@code S3AsyncUploadStrategy.MAX_PARALLEL_PARTS}.
     * One MongoDB write is issued per parallel upload wave.
     */
    static final int FLUSH_EVERY_N_PARTS = 4;

    private final TransferArtifactStateRepository repository;
    private final long rangeStart;
    private TransferArtifactState state;
    private int partsSinceLastFlush = 0;

    /**
     * @param state      the already-loaded (or freshly created) checkpoint entity
     * @param rangeStart byte offset from which this upload session starts;
     *                   added to {@code totalBytesUploaded} to compute the absolute position
     * @param repository repository used to persist the checkpoint state
     */
    CheckpointCallbackImpl(TransferArtifactState state, long rangeStart,
                           TransferArtifactStateRepository repository) {
        this.state = state;
        this.rangeStart = rangeStart;
        this.repository = repository;
    }

    /**
     * Records the S3 multipart upload ID and flushes immediately.
     * The upload ID must be durable before any parts are uploaded so that the
     * multipart upload can be aborted on crash recovery.
     *
     * @param uploadId the S3 multipart upload ID
     */
    @Override
    public synchronized void onUploadStarted(String uploadId) {
        state.setUploadId(uploadId);
        doFlush();
    }

    /**
     * Updates the in-memory byte offset and flushes every {@link #FLUSH_EVERY_N_PARTS} parts.
     * The {@code newBytes > state.getDownloadedBytes()} guard prevents regressions caused by
     * out-of-order part completions when {@code MAX_PARALLEL_PARTS > 1}.
     *
     * @param partNumber         the 1-based part number
     * @param etag               the ETag returned by S3 for this part
     * @param totalBytesUploaded cumulative bytes uploaded in this multipart session
     */
    @Override
    public synchronized void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) {
        long newBytes = rangeStart + totalBytesUploaded;
        if (newBytes > state.getDownloadedBytes()) {
            state.setDownloadedBytes(newBytes);
        }
        if (++partsSinceLastFlush >= FLUSH_EVERY_N_PARTS) {
            doFlush();
            partsSinceLastFlush = 0;
        }
    }

    /**
     * Flushes any in-memory changes that have not yet been persisted.
     * No-op if no parts have completed since the last flush (avoids a redundant
     * write when the final part landed on a periodic flush boundary).
     * Called by the transfer strategy after the upload future completes.
     */
    public synchronized void flush() {
        if (partsSinceLastFlush > 0) {
            doFlush();
            partsSinceLastFlush = 0;
        }
    }

    private void doFlush() {
        try {
            state = repository.save(state);
        } catch (OptimisticLockingFailureException e) {
            log.debug("Concurrent update to TransferArtifactState {} during checkpoint flush; retrying",
                    state.getId());
            TransferArtifactState fresh = repository.findById(state.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Checkpoint missing during flush: " + state.getId()));
            fresh.setDownloadedBytes(state.getDownloadedBytes());
            if (state.getUploadId() != null) {
                fresh.setUploadId(state.getUploadId());
            }
            state = repository.save(fresh);
        }
    }
}
```

- [ ] **Step 4: Run the tests and confirm they all pass**

```bash
mvn -pl data-transfer -am -Dtest=CheckpointCallbackImplTest test 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all tests green.

- [ ] **Step 5: Commit**

```bash
cd /path/to/repo
git add data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/CheckpointCallbackImpl.java \
        data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/CheckpointCallbackImplTest.java
git commit -m "feat(data-transfer): add shared in-memory CheckpointCallbackImpl

Replaces per-part findById+save with in-memory state flushed every 4 parts.
Fixes OptimisticLockingFailureException race when MAX_PARALLEL_PARTS=4 callbacks
fire concurrently, and reduces checkpoint DB writes by ~8x for a 1 GB file.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 2: Migrate `HttpPullTransferStrategy` to use the shared callback

**Files:**
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategy.java`
- Modify: `data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategyTest.java`

---

- [ ] **Step 1: Remove the inner `CheckpointCallbackImpl` from `HttpPullTransferStrategy`**

In `HttpPullTransferStrategy.java`, delete the entire inner class at the bottom of the file (lines starting with `private static class CheckpointCallbackImpl` through its closing `}`). The file currently ends with:

```java
    /**
     * Implementation of {@link UploadCheckpointCallback} for saving HTTP PULL transfer progress.
     */
    private static class CheckpointCallbackImpl implements UploadCheckpointCallback {
        private final String transferProcessId;
        private final long rangeStart;
        private final TransferArtifactStateRepository repository;

        CheckpointCallbackImpl(String transferProcessId, long rangeStart,
                               TransferArtifactStateRepository repository) {
            this.transferProcessId = transferProcessId;
            this.rangeStart = rangeStart;
            this.repository = repository;
        }

        @Override
        public void onUploadStarted(String uploadId) {
            TransferArtifactState state = repository.findById(transferProcessId)
                    .orElseThrow(() -> new IllegalStateException("Checkpoint missing for transfer: " + transferProcessId));
            state.setUploadId(uploadId);
            repository.save(state);
        }

        @Override
        public void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) {
            // totalBytesUploaded is relative to this multipart session; add rangeStart for absolute offset
            TransferArtifactState state = repository.findById(transferProcessId)
                    .orElseThrow(() -> new IllegalStateException("Checkpoint missing for transfer: " + transferProcessId));
            state.setDownloadedBytes(rangeStart + totalBytesUploaded);
            repository.save(state);
        }
    }
}
```

Remove the inner class so the file ends with the closing `}` of `downloadAndUploadToS3` or `extractAuthorization`. The outer class closing `}` remains.

- [ ] **Step 2: Update the `transfer()` method to pass the state object and add a final flush**

In `HttpPullTransferStrategy.transfer()`, replace:

```java
        UploadCheckpointCallback checkpointCallback = new CheckpointCallbackImpl(
                transferProcessId, rangeStart, transferArtifactStateRepository);

        return downloadAndUploadToS3(
                transferProcess.getDataAddress().getEndpoint(),
                authorization,
                transferProcessId,
                rangeStart,
                cancellationToken,
                checkpointCallback
        ).thenAccept(key -> log.info("Stored transfer process id - {} data!", key));
```

With:

```java
        CheckpointCallbackImpl checkpointCallback = new CheckpointCallbackImpl(
                checkpoint, rangeStart, transferArtifactStateRepository);

        return downloadAndUploadToS3(
                transferProcess.getDataAddress().getEndpoint(),
                authorization,
                transferProcessId,
                rangeStart,
                cancellationToken,
                checkpointCallback
        ).thenAccept(key -> {
            checkpointCallback.flush();
            log.info("Stored transfer process id - {} data!", key);
        });
```

- [ ] **Step 3: Update the `setUp()` preload in `HttpPullTransferStrategyTest`**

In `HttpPullTransferStrategyTest.setUp()`, replace the inner-class preload:

```java
        try {
            Class.forName("it.eng.datatransfer.service.api.strategy.HttpPullTransferStrategy$CheckpointCallbackImpl",
                    false, HttpPullTransferStrategy.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot pre-load CheckpointCallbackImpl", e);
        }
```

With a preload for the new top-level class:

```java
        try {
            Class.forName("it.eng.datatransfer.service.api.strategy.CheckpointCallbackImpl",
                    false, HttpPullTransferStrategy.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot pre-load CheckpointCallbackImpl", e);
        }
```

- [ ] **Step 4: Add an assertion that the final `flush()` triggers a save in `transfer_success`**

In `HttpPullTransferStrategyTest.transfer_success()`, after `assertDoesNotThrow(() -> strategy.transfer(transferProcess).join())`, add:

```java
            // The final flush in thenAccept must persist the in-memory state
            verify(transferArtifactStateRepository, atLeastOnce())
                    .save(any(TransferArtifactState.class));
```

- [ ] **Step 5: Run `HttpPullTransferStrategyTest` and confirm it passes**

```bash
mvn -pl data-transfer -am -Dtest=HttpPullTransferStrategyTest test 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategy.java \
        data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategyTest.java
git commit -m "refactor(data-transfer): migrate HttpPullTransferStrategy to shared CheckpointCallbackImpl

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 3: Migrate `HttpPushTransferStrategy` to use the shared callback

**Files:**
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategy.java`
- Modify: `data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategyTest.java`

---

- [ ] **Step 1: Remove the inner `CheckpointCallbackImpl` from `HttpPushTransferStrategy`**

In `HttpPushTransferStrategy.java`, delete the entire inner class at the bottom (the block starting with `/**` / `* Implementation of UploadCheckpointCallback` and ending with the inner class closing `}`):

```java
    /**
     * Implementation of UploadCheckpointCallback for saving transfer progress.
     */
    private static class CheckpointCallbackImpl implements UploadCheckpointCallback {
        private final String transferProcessId;
        private final long rangeStart;
        private final TransferArtifactStateRepository repository;

        CheckpointCallbackImpl(String transferProcessId, long rangeStart,
                              TransferArtifactStateRepository repository) {
            this.transferProcessId = transferProcessId;
            this.rangeStart = rangeStart;
            this.repository = repository;
        }

        @Override
        public void onUploadStarted(String uploadId) {
            TransferArtifactState state = repository.findById(transferProcessId)
                    .orElseThrow(() -> new IllegalStateException("Checkpoint missing for transfer: " + transferProcessId));
            state.setUploadId(uploadId);
            repository.save(state);
        }

        @Override
        public void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) {
            TransferArtifactState state = repository.findById(transferProcessId)
                    .orElseThrow(() -> new IllegalStateException("Checkpoint missing for transfer: " + transferProcessId));
            state.setDownloadedBytes(rangeStart + totalBytesUploaded);
            repository.save(state);
        }
    }
}
```

Remove the inner class. The outer class closing `}` remains.

- [ ] **Step 2: Update the `transfer()` method to pass the state object and add a final flush**

In `HttpPushTransferStrategy.transfer()`, replace:

```java
        CheckpointCallbackImpl checkpointCallback = new CheckpointCallbackImpl(
                transferProcess.getId(), rangeStart, transferArtifactStateRepository);

        // Always generate a fresh presigned URL for PUSH (provider controls the source)
        String presignedUrl = s3ClientService.generateGetPresignedUrl(
                s3Properties.getBucketName(), transferProcess.getDatasetId(), Duration.ofDays(1L));

        return transfer(presignedUrl, destinationS3Properties, rangeStart, cancellationToken, checkpointCallback)
                .thenAccept(key -> log.info("Pushed transfer process id - {} data!", key));
```

With:

```java
        CheckpointCallbackImpl checkpointCallback = new CheckpointCallbackImpl(
                checkpoint, rangeStart, transferArtifactStateRepository);

        // Always generate a fresh presigned URL for PUSH (provider controls the source)
        String presignedUrl = s3ClientService.generateGetPresignedUrl(
                s3Properties.getBucketName(), transferProcess.getDatasetId(), Duration.ofDays(1L));

        return transfer(presignedUrl, destinationS3Properties, rangeStart, cancellationToken, checkpointCallback)
                .thenAccept(key -> {
                    checkpointCallback.flush();
                    log.info("Pushed transfer process id - {} data!", key);
                });
```

- [ ] **Step 3: Update the `setUp()` preload in `HttpPushTransferStrategyTest`**

In `HttpPushTransferStrategyTest.setUp()`, replace:

```java
        try {
            Class.forName("it.eng.datatransfer.service.api.strategy.HttpPushTransferStrategy$CheckpointCallbackImpl",
                    false, HttpPushTransferStrategy.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot pre-load CheckpointCallbackImpl", e);
        }
```

With:

```java
        try {
            Class.forName("it.eng.datatransfer.service.api.strategy.CheckpointCallbackImpl",
                    false, HttpPushTransferStrategy.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot pre-load CheckpointCallbackImpl", e);
        }
```

- [ ] **Step 4: Add an assertion that the final `flush()` triggers a save in `transfer_success`**

In `HttpPushTransferStrategyTest.transfer_success()`, after `assertDoesNotThrow(() -> strategy.transfer(transferProcess).join())`, add:

```java
            // The final flush in thenAccept must persist the in-memory state
            verify(transferArtifactStateRepository, atLeastOnce())
                    .save(any(TransferArtifactState.class));
```

The import `import it.eng.datatransfer.model.TransferArtifactState;` is already present.

- [ ] **Step 5: Run `HttpPushTransferStrategyTest` and confirm it passes**

```bash
mvn -pl data-transfer -am -Dtest=HttpPushTransferStrategyTest test 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategy.java \
        data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategyTest.java
git commit -m "refactor(data-transfer): migrate HttpPushTransferStrategy to shared CheckpointCallbackImpl

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 4: Full module verification

**Files:** None changed — verification only.

---

- [ ] **Step 1: Run the full `data-transfer` module test suite**

```bash
mvn -pl data-transfer -am test 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`, zero test failures, zero errors.

- [ ] **Step 2: Run Checkstyle to confirm no style violations**

```bash
mvn -pl data-transfer -am validate 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Final commit if no issues found**

No code changes at this step. If steps 1–2 reveal a test failure or style violation, fix it, then re-run before committing.
