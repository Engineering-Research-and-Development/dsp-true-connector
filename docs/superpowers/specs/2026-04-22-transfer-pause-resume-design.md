# Transfer Pause / Resume Design

**Date:** 2026-04-22  
**Module:** `data-transfer`, `tools`  
**Status:** Approved

---

## Problem

When a `TransferSuspensionMessage` is received, the running download is not interrupted — only the MongoDB state changes. On resume, the entire download starts from byte 0. This wastes bandwidth, time, and S3 storage for large files, and leaves dangling incomplete S3 multipart uploads.

---

## Decisions

| Question | Decision |
|---|---|
| Resume granularity | Abort multipart on suspend; `Range: bytes=N-` + fresh multipart on resume |
| Who owns suspend (PULL) | Consumer |
| Who owns suspend (PUSH) | Provider |
| Cancellation style | Graceful — finish current chunk, then stop |
| Implementation approach | `AtomicBoolean` cancellation token threaded through call stack |
| Crash / disconnect resilience | Full — checkpoint to MongoDB after every part |

---

## Architecture Overview

Three pieces of new infrastructure are added on top of the existing code:

### `CancellationRegistry` (new — `data-transfer`)

A Spring `@Component` wrapping `ConcurrentHashMap<String, AtomicBoolean>`.

- `register(transferProcessId)` — called at the start of `downloadData()`; inserts a `new AtomicBoolean(false)`
- `signal(transferProcessId)` — called by `suspendTransfer()`; sets the flag to `true`
- `deregister(transferProcessId)` — called in `whenComplete` on all exit paths

No other component needs to know about this map.

### `TransferArtifactState` (existing MongoDB model — `data-transfer`)

The checkpoint store. Already has all required fields: `uploadId`, `partNumber`, `etags`, `downloadedBytes`, `totalBytes`, `presignURL`, `destBucket`, `destObject`. Currently unpopulated — this feature populates it.

After every uploaded part, the strategy upserts this document with updated `downloadedBytes`, `partNumber`, and the new ETag. On transfer `COMPLETED` or `TERMINATED`, the document is deleted.

### `TransferCancelledException` (new unchecked exception — `data-transfer`)

Thrown by the upload strategy when it detects the cancellation flag. Carries the number of bytes successfully uploaded at the point of cancellation (informational, used in logging). Caught in `downloadData()`'s `whenComplete`, which treats it as a graceful suspend rather than a failure.

### `PresignedUrlExpiredException` (new unchecked exception — `data-transfer`)

Thrown by the transfer strategy when a presigned URL returns `403 Forbidden` on resume. Triggers automatic termination (see Error Handling).

---

## Suspend Flow

### Request side (triggered by `TransferSuspensionMessage`)

1. `AbstractDataTransferService.suspendDataTransfer()` transitions `TransferProcess` to `SUSPENDED` in MongoDB (existing behaviour, unchanged).
2. `DataTransferAPIService.suspendTransfer()` calls `cancellationRegistry.signal(transferProcessId)` immediately after — sets the `AtomicBoolean` to `true`.

**Race condition guard:** If the transfer completes naturally between the state transition (step 1) and the signal (step 2), `suspendTransfer()` checks whether the state is already terminal (`COMPLETED` / `TERMINATED`) and no-ops gracefully.

### Upload side (running on executor thread)

After each part finishes uploading, before requesting the next chunk:

1. Call `onPartCompleted` callback → upsert `TransferArtifactState` to MongoDB
2. Check `cancellationToken.get()` — if `true`:
   - Abort the in-progress S3 multipart upload
   - Throw `TransferCancelledException`

### `downloadData()` completion handler

`whenComplete` catches `TransferCancelledException`:
- Calls `cancellationRegistry.deregister(transferProcessId)`
- Resets `isDownloadInProgress = false`
- Does **not** publish `TRANSFER_FAILED` — the transfer is already `SUSPENDED`
- Does **not** delete `TransferArtifactState` — it is needed for resume

---

## Resume Flow

When `startTransfer()` is called on a `SUSPENDED` transfer, `downloadData()` runs the normal entry path but checks for an existing checkpoint first.

### Resume detection in `downloadData()`

1. Look up `TransferArtifactState` by `transferProcessId` from `TransferArtifactStateRepository`
2. If found → resume path
3. If not found → fresh download (existing behaviour, unchanged)

### Resume path

1. Load `downloadedBytes` from the checkpoint — this is the HTTP `Range` start offset
2. Register a fresh `AtomicBoolean(false)` in `CancellationRegistry`
3. Call `strategy.transfer(transferProcess, Optional<TransferArtifactState>)` with the loaded state
4. The strategy opens the HTTP connection with `Range: bytes=N-` instead of a plain `GET`
   - S3 / MinIO responds with `206 Partial Content`
   - The `Range` header is NOT part of the presigned URL signature — safe to add on PULL and PUSH alike
5. Start a **fresh** S3 multipart upload (new `uploadId`) — we do not continue the aborted multipart
   - The destination object key is unchanged; the new multipart replaces any partial state
6. Checkpointing and cancellation proceed identically to the original download

### Strategy interface change

`DataTransferStrategy` gets a second method:

```java
CompletableFuture<Void> transfer(TransferProcess transferProcess,
                                 Optional<TransferArtifactState> resumeContext);
```

The existing `transfer(TransferProcess)` becomes a default method calling through with `Optional.empty()`.

### PUSH presigned URL on resume

For `HTTP_PUSH`, the provider generates a **fresh** 1-day presigned URL each time `transfer()` is called (existing behaviour). There is no expiry issue on resume.

### Cleanup on completion

When `whenComplete` fires with no error (natural finish), `downloadData()` deletes the `TransferArtifactState` document via `transferArtifactStateRepository.deleteById(transferProcessId)`.

---

## Upload Strategy Changes

### `UploadCheckpointCallback` (new functional interface — `tools`)

```java
@FunctionalInterface
public interface UploadCheckpointCallback {
    void onPartCompleted(int partNumber, String etag, long totalBytesUploaded);
}
```

### `S3UploadStrategy` interface

A new overload is added:

```java
CompletableFuture<String> uploadFile(InputStream inputStream,
                                     S3ClientRequest s3ClientRequest,
                                     String bucketName,
                                     String objectKey,
                                     String contentType,
                                     String contentDisposition,
                                     AtomicBoolean cancellationToken,
                                     UploadCheckpointCallback onPartCompleted);
```

The existing 6-parameter `uploadFile` becomes a default method that calls through with `new AtomicBoolean(false)` and a no-op callback. No callers outside the transfer feature are affected.

### `S3SyncUploadStrategy` loop changes

After each `uploadPart` call:
1. Call `onPartCompleted(partNumber, etag, cumulativeBytes)`
2. Check `cancellationToken.get()` — if `true`, call `abortMultipartUpload(...)` then throw `TransferCancelledException`

### `S3AsyncUploadStrategy` changes

The cancellation check is added inside each part's `whenComplete` handler. If the flag is set when a part completes, no new parts are submitted to `partFutures`. After all in-flight parts drain (`allOf` completes), an abort is triggered before throwing `TransferCancelledException`. The `Semaphore` ensures in-flight parts complete gracefully before the abort.

### `S3ClientService` / `S3ClientServiceImpl`

A matching new 8-parameter `uploadFile` overload is added that threads `cancellationToken` and `onPartCompleted` down to the strategy.

---

## `DataTransferAPIService` wiring

The `onPartCompleted` callback is constructed in `downloadData()` as a lambda that upserts `TransferArtifactState`:

```java
UploadCheckpointCallback checkpoint = (partNumber, etag, totalBytes) -> {
    TransferArtifactState state = /* load existing or build new */;
    state.incrementPartNumber();
    state.addEtag(etag);
    state.setDownloadedBytes(totalBytes);
    transferArtifactStateRepository.save(state);
};
```

`DataTransferAPIService` gains two new constructor dependencies: `CancellationRegistry` and `TransferArtifactStateRepository`.

---

## Error Handling

### Presigned URL expiry (PULL only)

If the HTTP connection returns `403 Forbidden` on resume, the strategy throws `PresignedUrlExpiredException`. `downloadData()`'s `whenComplete` catches it and:

1. Sends `TransferTerminationMessage` with code `"409"` and reason `"download URL expired"` to the counterpart
2. Transitions the transfer to `TERMINATED`
3. Deletes `TransferArtifactState`
4. Cleans up the temporary bucket user if applicable (best-effort)

### `resetStaleDownloadingFlags` on startup

Left exactly as-is — resets `isDownloadInProgress=true` UI flags only. No checkpoint awareness. Any persisted `TransferArtifactState` documents survive and are picked up on the next `startTransfer()` call.

### Orphaned checkpoints

Deleted on `TERMINATED` (including URL-expiry path) and on natural `COMPLETED`. No scheduled cleanup is needed.

### Exception routing in `whenComplete`

`cancellationRegistry.deregister(transferProcessId)` is called on **every** exit path (success and all error types) before any other action.

| Exit condition | Action |
|---|---|
| Success (no throwable) | Deregister token, save completed `TransferProcess`, delete `TransferArtifactState`, send `TransferCompletionMessage`. |
| `TransferCancelledException` | Deregister token, reset `isDownloadInProgress`. No failure event — transfer is already `SUSPENDED`. Checkpoint preserved. |
| `PresignedUrlExpiredException` | Deregister token, send `TransferTerminationMessage` (code `"409"`, reason `"download URL expired"`), transition to `TERMINATED`, delete checkpoint, clean up temp user. |
| Any other `Throwable` | Deregister token, existing error path — reset `isDownloadInProgress`, publish `TRANSFER_FAILED`. |

---

## Testing

### Unit tests (new)

| Class | What is tested |
|---|---|
| `CancellationRegistryTest` | Register, signal, deregister, concurrent access |
| `S3SyncUploadStrategyTest` | Cancellation mid-loop aborts multipart and throws `TransferCancelledException`; checkpoint callback fires after each part with correct cumulative bytes |
| `S3AsyncUploadStrategyTest` | Same as above for the async parallel path |
| `HttpPullTransferStrategyTest` | `Range` header set when `TransferArtifactState` present; plain GET when absent |
| `HttpPushTransferStrategyTest` | `Range` header set when `TransferArtifactState` present; fresh presigned URL generated regardless |

### Integration tests (in `connector` module)

| Class | What is tested |
|---|---|
| `TransferSuspendResumeIT` | Full round-trip: start → suspend mid-flight → verify checkpoint in MongoDB → resume → verify `COMPLETED` and checkpoint deleted |
| `TransferUrlExpiryIT` | Mock 403 on resume → verify `TERMINATED` state and termination message sent with code `"409"` |

---

## Files Changed

### New files

| File | Module |
|---|---|
| `it/eng/datatransfer/service/api/CancellationRegistry.java` | `data-transfer` |
| `it/eng/datatransfer/exceptions/TransferCancelledException.java` | `data-transfer` |
| `it/eng/datatransfer/exceptions/PresignedUrlExpiredException.java` | `data-transfer` |
| `it/eng/tools/s3/service/upload/UploadCheckpointCallback.java` | `tools` |

### Modified files

| File | Change summary |
|---|---|
| `DataTransferAPIService.java` | Add `CancellationRegistry` + `TransferArtifactStateRepository` deps; checkpoint lambda in `downloadData()`; signal in `suspendTransfer()`; checkpoint lookup + delete in resume/complete paths; `PresignedUrlExpiredException` handler |
| `DataTransferStrategy.java` | Add `transfer(TransferProcess, Optional<TransferArtifactState>)` overload |
| `HttpPullTransferStrategy.java` | Add resume context parameter; set `Range` header; throw `PresignedUrlExpiredException` on 403 |
| `HttpPushTransferStrategy.java` | Add resume context parameter; set `Range` header on resume |
| `S3UploadStrategy.java` | Add 8-parameter `uploadFile` overload |
| `S3SyncUploadStrategy.java` | Implement cancellation check + abort after each part |
| `S3AsyncUploadStrategy.java` | Implement cancellation check + abort in parallel part path |
| `S3ClientService.java` | Add 8-parameter `uploadFile` overload |
| `S3ClientServiceImpl.java` | Implement new overload, thread params to strategy |
