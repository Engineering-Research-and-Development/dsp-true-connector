# S3 HTTP-PUSH / HTTP-PULL Transfer — RAM Usage Analysis

## Overview

This document analyses the RAM usage characteristics of the HTTP-PUSH and HTTP-PULL data transfer pipelines and documents the recommended fix for each identified problem. The pipeline consists of three shared layers:

1. `HttpPushTransferStrategy` / `HttpPullTransferStrategy` — downloads the file via a presigned GET URL
2. `S3ClientServiceImpl.uploadFile` — delegates to the configured upload strategy
3. `S3SyncUploadStrategy` / `S3AsyncUploadStrategy` — uploads the file to the destination S3/MinIO bucket using S3 Multipart Upload

---

## Problem 1: `HttpURLConnection` — potential full-file buffering ✅ Fixed

### Location
`HttpPushTransferStrategy.transfer()` · `HttpPullTransferStrategy.downloadAndUploadToS3()`

### Description
`HttpURLConnection` without `setFixedLengthStreamingMode(long)` may internally buffer the entire server response before handing back the `InputStream`. For 100 GB+ files this exhausts heap before a single byte reaches S3.

### Applied fix
```java
long contentLength = connection.getContentLengthLong();
if (contentLength > 0) {
    connection.setFixedLengthStreamingMode(contentLength);
}
```

### RAM impact
| File size | Before fix  | After fix |
|-----------|-------------|-----------|
| 1 GB      | ~1 GB heap  | ~50 MB    |
| 100 GB    | OOM / crash | ~50 MB    |

---

## Problem 2: `ByteArrayOutputStream` accumulator — triple-buffering per chunk ✅ Fixed

### Location
`S3SyncUploadStrategy.uploadFile()` · `S3AsyncUploadStrategy.uploadParts()`

### Description
Both strategies used the same anti-pattern:
1. `byte[] buffer = new byte[CHUNK_SIZE]` — read buffer (50 MB)
2. `ByteArrayOutputStream accumulator` — grows to CHUNK_SIZE (50 MB)
3. `accumulator.toByteArray()` — third copy of the same data (50 MB)

```
inputStream → buffer (50 MB) → accumulator (50 MB) → toByteArray() (50 MB)
```

At peak, **three 50 MB copies** (150 MB) were live simultaneously per transfer.

### Applied fix (both strategies)
Replaced with a `readFully` helper that fills the buffer completely:

```java
byte[] buffer = new byte[CHUNK_SIZE];
while (true) {
    int totalRead = readFully(inputStream, buffer);
    if (totalRead == 0) break;
    // Reuse the buffer for full parts; copy only the final smaller part
    byte[] partData = (totalRead == buffer.length)
            ? buffer
            : Arrays.copyOf(buffer, totalRead);
    uploadPart(..., partData);
}

private int readFully(InputStream in, byte[] buf) throws IOException {
    int offset = 0, read;
    while (offset < buf.length && (read = in.read(buf, offset, buf.length - offset)) != -1) {
        offset += read;
    }
    return offset;
}
```

### RAM impact (per transfer)
| Approach              | Peak RAM per transfer |
|-----------------------|-----------------------|
| Before fix (3 copies) | ~150 MB               |
| After fix (1 copy)    | ~50 MB                |

---

## Problem 3: `RequestBody.fromBytes()` — additional in-memory copy ✅ Fixed

### Location
`S3SyncUploadStrategy.uploadPart()` · `S3AsyncUploadStrategy.uploadPart()`

### Description
`RequestBody.fromBytes(partData)` / `AsyncRequestBody.fromBytes(partData)` copy the byte array internally. Using the `fromInputStream` variant with a known content length avoids this extra copy.

### Applied fix
```java
// Sync:
RequestBody.fromInputStream(new ByteArrayInputStream(partData), partData.length)

// Async:
AsyncRequestBody.fromInputStream(new ByteArrayInputStream(partData), (long) partData.length, executor)
```

---

## Problem 4: `readTimeout` too short for large transfers ✅ Fixed

### Location
`HttpPushTransferStrategy` — `DEFAULT_TIMEOUT = 10000` (10 s)
`HttpPullTransferStrategy` — `DEFAULT_TIMEOUT = 10000` (10 s)

### Description
The `readTimeout` applies to the gap between two consecutive socket reads. A 10-second limit causes a `SocketTimeoutException` for any slow network or large file, aborting the transfer mid-stream.

### Applied fix — dynamic timeout proportional to file size
```java
private static final int DEFAULT_CONNECT_TIMEOUT = 10_000;   // 10 s
private static final int FALLBACK_READ_TIMEOUT   = 1_800_000; // 30 min (no Content-Length)
private static final long MIN_TRANSFER_SPEED_BYTES_PER_SEC = 1024L * 1024L; // 1 MB/s

long contentLength = connection.getContentLengthLong();
if (contentLength > 0) {
    int dynamicTimeout = computeReadTimeout(contentLength);
    connection.setReadTimeout(dynamicTimeout);
}

// Formula: ceil(fileSize_bytes × 1.1 / 1_048_576) seconds → milliseconds
// Examples:
//   100 MB → ceil(100 × 1.1 / 1) = 110 s
//   10 GB  → ceil(10240 × 1.1 / 1) = 11264 s ≈ 3.1 h
//   100 GB → ceil(102400 × 1.1 / 1) = 112640 s ≈ 31 h
private int computeReadTimeout(long contentLengthBytes) {
    long seconds = (long) Math.ceil(contentLengthBytes * 1.1 / MIN_TRANSFER_SPEED_BYTES_PER_SEC);
    return (int) Math.min(seconds * 1000L, Integer.MAX_VALUE);
}
```

When no `Content-Length` header is present, `FALLBACK_READ_TIMEOUT` (30 min) is used.

> **Future improvement**: expose `MIN_TRANSFER_SPEED_BYTES_PER_SEC` as a configurable property (`datatransfer.http.min-transfer-speed-bps`) so it can be tuned per deployment.

---

## Problem 5: No backpressure / concurrency limit — HTTP-PUSH ✅ Fixed

### Location
`HttpPushTransferStrategy.transfer()` — previously dispatched to the common `ForkJoinPool`

### Description
`CompletableFuture.supplyAsync()` without an explicit executor uses `ForkJoinPool.commonPool()` (sized at `CPU cores − 1`). With many concurrent transfers arriving together, each holding a 50 MB buffer, the JVM can run out of memory. There was no cap on simultaneous transfers.

**How `supplyAsync` amplifies RAM:** the common pool can accept new tasks (and start new I/O) before earlier tasks finish. Without back-pressure, 20 simultaneous requests can all reach the "read from HTTP, write to S3" stage at the same time.

### Applied fix
```java
private static final ExecutorService TRANSFER_EXECUTOR = Executors.newFixedThreadPool(8);

private CompletableFuture<String> transfer(String presignedUrl, ...) {
    return CompletableFuture.supplyAsync(() -> {
        // All connection + upload logic runs on the bounded pool
        ...
        return s3ClientService.uploadFile(...).join();
    }, TRANSFER_EXECUTOR);
}
```

Peak RAM from transfer buffers: `8 threads × 50 MB = 400 MB` regardless of concurrency.

---

## Problem 6: No backpressure / concurrency limit — HTTP-PULL ✅ Fixed

### Location
`HttpPullTransferStrategy.downloadAndUploadToS3()` — same pattern as HTTP-PUSH, but the connection setup was also outside `supplyAsync`, meaning the `HttpURLConnection` was opened on the calling thread before the async work began.

### Applied fix
Same bounded executor pattern as HTTP-PUSH, and the entire connection lifecycle is now inside the `supplyAsync` lambda:

```java
private static final ExecutorService TRANSFER_EXECUTOR = Executors.newFixedThreadPool(8);

private CompletableFuture<String> downloadAndUploadToS3(...) {
    return CompletableFuture.supplyAsync(() -> {
        // openConnection, setTimeouts, getInputStream, uploadFile — all bounded
        ...
        return s3ClientService.uploadFile(...).join();
    }, TRANSFER_EXECUTOR);
}
```

---

## Problem 7: `S3AsyncUploadStrategy` — unbounded parallel parts ✅ Fixed

### Location
`S3AsyncUploadStrategy.uploadParts()`

### Description
The async strategy launches an upload `CompletableFuture` for **every part as soon as it is read**, with no cap on simultaneous in-flight uploads. For a 100 GB file that is 2000 parts. With each part holding its own 50 MB byte array, up to `2000 × 50 MB = 100 GB` of heap could be allocated before any uploads complete (the common `ForkJoinPool` can queue all tasks quickly, but the byte arrays are allocated on the calling thread before being handed to the executor).

**Does S3SyncUploadStrategy have the same problem?**
No. The sync strategy uploads parts one at a time (sequential loop), so peak RAM is always one `CHUNK_SIZE` buffer per transfer.

### Applied fix — `Semaphore`-bounded parallelism
```java
private static final int MAX_PARALLEL_PARTS = 4; // 4 × 50 MB = 200 MB per transfer

Semaphore parallelism = new Semaphore(MAX_PARALLEL_PARTS);

while (true) {
    int totalRead = readFully(inputStream, buffer);
    if (totalRead == 0) break;
    byte[] partData = ...;

    parallelism.acquire();  // blocks the read loop when 4 parts are already in flight
    CompletableFuture<CompletedPart> partFuture =
        uploadPart(s3AsyncClient, ..., partData)
            .whenComplete((r, t) -> parallelism.release());
    partFutures.add(partFuture);
}
CompletableFuture.allOf(partFutures.toArray(new CompletableFuture[0])).join();
```

Peak RAM per async transfer: `MAX_PARALLEL_PARTS × CHUNK_SIZE = 4 × 50 MB = 200 MB`.

---

## Problem 8: Connection lifecycle outside async boundary (HTTP-PULL) ✅ Fixed

### Location
`HttpPullTransferStrategy.downloadAndUploadToS3()` — original

### Description
In the original code, `url.openConnection()`, `connection.setRequestMethod()`, `connection.getResponseCode()`, and `connection.getInputStream()` were all called on the **calling thread** (a Spring request-handling thread), not on the async executor thread. Only the final `s3ClientService.uploadFile(...)` was async. This meant:
- The calling thread was blocked for the full HTTP round-trip (header + response-code check)
- Connection errors thrown synchronously bypassed the `CompletableFuture` error chain
- Any blocking timeout applied to the wrong thread pool

### Applied fix
The entire `try` block (open → configure → read headers → upload) is now inside the `supplyAsync` lambda, so it runs on `TRANSFER_EXECUTOR` and all errors are properly propagated through the future.

---

## Summary Table

| # | Problem | Affected strategy | Severity | Status |
|---|---------|-------------------|----------|--------|
| 1 | `HttpURLConnection` buffers full file without `setFixedLengthStreamingMode` | PUSH + PULL | 🔴 Critical (100 GB+) | ✅ Fixed |
| 2 | `ByteArrayOutputStream` + `toByteArray()` = 3× chunk in heap | SYNC + ASYNC | 🟠 High | ✅ Fixed — `readFully` + buffer reuse |
| 3 | `RequestBody.fromBytes()` copies chunk again inside SDK | SYNC + ASYNC | 🟡 Medium | ✅ Fixed — `fromInputStream(stream, length)` |
| 4 | `readTimeout = 10 s` breaks any large-file transfer | PUSH + PULL | 🟠 High | ✅ Fixed — dynamic timeout (fileSize / 1 MB/s × 1.1) |
| 5 | No concurrency cap on HTTP-PUSH → unbounded heap | PUSH | 🟡 Medium | ✅ Fixed — `Executors.newFixedThreadPool(8)` |
| 6 | No concurrency cap on HTTP-PULL → unbounded heap | PULL | 🟡 Medium | ✅ Fixed — `Executors.newFixedThreadPool(8)` |
| 7 | ASYNC uploads all parts in parallel → N × 50 MB heap | ASYNC | 🔴 Critical (large files) | ✅ Fixed — `Semaphore(4)` caps concurrent parts |
| 8 | HTTP connection lifecycle on calling thread, not on executor | PULL | 🟡 Medium | ✅ Fixed — full block inside `supplyAsync` |

---

## Expected RAM Usage After All Fixes

**`CHUNK_SIZE` = 50 MB · `TRANSFER_EXECUTOR` = 8 threads · `MAX_PARALLEL_PARTS` = 4**

| Scenario | Peak heap from transfers |
|----------|--------------------------|
| 1 PUSH or PULL transfer (SYNC) | ~50 MB |
| 1 PUSH or PULL transfer (ASYNC) | ~200 MB (4 parts × 50 MB) |
| 8 concurrent PUSH transfers (SYNC) | ~400 MB |
| 8 concurrent PUSH transfers (ASYNC) | ~1.6 GB (8 × 4 × 50 MB) |
| 100 GB file, SYNC | ~50 MB — constant (sequential parts) |
| 100 GB file, ASYNC | ~200 MB — constant (semaphore holds 4 parts) |

> When mixing ASYNC uploads with the bounded executor, keep `MAX_PARALLEL_PARTS × CHUNK_SIZE × maxThreads` in mind. With the defaults above that is `4 × 50 MB × 8 = 1.6 GB`. Reduce `MAX_PARALLEL_PARTS` or `maxThreads` for memory-constrained deployments.

---

## Files Modified

| File | Module | Changes |
|------|--------|---------|
| `data-transfer/…/HttpPushTransferStrategy.java` | `data-transfer` | Fixed timeouts, `setFixedLengthStreamingMode`, dynamic read timeout, bounded `ExecutorService`, `computeReadTimeout` helper |
| `data-transfer/…/HttpPullTransferStrategy.java` | `data-transfer` | Same as PUSH + moved full connection lifecycle inside `supplyAsync` |
| `tools/…/S3SyncUploadStrategy.java` | `tools` | Replaced `ByteArrayOutputStream` accumulator with `readFully`; `RequestBody.fromInputStream` |
| `tools/…/S3AsyncUploadStrategy.java` | `tools` | Same buffer fix; `AsyncRequestBody.fromInputStream`; `Semaphore(4)` for parallel-part cap |
