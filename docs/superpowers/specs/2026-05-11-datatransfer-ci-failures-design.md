# Design: Data Transfer CI Failure Fixes

**Date:** 2026-05-11  
**Status:** Approved

---

## Problem Statement

All data-transfer GitHub Actions test suites fail. Two independent root causes produce different failure symptoms:

| Suite | Failure | Root Cause |
|---|---|---|
| http-push (and others) | Node.js `Fatal JavaScript invalid size error 169220804` | Newman/V8 crashes buffering a 160 MB presigned-URL download |
| http-pull-async | Consumer stuck in `STARTED` forever, Newman times out | Race condition: OLE in `completeTransfer()` + spurious second download attempt |

---

## Fix 1 — Test Data File Size (Newman V8 Crash)

### Root cause

`ci/docker/generate-test-data.sh` intentionally generates a ~160 MB `ENG-employee.json` to give the suspend/resume tests enough upload time. When Newman downloads this file via the presigned S3 URL for the data-integrity assertion, V8 tries to allocate a ~160 MB `TypedArray`. V8's hard limit is approximately 162 MB (crbug.com/1201626); the allocation fails with a fatal error and kills the Node.js process.

The connector code is correct — a real S3 client would stream the file without issue. Only Newman buffers the full body.

### Fix

Change `TARGET_SIZE_MB` in `ci/docker/generate-test-data.sh`:

```
TARGET_SIZE_MB=120
```

**Why 120 MB is sufficient:** At local Docker network speeds (~100 MB/s), a 120 MB file takes ~1.2 seconds to upload via multipart. The Newman test sends the suspend signal within milliseconds of starting the transfer, so 1.2 seconds is ample time for the suspend message to arrive mid-upload and exercise the suspend/resume path.

**Why 120 MB is safe for Newman:** V8's limit is ~162 MB. 120 MB is safely below that threshold with ~35% headroom.

---

## Fix 2 — HTTP PULL Async Race Condition

### Root cause

The race involves three concurrent actors after a suspend/resume cycle where the ASYNC S3 upload continued running during the SUSPENDED state:

1. **First download** (`http-pull-transfer-1` → `aws-client-0-*` threads): S3 multipart upload completes, calls `downloadData().whenComplete()` → saves `withDownloadComplete()` → calls `completeTransfer()`.

2. **Case A resume** (`http-nio-8080-exec-*` thread): `startTransfer()` reads the entity, sees `isDownloaded=false` (snapshot taken before `withDownloadComplete()` committed), triggers second `downloadData()` call on `ForkJoinPool`.

3. **Second download** (`ForkJoinPool.commonPool-worker-*`): reads checkpoint (`downloadedBytes = 167,514,212` = full file size), sends `Range: bytes=167514212-` to provider → **HTTP 416 Range Not Satisfiable**.

Meanwhile, `completeTransfer()` from the first download tries to save with a stale `@Version` → **`OptimisticLockingFailureException`**. The save silently fails (caught by `.exceptionally(t -> null)`). Consumer stays in `STARTED` forever.

### Key code locations

| File | Line | Description |
|---|---|---|
| `DataTransferAPIService.java` | 530–532 | `completeTransfer()` saves with stale `@Version` — **Bug A** |
| `DataTransferAPIService.java` | 418 | Case A resume checks stale `transferProcessStarted.isDownloaded()` — **Bug B** |
| `DataTransferAPIService.java` | 822–823 | Existing re-read pattern (reference for Fix A style) |
| `CancellationRegistry.java` | 69–71 | `isRegistered()` — in-memory download-in-flight indicator |

### Fix A — OLE catch-and-retry in `completeTransfer()`

Replace the bare `save()` at line 532 with a try-catch that re-reads on `OptimisticLockingFailureException`:

```java
// DataTransferAPIService.java, inside completeTransfer(), if (response.isSuccess()) block

try {
    transferProcessRepository.save(transferProcessCompleted);
} catch (OptimisticLockingFailureException e) {
    log.warn("OLE saving COMPLETED for process {}, retrying with fresh read", transferProcessId);
    transferProcessRepository.findById(transferProcessId)
        .ifPresent(freshTp -> transferProcessRepository.save(
            freshTp.copyWithNewTransferState(TransferState.COMPLETED)));
}
```

- No extra DB read on the happy path.
- Retry only fires when the OLE race actually occurs.
- `transferProcessCompleted` continues to be used for audit event and return value (its IDs and role are correct; only `@Version` is stale).

### Fix B — Case A resume: check `cancellationRegistry.isRegistered()`

Add a third branch to the Case A resume decision block (lines 418–452):

```java
if (transferProcessStarted.isDownloaded()) {
    // Upload completed before/during suspend — complete directly without re-downloading
    log.info("Case A resume for process {} — data already downloaded. Completing directly.", tpIdForResume);
    CompletableFuture.runAsync(() -> {
        try {
            completeTransfer(tpIdForResume);
        } catch (Exception e) {
            log.error("Auto-completion after Case A resume failed for process {}: {}",
                    tpIdForResume, e.getMessage());
        }
    });
} else if (cancellationRegistry.isRegistered(tpIdForResume)) {
    // First download is still in flight (ASYNC upload running) — do not trigger a second
    // download. The in-flight download will call completeTransfer() when it finishes.
    log.info("Case A resume for process {} — download in progress. Letting first download complete.", tpIdForResume);
} else {
    // No download in progress — trigger one
    log.info("Case A resume for process {}. Auto-triggering download.", tpIdForResume);
    CompletableFuture.runAsync(() -> {
        try {
            downloadData(tpIdForResume)
                    .exceptionally(err -> {
                        log.error("Auto-triggered download failed after Case A resume for process {}: {}",
                                tpIdForResume, err.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.error("Auto-triggered download failed after Case A resume for process {}: {}",
                    tpIdForResume, e.getMessage());
        }
    });
}
```

`cancellationRegistry` is already injected into `DataTransferAPIService`. No new dependencies.

#### Remaining edge case

If `cancellationRegistry.deregister()` runs (line 806) just before `transferProcessRepository.save(tp.withDownloadComplete())` commits (line 822–823), a ~1–5 ms window exists where `isRegistered()` returns `false` but `isDownloaded` is not yet `true` in DB. In this case the "no download in progress" branch triggers a second `downloadData()`. That second download:
- reads from DB → sees `isDownloaded=true` (save has since completed) → aborts cleanly, OR
- reads `isDownloaded=false` → proceeds → HTTP 416 → exception

Fix A (OLE retry) ensures the consumer is marked COMPLETED even if this residual case occurs.

---

## Files Changed

| File | Change |
|---|---|
| `ci/docker/generate-test-data.sh` | `TARGET_SIZE_MB=160` → `TARGET_SIZE_MB=120` |
| `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java` | Fix A: OLE catch-and-retry in `completeTransfer()`; Fix B: third branch in Case A resume using `cancellationRegistry.isRegistered()` |

---

## Testing

- Existing Newman suite `datatransfer-api-http-pull-tests` must pass end-to-end (no timeout on "Wait for download to complete")
- Existing Newman suite `datatransfer-api-http-push-tests` must pass (no V8 crash on "Use presignedURL to download actual data")
- Existing unit/integration tests in `data-transfer` module must not regress: `mvn -pl data-transfer -am test`
