# Resume Integrity Fixes Design

**Date:** 2026-05-25  
**Branch:** feature/suspend-resume-transfers  
**Related PR:** #241

## Problem

The PR #241 suspend/resume implementation has several correctness issues identified in a Copilot code review. The most critical is a data-corruption bug: when a transfer is suspended, the S3 multipart upload is aborted (all uploaded parts discarded), but on resume the HTTP download skips ahead to the saved byte offset. The resulting S3 object contains only bytes `N → end` and is permanently truncated.

Secondary issues: stale checkpoints left after success/expiry, a presigned URL logged at INFO level, an incorrect identifier passed to `PresignedUrlExpiredException` in Push, and a race window before `completeMultipartUpload` in the async strategy.

## Architecture Overview

The fix spans four areas:

1. **Suspend path** — stop aborting the multipart upload on `TransferCancelledException`
2. **Resume path** — validate the saved `uploadId` is still alive via `listParts`; fall back to restart from byte 0 if expired
3. **Checkpoint model** — persist part ETags and compute the byte cursor using the highest *contiguous* confirmed part to handle async out-of-order completions
4. **Housekeeping fixes** — checkpoint cleanup, logging, exception construction, pre-complete cancellation check

## Section 1: Suspend Path

**Files:** `S3SyncUploadStrategy`, `S3AsyncUploadStrategy`

**Change:** Remove `abortMultipartUpload` from the `TransferCancelledException` branch in both strategies. On intentional suspension, the multipart upload must stay open on S3 so it can be continued on resume. The general-error (non-cancellation) path keeps the abort call.

**`S3AsyncUploadStrategy` additional changes:**
- The cancellation check inside the `uploadParts` read loop currently calls `abortMultipartUpload` before throwing. Remove that abort — just throw `TransferCancelledException`.
- Add a cancellation check *between* `uploadParts(...)` and `completeMultipartUpload(...)` in the `thenComposeAsync` chain. If the token is set at that point, throw `TransferCancelledException` without calling complete or abort.

**S3 resource note:** Incomplete multipart uploads have no default expiry on AWS or MinIO — they persist until explicitly completed, aborted, or cleaned up by a bucket lifecycle rule. Suspended transfers consume zero connections and zero threads; S3 holds only the uploaded part bytes (billed as storage). Practically unlimited transfers may be suspended concurrently.

**In-flight part overlap on suspend (async only):** When cancellation fires in the async strategy, up to `MAX_PARALLEL_PARTS × chunkSize` (default ~40 MB) of parts already submitted may still be in flight and complete after the exception propagates. These parts are uploaded to S3 but not captured in the final checkpoint flush. On resume, those part numbers will be re-uploaded, overwriting the orphaned versions. S3 allows overwriting a part with the same part number. The resulting object is correct — the overlap causes only minor redundant bandwidth.

## Section 2: Resume Path

**Files:** `HttpPullTransferStrategy`, `HttpPushTransferStrategy`, `S3ClientService` (interface + impl), `S3SyncUploadStrategy`, `S3AsyncUploadStrategy`

### Validity check on resume

In `HttpPullTransferStrategy.transfer()` and `HttpPushTransferStrategy.transfer()`, replace:

```java
if (rangeStart > 0) {
    checkpoint.setUploadId(null); // BUG
}
```

With:

```java
if (rangeStart > 0 && checkpoint.getUploadId() != null) {
    try {
        s3ClientService.listParts(checkpoint.getUploadId(), bucketName, objectKey);
        // uploadId is alive — continue
    } catch (NoSuchUploadException e) {
        log.info("Multipart upload for process {} expired; restarting from byte 0", transferProcess.getId());
        rangeStart = 0;
        checkpoint.setDownloadedBytes(0);
        checkpoint.setUploadId(null);
        checkpoint.clearCompletedParts();
    }
}
```

**Pull vs Push `listParts` client selection:**

- **Pull:** The multipart upload is on the **local** MinIO/S3 (the destination). `S3ClientService` gets a new method `listParts(String uploadId, String bucketName, String objectKey)` that uses the local S3 client.
- **Push:** The multipart upload is on the **remote** (destination) S3 supplied via `destinationS3Properties`. `S3ClientService` gets a second overload `listParts(String uploadId, String objectKey, Map<String, String> destinationS3Properties)` that builds a remote S3 client via `S3ClientProvider`. The destination bucket name and object key are available from `TransferArtifactState.destBucket` / `TransferArtifactState.destObject` (these fields already exist in the model).

Both overloads return `ListPartsResponse`. Both sync and async clients support `ListParts` natively in the AWS SDK v2.

**AWS note:** AWS documentation explicitly states not to use `listParts` output as input to `completeMultipartUpload`. We use `listParts` *only* as a liveness check. The actual ETag data used for completion always comes from the persisted checkpoint.

### Upload strategy resume

When `checkpointCallback` reports a non-null existing `uploadId` (i.e., resume case), both strategies skip `createMultipartUpload` and use the existing `uploadId` directly. Part numbering starts from `lastPersistedPartNumber + 1` (derived from `completedParts.size()`).

`completeMultipartUpload` assembles its `CompletedPart` list by merging the persisted `completedParts` map (prior session) with the entries added in the current session, sorted by part number ascending.

## Section 3: Checkpoint Model

**Files:** `TransferArtifactState`, `CheckpointCallbackImpl`, `S3SyncUploadStrategy`, `S3AsyncUploadStrategy`

### `TransferArtifactState` model change

Replace the existing `List<String> etags` + `int partNumber` / `incrementPartNumber()` / `addEtag()` with:

```java
@Setter
private Map<Integer, String> completedParts = new LinkedHashMap<>();
```

Part number → ETag. MongoDB persists this as a subdocument. Helper methods:
- `addCompletedPart(int partNumber, String etag)` — adds an entry
- `clearCompletedParts()` — resets the map (used on fallback to byte 0)
- `getLastPartNumber()` — `completedParts.isEmpty() ? 0 : Collections.max(completedParts.keySet())`

Builder gains corresponding `completedParts(Map<...>)` builder method.

### `CheckpointCallbackImpl` changes

**`UploadCheckpointCallback.onPartCompleted` signature change:**

Change the third parameter from `long totalBytesUploaded` (cumulative, unreliable under async out-of-order completions) to `long partBytes` (the actual byte count of this specific part). Both upload strategies already have the per-part size available at the call site.

**Constructor:** No `chunkSize` parameter needed (removed — we now use the actual per-part byte count instead).

**`onPartCompleted(partNumber, etag, partBytes)`:**
1. Add `(partNumber, etag)` to `state.completedParts`
2. Record `partBytes` in a local `Map<Integer, Long> partSizes`
3. Recompute `downloadedBytes` as the highest *contiguous* part boundary:

```java
long contiguousBytes = 0;
for (int p = 1; partSizes.containsKey(p); p++) {
    contiguousBytes += partSizes.get(p);
}
state.setDownloadedBytes(rangeStart + contiguousBytes);
```

This guarantees the byte cursor never advances past a part that hasn't been confirmed and is exact for all part sizes including the final (smaller) part. Out-of-order completions in the async strategy are handled safely: if part 3 completes before part 2, the cursor stays at the part 1 boundary until part 2 arrives.

**`onUploadStarted(uploadId)`:** No change — already flushes immediately.

**`flush()`:** No change.

### `CheckpointCallbackImpl` — resume initialization

When initialized for a resume, the constructor receives the existing `TransferArtifactState` (which already has `completedParts` from the prior session). New completions from this session are added on top via `onPartCompleted`, so the map grows across sessions naturally.

A helper method `getExistingUploadId()` is added to `CheckpointCallbackImpl` to let the strategies check whether to skip `createMultipartUpload`.

## Section 4: Housekeeping Fixes

### Checkpoint cleanup on terminal paths (`DataTransferAPIService`)

**Success path** (after `tp.withDownloadComplete()` save):
```java
transferArtifactStateRepository.deleteById(transferProcessId);
```

**`PresignedUrlExpiredException` path** (after `terminateTransferWithReason`):
```java
transferArtifactStateRepository.deleteById(transferProcessId);
```

URL expiry is terminal — leaving the checkpoint risks a stale resume attempt if the process is somehow restarted.

### Presigned URL at INFO log level (`HttpPullTransferStrategy`)

Line ~189: `log.info("Presigned URL: {}", presignedUrl)` → `log.debug(...)`.

Presigned URLs are time-limited credentials. Logging them at INFO exposes them to anyone with log access. DEBUG is off by default in production.

### `PresignedUrlExpiredException` identifier in Push (`HttpPushTransferStrategy`)

Line ~182: `throw new PresignedUrlExpiredException(presignedUrl)` → `throw new PresignedUrlExpiredException(key)`.

`HttpPullTransferStrategy` already passes the transfer process ID as the identifier (not the full URL). Push should match Pull for consistency and to avoid leaking the URL in exception messages and audit events.

### Pre-complete cancellation check (`S3AsyncUploadStrategy`)

In the `thenComposeAsync` chain, add before `completeMultipartUpload`:

```java
.thenComposeAsync(uploadResult -> {
    if (cancellationToken.get()) {
        throw new CompletionException(new TransferCancelledException(objectKey));
    }
    return completeMultipartUpload(...);
})
```

This closes the narrow race window where a suspend signal arrives after the last part future completes but before `completeMultipartUpload` is called.

## Testing

- `CheckpointCallbackImplTest` — add tests for:
  - out-of-order part completions (cursor stays at contiguous boundary, using actual `partBytes`)
  - resume initialization (existing `completedParts` map carried forward)
  - `clearCompletedParts` on fallback
  - `onPartCompleted` with varying part sizes (last part smaller than others)
- `HttpPullTransferStrategyTest` / `HttpPushTransferStrategyTest` — add tests for:
  - resume with valid `uploadId` (no restart)
  - resume with expired `uploadId` (`NoSuchUploadException`) → restarts from 0 with INFO log
- `S3AsyncUploadStrategyTest` — add test for cancellation check between parts completion and `completeMultipartUpload`
- `DataTransferAPIServiceTest` — add tests verifying `deleteById` is called on success and on URL expiry
- `UploadCheckpointCallbackTest` (or updated existing tests) — verify new `partBytes` signature on both sync and async call sites

## Out of Scope

- CHANGELOG update (handled manually)
- Changes to the sync strategy's part ordering (sync is inherently sequential — no out-of-order issue)
- S3 multipart upload lifecycle rule configuration — an infrastructure concern, not application code
