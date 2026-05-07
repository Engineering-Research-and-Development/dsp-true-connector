# S3 Upload Checkpoint Optimization Design

**Date:** 2026-05-07  
**Status:** Approved  
**Scope:** `data-transfer` module — `HttpPushTransferStrategy`, `HttpPullTransferStrategy`, `CheckpointCallbackImpl`

---

## Problem

Both `HttpPushTransferStrategy` and `HttpPullTransferStrategy` contain an identical inner class `CheckpointCallbackImpl` that persists S3 multipart upload progress to MongoDB after every part. The current implementation has two problems:

### 1. Correctness — silent checkpoint drops under parallel uploads

`S3AsyncUploadStrategy` uploads up to `MAX_PARALLEL_PARTS = 4` parts concurrently. Each completed part calls `onPartCompleted`, which does a `findById` → mutate → `save` sequence. With 4 concurrent completions:

- All 4 threads call `findById` and get the document at version N.
- Thread 1 saves successfully; version becomes N+1.
- Threads 2–4 save with stale version N → `OptimisticLockingFailureException`.
- The exception is swallowed (no retry logic) → checkpoint for 3 of 4 parts is silently lost.

On a suspend/resume cycle, the persisted byte offset can be stale, causing unnecessary re-uploads from an earlier position than required.

### 2. Performance — 2 DB round-trips per part

Each `onPartCompleted` call does a `findById` (read) + `save` (write). For a 1 GB file with 10 MB chunks: **200 MongoDB round-trips** just for checkpointing. The `findById` is unnecessary in the common case — the document was already loaded at the start of `transfer()`.

---

## Solution: In-memory state with periodic flush

Hold the `TransferArtifactState` object in memory inside the callback. Update it in-memory on every part completion. Flush to MongoDB every `FLUSH_EVERY_N_PARTS` parts, always on upload start, and once more at transfer completion.

---

## Constants

```java
// S3AsyncUploadStrategy — unchanged
private static final int MAX_PARALLEL_PARTS = 4;

// CheckpointCallbackImpl — aligns flush cadence with parallel wave size
static final int FLUSH_EVERY_N_PARTS = MAX_PARALLEL_PARTS; // = 4
```

Keeping the two as separate named constants (with one referencing the other) makes the alignment explicit while allowing independent tuning in the future.

**DB write reduction:** For a 1 GB file (100 parts): `ceil(100/4) + 1 = 26 writes` vs 200 currently — approximately 8× fewer writes.

---

## Architecture

```
transfer()
  └─ load TransferArtifactState from DB (or create new)
  └─ new CheckpointCallbackImpl(state, rangeStart, repository)
  └─ uploadFile(..., checkpointCallback)
        ├─ onUploadStarted  → set uploadId in memory, flush immediately
        ├─ onPartCompleted  → update downloadedBytes in memory
        │                      flush every FLUSH_EVERY_N_PARTS parts
        └─ future completes
  └─ .thenAccept → checkpointCallback.flush()   ← guaranteed final flush
```

---

## Component: `CheckpointCallbackImpl`

Extracted from both strategies into a single **package-private** class in `it.eng.datatransfer.service.api.strategy`. Removing the duplication is the correct step since the two inner classes are identical.

### Constructor

```java
CheckpointCallbackImpl(TransferArtifactState state, long rangeStart,
                       TransferArtifactStateRepository repository)
```

Takes the already-loaded `TransferArtifactState` object instead of just the ID.

### `onUploadStarted`

```java
@Override
public synchronized void onUploadStarted(String uploadId) {
    state.setUploadId(uploadId);
    doFlush(); // always — upload ID must survive a crash for S3 abort recovery
}
```

Always flushes immediately. The S3 multipart upload ID is needed to abort an in-progress upload on crash recovery; it must be durable before any parts are uploaded.

### `onPartCompleted`

```java
@Override
public synchronized void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) {
    long newBytes = rangeStart + totalBytesUploaded;
    if (newBytes > state.getDownloadedBytes()) { // guard against out-of-order completions
        state.setDownloadedBytes(newBytes);
    }
    if (++partsSinceLastFlush >= FLUSH_EVERY_N_PARTS) {
        doFlush();
        partsSinceLastFlush = 0;
    }
}
```

`synchronized` serializes all concurrent callbacks from the parallel upload pool, eliminating the `OptimisticLockingFailureException` race. The `>` guard on `newBytes` handles the edge case where an out-of-order part completion would otherwise regress the stored offset.

### `flush()` (public, called by strategy)

```java
public synchronized void flush() {
    if (partsSinceLastFlush > 0) {
        doFlush();
        partsSinceLastFlush = 0;
    }
}
```

The guard avoids a redundant save when the last periodic flush happened to land exactly on the final part (e.g. part 100 of 100 with `FLUSH_EVERY_N_PARTS = 4`).

### `doFlush()` (private)

```java
private void doFlush() {
    try {
        state = repository.save(state); // capture returned entity — updates @Version in memory
    } catch (OptimisticLockingFailureException e) {
        // suspendTransfer concurrently set suspendedBy — re-read and re-apply our fields
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
```

`state = repository.save(state)` captures the returned entity. Spring Data MongoDB returns the saved entity with the updated `@Version` value; assigning it back to `state` ensures the next flush does not send a stale version.

A single retry on `OptimisticLockingFailureException` is sufficient: the `suspendTransfer` path sets `suspendedBy` once, then signals `cancellationToken = true`, so no further concurrent modification from that path will occur.

---

## Strategy changes

Both strategies replace the inner `CheckpointCallbackImpl` with the shared class and pass the state object:

```java
// Before
CheckpointCallbackImpl checkpointCallback = new CheckpointCallbackImpl(
        transferProcess.getId(), rangeStart, transferArtifactStateRepository);

// After
CheckpointCallbackImpl checkpointCallback = new CheckpointCallbackImpl(
        checkpoint, rangeStart, transferArtifactStateRepository);
```

Final flush chained on the returned future:

```java
return transfer(presignedUrl, ..., cancellationToken, checkpointCallback)
        .thenAccept(key -> {
            checkpointCallback.flush();
            log.info("Stored transfer process id - {} data!", key);
        });
```

---

## Error handling

| Scenario | Behaviour |
|---|---|
| `OptimisticLockingFailureException` in `doFlush()` | Single retry: re-read fresh document, re-apply `downloadedBytes` and `uploadId`, save |
| MongoDB connection error in `doFlush()` | Propagates through `onPartCompleted` → upload future fails → `downloadData().whenComplete()` handles as generic transfer failure |
| Document missing in retry re-read | Hard `IllegalStateException` — data integrity violation, surfaces loudly |
| Cancellation/suspension mid-upload | At most `FLUSH_EVERY_N_PARTS - 1` (= 3) parts / 30 MB not flushed; resume starts from last periodic checkpoint |

---

## Testing

New test class: `CheckpointCallbackImplTest` in `data-transfer`.

| Test | Verifies |
|---|---|
| Normal N-part upload | `repository.save()` called exactly `ceil(N / FLUSH_EVERY_N_PARTS)` times for periodic flushes, plus at most 1 for the final flush (0 if last part landed on a boundary) |
| `onUploadStarted` | Always flushes immediately, regardless of part counter state |
| Concurrent `onPartCompleted` (4 threads) | No `OptimisticLockingFailureException`; final `downloadedBytes` equals highest cumulative value |
| Out-of-order completions | `downloadedBytes` never regresses; final value is the highest seen |
| `OptimisticLockingFailureException` on first save | Re-reads, merges fields, retries once; `state` field updated with fresh `@Version` |
| Document missing on retry re-read | `IllegalStateException` thrown |

Existing `HttpPullTransferStrategyTest` and `HttpPushTransferStrategyTest` should be updated to verify the callback is constructed with the state object and that `flush()` is called once after the transfer future completes.
