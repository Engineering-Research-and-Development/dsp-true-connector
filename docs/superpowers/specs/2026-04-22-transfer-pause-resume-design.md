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
| Who can suspend | Either party (Consumer **or** Provider) — both strategies, both roles |
| Who can resume | Only the party who sent the last `TransferSuspensionMessage` |
| Initial `TransferStartMessage` | Provider only (standard DSP) |
| Resume `TransferStartMessage` | Either party — DSP spec permits this; validated against `suspendedBy` |
| Suspend effect | Immediate — `CancellationRegistry` is signalled inline with `TransferSuspensionMessage` handling, no intermediary step |
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

One new field is added: **`suspendedBy`** (`String` — `"CONSUMER"` or `"PROVIDER"`). This is set when the transfer is suspended and validated on every resume attempt.

After every uploaded part, the strategy upserts this document with updated `downloadedBytes`, `partNumber`, and the new ETag. On transfer `COMPLETED` or `TERMINATED`, the document is deleted.

### `TransferCancelledException` (new unchecked exception — `data-transfer`)

Thrown by the upload strategy when it detects the cancellation flag. Carries the number of bytes successfully uploaded at the point of cancellation (informational, used in logging). Caught in `downloadData()`'s `whenComplete`, which treats it as a graceful suspend rather than a failure.

### `PresignedUrlExpiredException` (new unchecked exception — `data-transfer`)

Thrown by the transfer strategy when a presigned URL returns `403 Forbidden` on resume. Triggers automatic termination (see Error Handling).

---

## Suspend Flow

Either party (Consumer or Provider) can send a `TransferSuspensionMessage`. Whoever sends it is recorded as `suspendedBy` and is the only one who may resume the transfer.

### Identifying the active party

The party running the active transfer thread is always the same:
- **PULL** — Consumer runs the download. Consumer's connector holds the `CancellationToken`.
- **PUSH** — Provider runs the upload. Provider's connector holds the `CancellationToken`.

When `suspendDataTransfer()` is called (either because this connector initiated the suspension, or because it received a `TransferSuspensionMessage`), it calls `cancellationRegistry.signal(transferProcessId)`. This is a no-op on the non-active side (no token registered there), so the same code path works unconditionally on both connectors.

### Suspension initiated by the ACTIVE party (own request)

*Example: Consumer suspends a PULL transfer; Provider suspends a PUSH transfer.*

1. Management API receives the suspend request.
2. `suspendDataTransfer()` is called:
   a. Records `suspendedBy = <this connector's role>` in `TransferArtifactState`.
   b. Transitions `TransferProcess` to `SUSPENDED` in MongoDB.
   c. Calls `cancellationRegistry.signal(transferProcessId)` — stops the running download/upload.
3. Sends `TransferSuspensionMessage` to the counterpart's callback endpoint.

### Suspension initiated by the NON-ACTIVE party (remote request)

*Example: Provider suspends a PULL transfer; Consumer suspends a PUSH transfer.*

1. Non-active party's management API receives the suspend request.
2. Non-active party sends `TransferSuspensionMessage` to the active party's callback endpoint.
3. Active party's protocol endpoint receives the message:
   a. Records `suspendedBy = <sender's role>` in `TransferArtifactState`.
   b. Transitions `TransferProcess` to `SUSPENDED` in MongoDB.
   c. Calls `cancellationRegistry.signal(transferProcessId)` — stops the running download/upload immediately.

### Upload side (running on executor thread)

After each part finishes uploading, before requesting the next chunk:

1. Call `onPartCompleted` callback → upsert `TransferArtifactState` to MongoDB.
2. Check `cancellationToken.get()` — if `true`:
   - Abort the in-progress S3 multipart upload.
   - Throw `TransferCancelledException`.

### `downloadData()` completion handler

`whenComplete` catches `TransferCancelledException`:
- Calls `cancellationRegistry.deregister(transferProcessId)`.
- Resets `isDownloadInProgress = false`.
- Does **not** publish `TRANSFER_FAILED` — the transfer is already `SUSPENDED`.
- Does **not** delete `TransferArtifactState` — it is needed for resume.

**Race condition guard:** If the transfer completes naturally between the state transition and the signal, `suspendDataTransfer()` checks whether the state is already terminal (`COMPLETED` / `TERMINATED`) and no-ops gracefully.

---

## Resume Flow

When `startTransfer()` is called on a `SUSPENDED` transfer, `downloadData()` runs the normal entry path but checks for an existing checkpoint first.

### Resume authorisation

A `TransferStartMessage` on a `SUSPENDED` transfer is accepted only if the sender's role matches `suspendedBy` recorded in `TransferArtifactState`. If it does not match, the message is rejected with a protocol error.

**Initial start vs. resume start:** The initial `TransferStartMessage` (from `REQUESTED` state) can only be sent by the Provider (standard DSP). Resume `TransferStartMessage` (from `SUSPENDED` state) can be sent by either party — DSP spec explicitly permits this — but is gated by the `suspendedBy` check above.

### Auto-trigger on TransferStartMessage

The transfer execution (download/upload) is triggered **automatically** when a `TransferStartMessage` is received, with no separate management API call needed — mirroring how `TransferCompletionMessage` is sent automatically when a download finishes.

The `automaticTransfer` property guard is **removed** from this path. The active-party connector always auto-starts on receipt.

| Role | Format | Direction | Trigger point | Action |
|------|--------|-----------|---------------|--------|
| CONSUMER | HTTP_PULL | Receives `TransferStartMessage` (initial from Provider, or resume when Provider suspended) | End of `startDataTransfer()` | Publish `AutoTransferDownloadEvent` |
| CONSUMER | HTTP_PULL | Sends `TransferStartMessage` (resume when Consumer suspended) | After 200 OK in `startTransfer()` API method | Publish `AutoTransferDownloadEvent` |
| PROVIDER | HTTP_PUSH | Sends `TransferStartMessage` (initial or resume) | After 200 OK in `processStart()` chain | Already calls `processDownload()` |
| CONSUMER | HTTP_PUSH | Receives `TransferStartMessage` | No action — Provider is active party | — |
| PROVIDER | HTTP_PULL | Receives `TransferStartMessage` | No action — Consumer is active party | — |

Because `downloadData()` already checks for a `TransferArtifactState` checkpoint, the same code path handles both fresh downloads and resumes — no separate "resume" API call is required.

### Resume message flow

**Case A — Active party resumes (Consumer resumes PULL they suspended; Provider resumes PUSH they suspended):**
1. Management API receives the resume request.
2. Validates `suspendedBy == <this connector's role>`.
3. Sends `TransferStartMessage` to counterpart's callback endpoint.
4. Counterpart receives it, transitions state to `STARTED`, sends 200 OK.
5. Sender's `startTransfer()` receives 200 OK → auto-triggers `downloadData()` / `processDownload()`.

**Case B — Non-active party resumes (Provider resumes a PULL they suspended; Consumer resumes a PUSH they suspended):**
1. Management API receives the resume request.
2. Validates `suspendedBy == <this connector's role>`.
3. Sends `TransferStartMessage` to active party's callback endpoint.
4. Active party receives it, transitions state to `STARTED`, auto-triggers `downloadData()` via `AutoTransferDownloadEvent`.

### Resume detection in `downloadData()`

1. Look up `TransferArtifactState` by `transferProcessId` from `TransferArtifactStateRepository`.
2. If found → resume path (publish `TRANSFER_RESUMED` audit event).
3. If not found → fresh download (existing behaviour, unchanged).

### Resume path

1. Load `downloadedBytes` from the checkpoint — this is the HTTP `Range` start offset.
2. Register a fresh `AtomicBoolean(false)` in `CancellationRegistry`.
3. Call `strategy.transfer(transferProcess, Optional<TransferArtifactState>)` with the loaded state.
4. The strategy opens the HTTP connection with `Range: bytes=N-` instead of a plain `GET`
   - S3 / MinIO responds with `206 Partial Content`
   - The `Range` header is NOT part of the presigned URL signature — safe to add on PULL and PUSH alike (verified by `MinioPresignedUrlRangeIT`)
5. Start a **fresh** S3 multipart upload (new `uploadId`) — we do not continue the aborted multipart
   - The destination object key is unchanged; the new multipart replaces any partial state
6. Checkpointing and cancellation proceed identically to the original download.

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

## Audit Logging

Audit events are published at every significant success point and on every exception path. The existing `AuditEventPublisher` and `AuditEventType` enum are used throughout.

### New `AuditEventType` values

Two new values are added to the enum:

| Enum value | Label | Where published |
|---|---|---|
| `TRANSFER_PAUSED` | `"Transfer paused"` | `whenComplete` catches `TransferCancelledException` |
| `TRANSFER_RESUMED` | `"Transfer resumed"` | `downloadData()` detects existing `TransferArtifactState` |
| `TRANSFER_URL_EXPIRED` | `"Transfer URL expired"` | `whenComplete` catches `PresignedUrlExpiredException` |

`TRANSFER_FAILED` already exists and is published on other throwables.

### Audit event placement

| Event | Type | Payload fields |
|---|---|---|
| Transfer download / upload started | `PROTOCOL_TRANSFER_STARTED` (existing) | `transferProcessId`, `role`, `format`, `resuming=false/true`, `rangeStart` |
| Transfer paused (graceful stop on cancellation token) | `TRANSFER_PAUSED` (new) | `transferProcessId`, `role`, `format`, `downloadedBytes`, `suspendedBy` |
| Transfer resumed (checkpoint detected) | `TRANSFER_RESUMED` (new) | `transferProcessId`, `role`, `format`, `resumeFromBytes` |
| Transfer completed successfully | `TRANSFER_COMPLETED` (existing) | `transferProcessId`, `role`, `format` |
| Transfer URL expired on resume | `TRANSFER_URL_EXPIRED` (new) | `transferProcessId`, `role`, `format`, `errorMessage` |
| Transfer failed (other exception) | `TRANSFER_FAILED` (existing) | `transferProcessId`, `role`, `format`, `errorMessage` |
| `suspendedBy` mismatch on resume attempt | `PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR` (existing) | `transferProcessId`, `suspendedBy`, `requestedBy`, `errorMessage` |

### Logging rules

- All audit events on the **success path** are published after the state is persisted.
- All audit events on **exception paths** are published before cleanup (so the state is captured at the point of failure).
- `cancellationRegistry.deregister()` is always called **before** audit publishing on all paths.
- Standard `log.info` / `log.error` / `log.warn` SLF4J calls accompany every audit event for operational visibility.

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
| `AuditEventType.java` | Add `TRANSFER_PAUSED`, `TRANSFER_RESUMED`, `TRANSFER_URL_EXPIRED` enum values |
| `TransferArtifactState.java` | Add `suspendedBy` field (`String`) |
| `AbstractDataTransferService.java` | Remove `automaticTransfer` guard from auto-trigger in `startDataTransfer()`; publish `AutoTransferDownloadEvent` unconditionally for `HTTP_PULL` + `CONSUMER` receiver; validate `suspendedBy` on `SUSPENDED` → `STARTED` transition |
| `DataTransferAPIService.java` | Add `CancellationRegistry` + `TransferArtifactStateRepository` deps; checkpoint lambda in `downloadData()`; signal + set `suspendedBy` in `suspendTransfer()`; checkpoint lookup + delete in resume/complete paths; `PresignedUrlExpiredException` handler; auto-trigger `downloadData()` after 200 OK for `HTTP_PULL` + `CONSUMER` in `startTransfer()`; full audit logging on all success and exception paths |
| `DataTransferStrategy.java` | Add `transfer(TransferProcess, Optional<TransferArtifactState>)` overload |
| `HttpPullTransferStrategy.java` | Add resume context parameter; set `Range` header; throw `PresignedUrlExpiredException` on 403 |
| `HttpPushTransferStrategy.java` | Add resume context parameter; set `Range` header on resume |
| `S3UploadStrategy.java` | Add 8-parameter `uploadFile` overload |
| `S3SyncUploadStrategy.java` | Implement cancellation check + abort after each part |
| `S3AsyncUploadStrategy.java` | Implement cancellation check + abort in parallel part path |
| `S3ClientService.java` | Add 8-parameter `uploadFile` overload |
| `S3ClientServiceImpl.java` | Implement new overload, thread params to strategy |
