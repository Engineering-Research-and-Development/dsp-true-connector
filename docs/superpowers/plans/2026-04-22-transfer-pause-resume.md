# Transfer Pause / Resume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement graceful pause/resume for HTTP_PULL and HTTP_PUSH transfers — either party can suspend, only the suspending party can resume, and the download picks up from the exact byte offset where it stopped.

**Architecture:** A `CancellationRegistry` (Spring `@Component`, `ConcurrentHashMap<transferProcessId, AtomicBoolean>`) lets the suspend path signal any running upload/download loop to stop gracefully after its current chunk. On suspension the S3 multipart is aborted and `TransferArtifactState` retains `downloadedBytes` as the resume offset. On resume, strategies add a `Range: bytes=N-` header to the presigned GET and start a fresh multipart upload from part 1. The `suspendedBy` field in `TransferArtifactState` gates which party may call resume.

**Tech Stack:** Java 17, Spring Boot, Spring Data MongoDB, AWS SDK v2, OkHttp, JUnit 5, Mockito, WireMock, Testcontainers (MongoDB + MinIO already wired in `BaseIntegrationTest`)

---

## File Map

### New files
| File | Responsibility |
|---|---|
| `data-transfer/src/main/java/it/eng/datatransfer/service/CancellationRegistry.java` | Thread-safe registry of per-transfer `AtomicBoolean` cancellation tokens |
| `data-transfer/src/main/java/it/eng/datatransfer/exceptions/TransferCancelledException.java` | Thrown when a running transfer is stopped by a suspension signal |
| `data-transfer/src/main/java/it/eng/datatransfer/exceptions/PresignedUrlExpiredException.java` | Thrown when a presigned GET URL returns HTTP 403 |
| `tools/src/main/java/it/eng/tools/s3/service/upload/UploadCheckpointCallback.java` | Two-method callback: `onUploadStarted(uploadId)` + `onPartCompleted(partNumber, etag, bytes)` |
| `data-transfer/src/test/java/it/eng/datatransfer/service/CancellationRegistryTest.java` | Unit tests for CancellationRegistry |
| `data-transfer/src/test/java/it/eng/datatransfer/service/AbstractDataTransferServiceTest.java` | Unit tests for updated suspend/start protocol methods |
| `connector/src/test/java/it/eng/connector/integration/datatransfer/DataTransferSuspendResumeIT.java` | Integration: suspend mid-transfer, verify signal + suspendedBy persistence |
| `connector/src/test/java/it/eng/connector/integration/datatransfer/DataTransferUrlExpiryIT.java` | Integration: presigned URL 403 → terminates with "download URL expired" |

### Modified files
| File | Changes |
|---|---|
| `data-transfer/src/main/java/it/eng/datatransfer/model/TransferArtifactState.java` | Add `suspendedBy` field + builder method |
| `tools/src/main/java/it/eng/tools/event/AuditEventType.java` | Add `TRANSFER_PAUSED`, `TRANSFER_RESUMED`, `TRANSFER_URL_EXPIRED` |
| `tools/src/main/java/it/eng/tools/s3/service/upload/S3UploadStrategy.java` | Add 8-param abstract `uploadFile`; downgrade 6-param to `default` calling 8-param |
| `tools/src/main/java/it/eng/tools/s3/service/upload/S3SyncUploadStrategy.java` | Implement 8-param `uploadFile` with per-part cancellation check, abort, and checkpoint callback |
| `tools/src/main/java/it/eng/tools/s3/service/upload/S3AsyncUploadStrategy.java` | Implement 8-param `uploadFile` with cancellation check, abort on cancel, per-part checkpoint callback |
| `tools/src/main/java/it/eng/tools/s3/service/S3ClientService.java` | Add 6-param `uploadFile` overload (+ `cancellationToken` + `checkpointCallback`); make 4-param a `default` |
| `tools/src/main/java/it/eng/tools/s3/service/S3ClientServiceImpl.java` | Implement new 6-param `uploadFile` routing callbacks to strategy 8-param method |
| `data-transfer/src/main/java/it/eng/datatransfer/service/AbstractDataTransferService.java` | Add `CancellationRegistry` + `TransferArtifactStateRepository` deps; update `suspendDataTransfer()` and `startDataTransfer()` |
| `data-transfer/src/main/java/it/eng/datatransfer/service/DataTransferService.java` | Pass 2 new constructor args to `super()` |
| `data-transfer/src/main/java/it/eng/datatransfer/service/TCKDataTransferService.java` | Pass 2 new constructor args to `super()` |
| `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategy.java` | Inject new deps; Range header; pass `cancellationToken` + `checkpointCallback` to `s3ClientService.uploadFile` |
| `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategy.java` | Same as HttpPull |
| `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java` | Record `suspendedBy` + signal registry in `suspendTransfer()`; handle `TransferCancelledException` + `PresignedUrlExpiredException` in `downloadData()`; validate `suspendedBy` + auto-trigger in `startTransfer()` |
| `tools/src/test/java/it/eng/tools/s3/service/upload/S3SyncUploadStrategyTest.java` | Add tests for cancellation, abort, and checkpoint callback |
| `tools/src/test/java/it/eng/tools/s3/service/upload/S3AsyncUploadStrategyTest.java` | Add cancellation test |
| `data-transfer/src/test/java/it/eng/datatransfer/service/api/DataTransferAPIServiceTest.java` | Add suspend-signal, url-expiry-termination, and resume-auto-trigger tests |

---

## Key Design Contracts

Before reading individual tasks, note these cross-cutting contracts that every task must honour:

1. **CancellationRegistry lifecycle**: `register()` in strategy's `transfer()` → `deregister()` in `downloadData().whenComplete()` on ALL exit paths (success, cancel, expiry, generic error). A dangling token means the next download for the same TP would be immediately cancelled.

2. **suspendedBy semantics**:
   - `suspendTransfer()` API (sender side): `suspendedBy = transferProcess.getRole()` (I am the one sending the suspension).
   - `suspendDataTransfer()` protocol (receiver side): `suspendedBy = opposite of transferProcess.getRole()` (the remote party sent it).
   - Resume validation: `suspendedBy` must equal the role of the party attempting to resume.

3. **Checkpoint contract**: `TransferArtifactState.downloadedBytes` is the absolute byte offset from the start of the source file. After abort, `downloadedBytes` is the resume cursor. On each resume, `rangeStart = downloadedBytes` before starting the new multipart; the callback writes `rangeStart + partCumulativeBytes` as the new `downloadedBytes`.

4. **Range header**: Added only when `rangeStart > 0`. The server returns `206 Partial Content`; code also accepts `200 OK` (server ignoring Range header) without error. Only `403 Forbidden` → `PresignedUrlExpiredException`.

---

## Task 1: Foundation — CancellationRegistry + custom exceptions

**Files:**
- Create: `data-transfer/src/main/java/it/eng/datatransfer/service/CancellationRegistry.java`
- Create: `data-transfer/src/main/java/it/eng/datatransfer/exceptions/TransferCancelledException.java`
- Create: `data-transfer/src/main/java/it/eng/datatransfer/exceptions/PresignedUrlExpiredException.java`
- Test: `data-transfer/src/test/java/it/eng/datatransfer/service/CancellationRegistryTest.java`

- [ ] **Step 1.1: Write the failing CancellationRegistry test**

```java
// data-transfer/src/test/java/it/eng/datatransfer/service/CancellationRegistryTest.java
package it.eng.datatransfer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class CancellationRegistryTest {

    private CancellationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CancellationRegistry();
    }

    @Test
    @DisplayName("register returns a false AtomicBoolean")
    void registerReturnsFalseToken() {
        AtomicBoolean token = registry.register("tp-1");
        assertFalse(token.get());
    }

    @Test
    @DisplayName("signal sets registered token to true")
    void signalSetsTokenTrue() {
        AtomicBoolean token = registry.register("tp-2");
        registry.signal("tp-2");
        assertTrue(token.get());
    }

    @Test
    @DisplayName("signal on unknown id is a no-op")
    void signalUnknownIdIsNoOp() {
        assertDoesNotThrow(() -> registry.signal("unknown-id"));
    }

    @Test
    @DisplayName("deregister removes the token")
    void deregisterRemovesToken() {
        registry.register("tp-3");
        registry.deregister("tp-3");
        assertFalse(registry.isRegistered("tp-3"));
    }

    @Test
    @DisplayName("signal after deregister is a no-op")
    void signalAfterDeregisterIsNoOp() {
        registry.register("tp-4");
        registry.deregister("tp-4");
        assertDoesNotThrow(() -> registry.signal("tp-4"));
    }

    @Test
    @DisplayName("isRegistered returns true only while token is present")
    void isRegisteredLifecycle() {
        assertFalse(registry.isRegistered("tp-5"));
        registry.register("tp-5");
        assertTrue(registry.isRegistered("tp-5"));
        registry.deregister("tp-5");
        assertFalse(registry.isRegistered("tp-5"));
    }
}
```

- [ ] **Step 1.2: Run test to verify it fails**

```bash
mvn -pl data-transfer -am -Dtest=CancellationRegistryTest test
```
Expected: FAIL — `CancellationRegistry` class not found.

- [ ] **Step 1.3: Create CancellationRegistry**

```java
// data-transfer/src/main/java/it/eng/datatransfer/service/CancellationRegistry.java
package it.eng.datatransfer.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registry of per-transfer cancellation tokens.
 *
 * <p>Each active download registers a token before starting. The suspend path calls
 * {@link #signal} to set the token to {@code true}, causing the running upload loop
 * to stop gracefully after its current chunk.
 */
@Component
public class CancellationRegistry {

    private final ConcurrentHashMap<String, AtomicBoolean> tokens = new ConcurrentHashMap<>();

    /**
     * Registers a new cancellation token for the given transfer process ID.
     *
     * @param transferProcessId the internal MongoDB ID of the TransferProcess
     * @return the new token, initially {@code false}
     */
    public AtomicBoolean register(String transferProcessId) {
        AtomicBoolean token = new AtomicBoolean(false);
        tokens.put(transferProcessId, token);
        return token;
    }

    /**
     * Sets the cancellation token for the given transfer process to {@code true}.
     * No-op if no token is currently registered.
     *
     * @param transferProcessId the internal MongoDB ID of the TransferProcess
     */
    public void signal(String transferProcessId) {
        AtomicBoolean token = tokens.get(transferProcessId);
        if (token != null) {
            token.set(true);
        }
    }

    /**
     * Removes the cancellation token for the given transfer process.
     *
     * @param transferProcessId the internal MongoDB ID of the TransferProcess
     */
    public void deregister(String transferProcessId) {
        tokens.remove(transferProcessId);
    }

    /**
     * Returns {@code true} if a token is currently registered for the given ID.
     *
     * @param transferProcessId the internal MongoDB ID of the TransferProcess
     * @return {@code true} if registered
     */
    public boolean isRegistered(String transferProcessId) {
        return tokens.containsKey(transferProcessId);
    }
}
```

- [ ] **Step 1.4: Create TransferCancelledException**

```java
// data-transfer/src/main/java/it/eng/datatransfer/exceptions/TransferCancelledException.java
package it.eng.datatransfer.exceptions;

/**
 * Thrown when a running transfer is stopped by a suspension signal via {@code CancellationRegistry}.
 */
public class TransferCancelledException extends RuntimeException {

    /**
     * Constructs a new exception for the given transfer process.
     *
     * @param transferProcessId the MongoDB ID of the suspended TransferProcess
     */
    public TransferCancelledException(String transferProcessId) {
        super("Transfer " + transferProcessId + " was stopped by a suspension signal.");
    }
}
```

- [ ] **Step 1.5: Create PresignedUrlExpiredException**

```java
// data-transfer/src/main/java/it/eng/datatransfer/exceptions/PresignedUrlExpiredException.java
package it.eng.datatransfer.exceptions;

/**
 * Thrown when a presigned GET URL returns HTTP 403 (Forbidden / Expired).
 */
public class PresignedUrlExpiredException extends RuntimeException {

    /**
     * Constructs a new exception for the given transfer process or URL identifier.
     *
     * @param identifier the MongoDB ID or URL that triggered the expiry
     */
    public PresignedUrlExpiredException(String identifier) {
        super("Presigned URL for transfer " + identifier + " has expired (HTTP 403).");
    }
}
```

- [ ] **Step 1.6: Run tests to verify they pass**

```bash
mvn -pl data-transfer -am -Dtest=CancellationRegistryTest test
```
Expected: 6/6 PASS.

- [ ] **Step 1.7: Checkstyle**

```bash
mvn -pl data-transfer -am validate
```
Expected: BUILD SUCCESS.

- [ ] **Step 1.8: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/service/CancellationRegistry.java \
        data-transfer/src/main/java/it/eng/datatransfer/exceptions/TransferCancelledException.java \
        data-transfer/src/main/java/it/eng/datatransfer/exceptions/PresignedUrlExpiredException.java \
        data-transfer/src/test/java/it/eng/datatransfer/service/CancellationRegistryTest.java
git commit -m "feat(transfer): add CancellationRegistry and custom suspension exceptions

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 2: Model changes — TransferArtifactState.suspendedBy + new AuditEventType values

**Files:**
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/model/TransferArtifactState.java`
- Modify: `tools/src/main/java/it/eng/tools/event/AuditEventType.java`

- [ ] **Step 2.1: Add `suspendedBy` field to TransferArtifactState**

Open `TransferArtifactState.java`. After the last field (e.g. `destObject`), add:

```java
    @Setter
    private String suspendedBy;
```

In the `Builder` inner class, after the `destObject` builder method, add:

```java
        /**
         * Sets which party suspended this transfer; either {@code "CONSUMER"} or {@code "PROVIDER"}.
         *
         * @param suspendedBy the role that suspended the transfer
         * @return this builder
         */
        public Builder suspendedBy(String suspendedBy) {
            transferArtifactState.suspendedBy = suspendedBy;
            return this;
        }
```

Verify that the class already has `@Getter` (Lombok) so `getSuspendedBy()` is auto-generated. Add `@Setter` at the class level if it is not already present, or keep field-level `@Setter` as shown above.

- [ ] **Step 2.2: Add new AuditEventType values**

In `tools/src/main/java/it/eng/tools/event/AuditEventType.java`, locate the last enum constant (e.g. `TRANSFER_FAILED("Transfer failed")`) and after it add:

```java
    TRANSFER_PAUSED("Transfer paused"),
    TRANSFER_RESUMED("Transfer resumed"),
    TRANSFER_URL_EXPIRED("Transfer URL expired"),
```

- [ ] **Step 2.3: Compile**

```bash
mvn -pl data-transfer,tools -am compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 2.4: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/model/TransferArtifactState.java \
        tools/src/main/java/it/eng/tools/event/AuditEventType.java
git commit -m "feat(transfer): add suspendedBy checkpoint field and new audit event types

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 3: UploadCheckpointCallback interface

**Files:**
- Create: `tools/src/main/java/it/eng/tools/s3/service/upload/UploadCheckpointCallback.java`

- [ ] **Step 3.1: Create UploadCheckpointCallback**

```java
// tools/src/main/java/it/eng/tools/s3/service/upload/UploadCheckpointCallback.java
package it.eng.tools.s3.service.upload;

/**
 * Callback invoked by S3 upload strategies to record progress for crash-safe pause/resume checkpointing.
 *
 * <p>{@link #onUploadStarted} is called once the multipart upload has been created on S3.
 * {@link #onPartCompleted} is called after every successfully uploaded part so the caller
 * can persist the current byte offset before continuing.
 */
public interface UploadCheckpointCallback {

    /**
     * Called once the multipart upload has been initiated on S3.
     *
     * @param uploadId the S3 multipart upload ID
     */
    void onUploadStarted(String uploadId);

    /**
     * Called after a part has been successfully uploaded.
     *
     * @param partNumber          the 1-based part number within the current multipart upload
     * @param etag                the ETag returned by S3 for this part
     * @param totalBytesUploaded  cumulative bytes uploaded in this multipart upload so far
     */
    void onPartCompleted(int partNumber, String etag, long totalBytesUploaded);

    /**
     * Returns a no-op callback suitable for callers that do not need checkpointing.
     *
     * @return a no-op {@code UploadCheckpointCallback}
     */
    static UploadCheckpointCallback noOp() {
        return new UploadCheckpointCallback() {
            @Override
            public void onUploadStarted(String uploadId) { /* no-op */ }

            @Override
            public void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) { /* no-op */ }
        };
    }
}
```

- [ ] **Step 3.2: Compile**

```bash
mvn -pl tools -am compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 3.3: Commit**

```bash
git add tools/src/main/java/it/eng/tools/s3/service/upload/UploadCheckpointCallback.java
git commit -m "feat(tools): add UploadCheckpointCallback for S3 multipart progress tracking

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 4: S3UploadStrategy interface + S3SyncUploadStrategy 8-param implementation

**Files:**
- Modify: `tools/src/main/java/it/eng/tools/s3/service/upload/S3UploadStrategy.java`
- Modify: `tools/src/main/java/it/eng/tools/s3/service/upload/S3SyncUploadStrategy.java`
- Test: `tools/src/test/java/it/eng/tools/s3/service/upload/S3SyncUploadStrategyTest.java`

This task makes the 6-param `uploadFile` a `default` method and adds an 8-param abstract method that carries cancellation + checkpoint. The `S3SyncUploadStrategy` is the synchronous (SYNC mode) implementation.

- [ ] **Step 4.1: Add failing tests for S3SyncUploadStrategy**

In `S3SyncUploadStrategyTest.java`, add these tests after the existing ones (keep all existing tests intact):

```java
    // --- Cancellation + checkpoint tests ---

    @Test
    @DisplayName("uploadFile with cancellation token already set throws TransferCancelledException and aborts multipart")
    void uploadFileWithCancellationAborts() {
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());
        when(s3Properties.getChunkSize()).thenReturn(10 * 1024 * 1024);

        AtomicBoolean cancelToken = new AtomicBoolean(true); // pre-cancelled
        InputStream input = new ByteArrayInputStream("some bytes".getBytes());

        CompletableFuture<String> future = syncUploadStrategy.uploadFile(
                input, s3ClientRequest, BUCKET_NAME, OBJECT_KEY,
                CONTENT_TYPE, CONTENT_DISPOSITION, cancelToken, UploadCheckpointCallback.noOp());

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(TransferCancelledException.class, ex.getCause());
        verify(s3Client).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
        verify(s3Client, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("uploadFile invokes checkpoint callback after each part with cumulative byte count")
    void uploadFileInvokesCheckpointCallback() {
        byte[] data = new byte[15 * 1024 * 1024]; // 15 MB -> 2 parts at 10 MB chunk
        when(s3Properties.getChunkSize()).thenReturn(10 * 1024 * 1024);
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag(ETAG).build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag(ETAG).build());

        List<Integer> partNums = new java.util.ArrayList<>();
        List<Long> byteCounts = new java.util.ArrayList<>();
        UploadCheckpointCallback cb = new UploadCheckpointCallback() {
            @Override public void onUploadStarted(String uploadId) {}
            @Override public void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) {
                partNums.add(partNumber);
                byteCounts.add(totalBytesUploaded);
            }
        };

        syncUploadStrategy.uploadFile(new ByteArrayInputStream(data), s3ClientRequest,
                BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION,
                new AtomicBoolean(false), cb).join();

        assertEquals(List.of(1, 2), partNums);
        assertEquals(15 * 1024 * 1024, (long) byteCounts.get(1));
    }
```

Add imports to the test class:
```java
import it.eng.datatransfer.exceptions.TransferCancelledException;
import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import java.util.concurrent.atomic.AtomicBoolean;
```

- [ ] **Step 4.2: Run tests to confirm they fail**

```bash
mvn -pl tools -am -Dtest=S3SyncUploadStrategyTest test
```
Expected: FAIL — new 8-param method not yet defined.

- [ ] **Step 4.3: Update S3UploadStrategy interface**

Replace the entire interface body with:

```java
package it.eng.tools.s3.service.upload;

import it.eng.datatransfer.exceptions.TransferCancelledException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Strategy interface for S3 file upload operations.
 */
public interface S3UploadStrategy {

    /**
     * Uploads a file to S3 with cancellation support and checkpoint callbacks.
     *
     * <p>Implementations MUST check {@code cancellationToken} after each part upload and,
     * if {@code true}, abort the multipart upload and throw {@link TransferCancelledException}.
     *
     * @param inputStream        the data source
     * @param s3ClientRequest    S3 connection config
     * @param bucketName         destination bucket
     * @param objectKey          destination object key
     * @param contentType        MIME type
     * @param contentDisposition content-disposition header value
     * @param cancellationToken  set to {@code true} externally to request graceful stop
     * @param checkpointCallback invoked after each successfully uploaded part
     * @return CompletableFuture resolving to the final ETag
     */
    CompletableFuture<String> uploadFile(InputStream inputStream,
                                        S3ClientRequest s3ClientRequest,
                                        String bucketName,
                                        String objectKey,
                                        String contentType,
                                        String contentDisposition,
                                        AtomicBoolean cancellationToken,
                                        UploadCheckpointCallback checkpointCallback);

    /**
     * Uploads a file to S3 without cancellation or checkpoint support.
     *
     * <p>Delegates to the 8-parameter overload with a no-op token and no-op callback.
     *
     * @param inputStream        the data source
     * @param s3ClientRequest    S3 connection config
     * @param bucketName         destination bucket
     * @param objectKey          destination object key
     * @param contentType        MIME type
     * @param contentDisposition content-disposition header value
     * @return CompletableFuture resolving to the final ETag
     */
    default CompletableFuture<String> uploadFile(InputStream inputStream,
                                                 S3ClientRequest s3ClientRequest,
                                                 String bucketName,
                                                 String objectKey,
                                                 String contentType,
                                                 String contentDisposition) {
        return uploadFile(inputStream, s3ClientRequest, bucketName, objectKey,
                contentType, contentDisposition,
                new AtomicBoolean(false), UploadCheckpointCallback.noOp());
    }
}
```

- [ ] **Step 4.4: Implement 8-param uploadFile in S3SyncUploadStrategy**

Replace the existing `uploadFile` `@Override` with the 8-param version. Keep `uploadPart`, `readFully`, and any other helpers unchanged. Add a new `abortMultipartUpload` helper:

```java
    @Override
    public CompletableFuture<String> uploadFile(InputStream inputStream,
                                               S3ClientRequest s3ClientRequest,
                                               String bucketName,
                                               String objectKey,
                                               String contentType,
                                               String contentDisposition,
                                               AtomicBoolean cancellationToken,
                                               UploadCheckpointCallback checkpointCallback) {
        return CompletableFuture.supplyAsync(() -> {
            S3Client s3Client = s3ClientProvider.s3Client(s3ClientRequest);
            String uploadId = null;
            try {
                log.info("Creating multipart upload (SYNC) for key: {}", objectKey);
                CreateMultipartUploadResponse createResp = s3Client.createMultipartUpload(
                        CreateMultipartUploadRequest.builder()
                                .bucket(bucketName).contentType(contentType)
                                .contentDisposition(contentDisposition).key(objectKey).build());
                uploadId = createResp.uploadId();
                log.info("Created multipart upload (SYNC) uploadId={} key={}", uploadId, objectKey);
                checkpointCallback.onUploadStarted(uploadId);

                List<CompletedPart> completedParts = new ArrayList<>();
                int partNumber = 1;
                long totalBytesUploaded = 0;
                byte[] buffer = new byte[s3Properties.getChunkSize()];

                while (true) {
                    if (cancellationToken.get()) {
                        log.info("Cancellation signalled before part {} for key={}. Aborting.", partNumber, objectKey);
                        abortMultipartUpload(s3Client, bucketName, objectKey, uploadId);
                        throw new TransferCancelledException(objectKey);
                    }
                    int totalRead = readFully(inputStream, buffer);
                    if (totalRead == 0) break;

                    byte[] partData = (totalRead == buffer.length)
                            ? buffer : Arrays.copyOf(buffer, totalRead);
                    CompletedPart part = uploadPart(s3Client, bucketName, objectKey, uploadId, partNumber, partData);
                    completedParts.add(part);
                    totalBytesUploaded += totalRead;
                    checkpointCallback.onPartCompleted(partNumber, part.eTag(), totalBytesUploaded);
                    partNumber++;
                }

                CompleteMultipartUploadResponse completeResp = s3Client.completeMultipartUpload(
                        CompleteMultipartUploadRequest.builder()
                                .bucket(bucketName).key(objectKey).uploadId(uploadId)
                                .multipartUpload(CompletedMultipartUpload.builder()
                                        .parts(completedParts).build())
                                .build());
                String eTag = completeResp.eTag();
                log.info("Upload completed (SYNC) key={} eTag={}", objectKey, eTag);
                return eTag;

            } catch (TransferCancelledException e) {
                throw new CompletionException(e);
            } catch (IOException e) {
                log.error("Upload failed (SYNC) key={}: {}", objectKey, e.getMessage());
                if (uploadId != null) abortMultipartUpload(s3Client, bucketName, objectKey, uploadId);
                throw new CompletionException("Failed to upload file", e);
            } catch (Exception e) {
                log.error("Upload failed (SYNC) key={}: {}", objectKey, e.getMessage());
                if (uploadId != null) abortMultipartUpload(s3Client, bucketName, objectKey, uploadId);
                throw new CompletionException("Failed to upload file", e);
            } finally {
                try { inputStream.close(); } catch (IOException e) {
                    log.error("Failed to close input stream: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * Aborts a multipart upload, logging any error without rethrowing.
     *
     * @param s3Client   the S3 client
     * @param bucketName the bucket name
     * @param objectKey  the object key
     * @param uploadId   the multipart upload ID to abort
     */
    private void abortMultipartUpload(S3Client s3Client, String bucketName, String objectKey, String uploadId) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucketName).key(objectKey).uploadId(uploadId).build());
            log.info("Aborted multipart upload (SYNC) key={} uploadId={}", objectKey, uploadId);
        } catch (Exception e) {
            log.warn("Failed to abort multipart upload key={} uploadId={}: {}", objectKey, uploadId, e.getMessage());
        }
    }
```

Add imports:
```java
import it.eng.datatransfer.exceptions.TransferCancelledException;
import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import java.util.concurrent.atomic.AtomicBoolean;
```

- [ ] **Step 4.5: Run tests**

```bash
mvn -pl tools -am -Dtest=S3SyncUploadStrategyTest test
```
Expected: All PASS (including existing tests).

- [ ] **Step 4.6: Commit**

```bash
git add tools/src/main/java/it/eng/tools/s3/service/upload/S3UploadStrategy.java \
        tools/src/main/java/it/eng/tools/s3/service/upload/S3SyncUploadStrategy.java \
        tools/src/test/java/it/eng/tools/s3/service/upload/S3SyncUploadStrategyTest.java
git commit -m "feat(tools): 8-param uploadFile with cancellation and checkpoint in S3SyncUploadStrategy

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 5: S3AsyncUploadStrategy — cancellation + checkpoint

**Files:**
- Modify: `tools/src/main/java/it/eng/tools/s3/service/upload/S3AsyncUploadStrategy.java`
- Test: `tools/src/test/java/it/eng/tools/s3/service/upload/S3AsyncUploadStrategyTest.java`

- [ ] **Step 5.1: Add failing test for cancellation in S3AsyncUploadStrategy**

Add to `S3AsyncUploadStrategyTest.java` (keep all existing tests):

```java
    @Test
    @DisplayName("uploadFile with cancellation token set aborts multipart and throws TransferCancelledException")
    void uploadFileWithCancellationAbortsMultipart() {
        AtomicBoolean cancelToken = new AtomicBoolean(true);
        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));
        when(s3AsyncClient.abortMultipartUpload(any(AbortMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        AbortMultipartUploadResponse.builder().build()));

        CompletableFuture<String> future = asyncUploadStrategy.uploadFile(
                new ByteArrayInputStream("data".getBytes()), s3ClientRequest,
                BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION,
                cancelToken, UploadCheckpointCallback.noOp());

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(TransferCancelledException.class, ex.getCause());
        verify(s3AsyncClient).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }
```

Add imports:
```java
import it.eng.datatransfer.exceptions.TransferCancelledException;
import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import java.util.concurrent.atomic.AtomicBoolean;
```

- [ ] **Step 5.2: Run test to confirm failure**

```bash
mvn -pl tools -am -Dtest=S3AsyncUploadStrategyTest test
```
Expected: FAIL — 8-param method not yet implemented.

- [ ] **Step 5.3: Implement 8-param uploadFile in S3AsyncUploadStrategy**

Replace the existing `uploadFile` `@Override` with the 8-param version. The `uploadParts` inner method gets a `cancellationToken` and `checkpointCallback` parameter. Key change: check `cancellationToken.get()` before starting each new part in the reading loop.

```java
    @Override
    public CompletableFuture<String> uploadFile(InputStream inputStream,
                                               S3ClientRequest s3ClientRequest,
                                               String bucketName,
                                               String objectKey,
                                               String contentType,
                                               String contentDisposition,
                                               AtomicBoolean cancellationToken,
                                               UploadCheckpointCallback checkpointCallback) {
        S3AsyncClient s3AsyncClient = s3ClientProvider.s3AsyncClient(s3ClientRequest);

        log.info("Creating multipart upload (ASYNC) for key: {}", objectKey);

        return s3AsyncClient.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(bucketName).contentType(contentType)
                        .contentDisposition(contentDisposition).key(objectKey).build())
                .thenComposeAsync(response -> {
                    String uploadId = response.uploadId();
                    log.info("Created multipart upload (ASYNC) uploadId={} key={}", uploadId, objectKey);
                    checkpointCallback.onUploadStarted(uploadId);
                    return uploadParts(inputStream, s3AsyncClient, bucketName, objectKey, uploadId,
                            cancellationToken, checkpointCallback);
                })
                .thenComposeAsync(uploadResult ->
                        completeMultipartUpload(s3AsyncClient, bucketName, objectKey,
                                uploadResult.uploadId(), uploadResult.completedParts()))
                .exceptionally(throwable -> {
                    Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                            ? throwable.getCause() : throwable;
                    if (cause instanceof TransferCancelledException) {
                        log.info("Transfer cancelled (ASYNC) key={}. Aborting multipart.", objectKey);
                        throw new CompletionException(cause);
                    }
                    log.error("Upload failed (ASYNC) key={}: {}", objectKey, throwable.getMessage());
                    throw new CompletionException("Failed to upload file", throwable);
                })
                .whenComplete((result, throwable) -> {
                    try { inputStream.close(); } catch (IOException e) {
                        log.error("Failed to close input stream: {}", e.getMessage());
                    }
                });
    }
```

Replace `uploadParts` signature to add cancellation + checkpoint parameters:

```java
    private CompletableFuture<UploadResult> uploadParts(InputStream inputStream,
                                                        S3AsyncClient s3AsyncClient,
                                                        String bucketName,
                                                        String objectKey,
                                                        String uploadId,
                                                        AtomicBoolean cancellationToken,
                                                        UploadCheckpointCallback checkpointCallback) {
        List<CompletableFuture<CompletedPart>> partFutures = new ArrayList<>();
        AtomicLong totalBytesUploaded = new AtomicLong(0);

        return CompletableFuture.runAsync(() -> {
            try {
                int partNumber = 1;
                byte[] buffer = new byte[s3Properties.getChunkSize()];
                Semaphore parallelism = new Semaphore(MAX_PARALLEL_PARTS);

                while (true) {
                    if (cancellationToken.get()) {
                        log.info("Cancellation signalled before part {} (ASYNC) key={}. Aborting.", partNumber, objectKey);
                        s3AsyncClient.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                                .bucket(bucketName).key(objectKey).uploadId(uploadId).build());
                        throw new CompletionException(new TransferCancelledException(objectKey));
                    }
                    int totalRead = readFully(inputStream, buffer);
                    if (totalRead == 0) break;

                    byte[] partData = Arrays.copyOf(buffer, totalRead);
                    final int currentPartNumber = partNumber;
                    final long partBytes = totalRead;
                    parallelism.acquire();

                    CompletableFuture<CompletedPart> partFuture = uploadPart(
                            s3AsyncClient, bucketName, objectKey, uploadId, currentPartNumber, partData)
                            .whenComplete((completedPart, t) -> {
                                parallelism.release();
                                if (t == null && completedPart != null) {
                                    long cumulative = totalBytesUploaded.addAndGet(partBytes);
                                    checkpointCallback.onPartCompleted(currentPartNumber, completedPart.eTag(), cumulative);
                                }
                            });
                    partFutures.add(partFuture);
                    partNumber++;
                }
            } catch (IOException e) {
                throw new CompletionException("Failed to read input stream", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException("Upload interrupted", e);
            }
        }).thenCompose(v ->
            CompletableFuture.allOf(partFutures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    List<CompletedPart> completedParts = partFutures.stream()
                            .map(CompletableFuture::join).toList();
                    log.info("All {} parts uploaded (ASYNC) key={}", completedParts.size(), objectKey);
                    return new UploadResult(uploadId, completedParts);
                })
        );
    }
```

Add imports:
```java
import it.eng.datatransfer.exceptions.TransferCancelledException;
import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
```

- [ ] **Step 5.4: Run all tools tests**

```bash
mvn -pl tools -am test
```
Expected: All PASS.

- [ ] **Step 5.5: Commit**

```bash
git add tools/src/main/java/it/eng/tools/s3/service/upload/S3AsyncUploadStrategy.java \
        tools/src/test/java/it/eng/tools/s3/service/upload/S3AsyncUploadStrategyTest.java
git commit -m "feat(tools): 8-param uploadFile with cancellation and checkpoint in S3AsyncUploadStrategy

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 6: S3ClientService + S3ClientServiceImpl — cancellation-aware uploadFile

**Files:**
- Modify: `tools/src/main/java/it/eng/tools/s3/service/S3ClientService.java`
- Modify: `tools/src/main/java/it/eng/tools/s3/service/S3ClientServiceImpl.java`

- [ ] **Step 6.1: Add 6-param uploadFile to S3ClientService**

In `S3ClientService.java`:

1. Change the existing `uploadFile(4 params)` declaration to a `default` that calls the new 6-param method:

```java
    /**
     * Uploads a file without cancellation or checkpoint support.
     *
     * <p>Delegates to the 6-parameter overload with a no-op token and callback.
     *
     * @param inputStream             data source
     * @param destinationS3Properties destination bucket properties
     * @param contentType             MIME type
     * @param contentDisposition      content-disposition header value
     * @return CompletableFuture resolving to the final ETag
     */
    default CompletableFuture<String> uploadFile(InputStream inputStream,
                                                  Map<String, String> destinationS3Properties,
                                                  String contentType,
                                                  String contentDisposition) {
        return uploadFile(inputStream, destinationS3Properties, contentType, contentDisposition,
                new java.util.concurrent.atomic.AtomicBoolean(false),
                it.eng.tools.s3.service.upload.UploadCheckpointCallback.noOp());
    }
```

2. Add the new abstract 6-param declaration:

```java
    /**
     * Uploads a file with cancellation support and checkpoint callbacks.
     *
     * @param inputStream             data source
     * @param destinationS3Properties destination bucket properties
     * @param contentType             MIME type
     * @param contentDisposition      content-disposition header value
     * @param cancellationToken       set to {@code true} to request graceful stop
     * @param checkpointCallback      invoked after each successfully uploaded part
     * @return CompletableFuture resolving to the final ETag
     */
    CompletableFuture<String> uploadFile(InputStream inputStream,
                                         Map<String, String> destinationS3Properties,
                                         String contentType,
                                         String contentDisposition,
                                         java.util.concurrent.atomic.AtomicBoolean cancellationToken,
                                         it.eng.tools.s3.service.upload.UploadCheckpointCallback checkpointCallback);
```

- [ ] **Step 6.2: Implement 6-param uploadFile in S3ClientServiceImpl**

Remove the 4-param `@Override` (it's now a default). Add:

```java
    @Override
    public CompletableFuture<String> uploadFile(InputStream inputStream,
                                                Map<String, String> destinationS3Properties,
                                                String contentType,
                                                String contentDisposition,
                                                java.util.concurrent.atomic.AtomicBoolean cancellationToken,
                                                it.eng.tools.s3.service.upload.UploadCheckpointCallback checkpointCallback) {
        BucketCredentialsEntity bucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(destinationS3Properties.get(S3Utils.BUCKET_NAME))
                .accessKey(destinationS3Properties.get(S3Utils.ACCESS_KEY))
                .secretKey(destinationS3Properties.get(S3Utils.SECRET_KEY))
                .build();

        String bucketName = destinationS3Properties.get(S3Utils.BUCKET_NAME);
        String objectKey = destinationS3Properties.get(S3Utils.OBJECT_KEY);
        S3UploadMode uploadMode = getUploadMode();

        log.info("Uploading file {} to bucket {} using {} mode", objectKey, bucketName, uploadMode);

        S3ClientRequest s3ClientRequest = S3ClientRequest.from(
                destinationS3Properties.get(S3Utils.REGION),
                destinationS3Properties.get(S3Utils.ENDPOINT_OVERRIDE),
                bucketCredentials);

        return uploadStrategyFactory.getStrategy(uploadMode)
                .uploadFile(inputStream, s3ClientRequest, bucketName, objectKey,
                        contentType, contentDisposition, cancellationToken, checkpointCallback);
    }
```

- [ ] **Step 6.3: Compile and run all tools tests**

```bash
mvn -pl tools -am compile && mvn -pl tools -am test
```
Expected: All PASS.

- [ ] **Step 6.4: Commit**

```bash
git add tools/src/main/java/it/eng/tools/s3/service/S3ClientService.java \
        tools/src/main/java/it/eng/tools/s3/service/S3ClientServiceImpl.java
git commit -m "feat(tools): cancellation-aware 6-param uploadFile on S3ClientService

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 7: AbstractDataTransferService — new constructor deps + suspend/start updates

**Files:**
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/AbstractDataTransferService.java`
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/DataTransferService.java`
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/TCKDataTransferService.java`
- Test: `data-transfer/src/test/java/it/eng/datatransfer/service/AbstractDataTransferServiceTest.java`

- [ ] **Step 7.1: Write failing tests for AbstractDataTransferService**

```java
// data-transfer/src/test/java/it/eng/datatransfer/service/AbstractDataTransferServiceTest.java
package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.FieldEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractDataTransferServiceTest {

    private static final String CONSUMER_PID = "urn:uuid:consumer-abs-test";
    private static final String PROVIDER_PID = "urn:uuid:provider-abs-test";
    private static final String TP_ID = "tp-id-abs";

    @Mock private TransferProcessRepository transferProcessRepository;
    @Mock private AuditEventPublisher publisher;
    @Mock private OkHttpRestClient okHttpRestClient;
    @Mock private TransferRequestMessageRepository transferRequestMessageRepository;
    @Mock private DataTransferProperties dataTransferProperties;
    @Mock private TemporaryBucketUserService temporaryBucketUserService;
    @Mock private FieldEncryptionService fieldEncryptionService;
    @Mock private CancellationRegistry cancellationRegistry;
    @Mock private TransferArtifactStateRepository transferArtifactStateRepository;

    private AbstractDataTransferService service;

    @BeforeEach
    void setUp() {
        service = new DataTransferService(
                transferProcessRepository, transferRequestMessageRepository,
                publisher, okHttpRestClient, dataTransferProperties,
                temporaryBucketUserService, fieldEncryptionService,
                cancellationRegistry, transferArtifactStateRepository);
    }

    @Test
    @DisplayName("suspendDataTransfer records suspendedBy as the opposite of local role (the sender)")
    void suspendDataTransferRecordsSuspendedByAsSenderRole() {
        // Local role = CONSUMER → sender = PROVIDER
        TransferProcess tp = consumerPullTp(TransferState.STARTED);
        when(transferProcessRepository.findByConsumerPidAndProviderPid(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(Optional.of(tp));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transferArtifactStateRepository.findById(TP_ID)).thenReturn(Optional.empty());
        when(transferArtifactStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransferSuspensionMessage msg = TransferSuspensionMessage.Builder.newInstance()
                .consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID)
                .code("200").reason(List.of("test")).build();
        service.suspendDataTransfer(msg, CONSUMER_PID, PROVIDER_PID);

        verify(transferArtifactStateRepository).save(
                argThat(s -> IConstants.ROLE_PROVIDER.equals(s.getSuspendedBy())));
        verify(cancellationRegistry).signal(TP_ID);
    }

    @Test
    @DisplayName("startDataTransfer from SUSPENDED rejects resume when suspendedBy does not match sender")
    void startDataTransferRejectsMismatchedSuspendedBy() {
        // Local role = CONSUMER, suspendedBy = CONSUMER (consumer suspended it)
        // Sender = PROVIDER tries to resume → should be rejected
        TransferProcess suspended = consumerPullTp(TransferState.SUSPENDED);
        when(transferProcessRepository.findByConsumerPidAndProviderPid(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(Optional.of(suspended));
        TransferArtifactState state = TransferArtifactState.Builder.newInstance()
                .id(TP_ID).suspendedBy(IConstants.ROLE_CONSUMER).build();
        when(transferArtifactStateRepository.findById(TP_ID)).thenReturn(Optional.of(state));

        TransferStartMessage msg = TransferStartMessage.Builder.newInstance()
                .consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID).build();

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.startDataTransfer(msg, CONSUMER_PID, PROVIDER_PID));
    }

    @Test
    @DisplayName("startDataTransfer from SUSPENDED succeeds when suspendedBy matches sender")
    void startDataTransferSucceedsWhenSuspendedByMatchesSender() {
        // Local role = CONSUMER, suspendedBy = PROVIDER, sender = PROVIDER → allowed
        TransferProcess suspended = consumerPullTp(TransferState.SUSPENDED);
        when(transferProcessRepository.findByConsumerPidAndProviderPid(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(Optional.of(suspended));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TransferArtifactState state = TransferArtifactState.Builder.newInstance()
                .id(TP_ID).suspendedBy(IConstants.ROLE_PROVIDER).build();
        when(transferArtifactStateRepository.findById(TP_ID)).thenReturn(Optional.of(state));
        when(transferArtifactStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransferStartMessage msg = TransferStartMessage.Builder.newInstance()
                .consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID).build();

        assertDoesNotThrow(() -> service.startDataTransfer(msg, CONSUMER_PID, PROVIDER_PID));
    }

    @Test
    @DisplayName("startDataTransfer for CONSUMER+HTTP_PULL publishes AutoTransferDownloadEvent without automaticTransfer guard")
    void startDataTransferAlwaysPublishesAutoDownloadForConsumerPull() {
        TransferProcess requested = TransferProcess.Builder.newInstance()
                .id(TP_ID).consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID)
                .state(TransferState.REQUESTED).role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format()).build();
        when(transferProcessRepository.findByConsumerPidAndProviderPid(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(Optional.of(requested));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransferStartMessage msg = TransferStartMessage.Builder.newInstance()
                .consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID).build();

        service.startDataTransfer(msg, CONSUMER_PID, PROVIDER_PID);

        verify(publisher).publishEvent(any(it.eng.datatransfer.event.AutoTransferDownloadEvent.class));
    }

    // Helper
    private TransferProcess consumerPullTp(TransferState state) {
        return TransferProcess.Builder.newInstance()
                .id(TP_ID).consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID)
                .state(state).role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format()).build();
    }
}
```

- [ ] **Step 7.2: Run tests to confirm failure**

```bash
mvn -pl data-transfer -am -Dtest=AbstractDataTransferServiceTest test
```
Expected: FAIL — constructor signature mismatch.

- [ ] **Step 7.3: Add new fields and extend constructor in AbstractDataTransferService**

Add two fields:
```java
    private final CancellationRegistry cancellationRegistry;
    private final TransferArtifactStateRepository transferArtifactStateRepository;
```

Extend the `protected` constructor with two new parameters at the end:
```java
    protected AbstractDataTransferService(...existing params...,
                                          CancellationRegistry cancellationRegistry,
                                          TransferArtifactStateRepository transferArtifactStateRepository) {
        // ... existing assignments ...
        this.cancellationRegistry = cancellationRegistry;
        this.transferArtifactStateRepository = transferArtifactStateRepository;
    }
```

Add imports:
```java
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.model.TransferArtifactState;
```

- [ ] **Step 7.4: Update suspendDataTransfer()**

After `stateTransitionCheck(transferProcess, TransferState.SUSPENDED);`, add:

```java
        // Record which party initiated the suspension (the sender = opposite of local role)
        String senderRole = IConstants.ROLE_CONSUMER.equals(transferProcess.getRole())
                ? IConstants.ROLE_PROVIDER : IConstants.ROLE_CONSUMER;
        TransferArtifactState artifactState = transferArtifactStateRepository.findById(transferProcess.getId())
                .orElseGet(() -> TransferArtifactState.Builder.newInstance()
                        .id(transferProcess.getId()).build());
        artifactState.setSuspendedBy(senderRole);
        transferArtifactStateRepository.save(artifactState);
        // Signal any running upload/download on this JVM to stop gracefully after current chunk
        cancellationRegistry.signal(transferProcess.getId());
```

- [ ] **Step 7.5: Update startDataTransfer()**

**a) Add suspendedBy validation for SUSPENDED → STARTED transitions** — after the `findTransferProcess` call and before `stateTransitionCheck`:

```java
        if (TransferState.SUSPENDED.equals(transferProcessRequested.getState())) {
            String senderRole = IConstants.ROLE_CONSUMER.equals(transferProcessRequested.getRole())
                    ? IConstants.ROLE_PROVIDER : IConstants.ROLE_CONSUMER;
            TransferArtifactState artifactState = transferArtifactStateRepository
                    .findById(transferProcessRequested.getId()).orElse(null);
            if (artifactState != null && artifactState.getSuspendedBy() != null
                    && !artifactState.getSuspendedBy().equals(senderRole)) {
                String errorMsg = "Resume rejected: suspended by " + artifactState.getSuspendedBy()
                        + " but start sent by " + senderRole;
                publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR,
                        "Transfer process resume rejected",
                        Map.of("role", IConstants.ROLE_PROTOCOL,
                                "consumerPid", transferProcessRequested.getConsumerPid(),
                                "providerPid", transferProcessRequested.getProviderPid(),
                                "suspendedBy", artifactState.getSuspendedBy(),
                                "senderRole", senderRole));
                throw new TransferProcessInvalidStateException(errorMsg,
                        transferProcessRequested.getConsumerPid(), transferProcessRequested.getProviderPid());
            }
            // Clear suspendedBy now that the authorised party is resuming
            if (artifactState != null) {
                artifactState.setSuspendedBy(null);
                transferArtifactStateRepository.save(artifactState);
            }
        }
```

**b) Replace the auto-download trigger block** at the bottom of `startDataTransfer()`. Remove the old `if (role == CONSUMER && automaticTransfer && format == HTTP_PULL)` block and replace with:

```java
        // HTTP_PULL on CONSUMER: always auto-trigger download (initial start + Case B resume)
        if (transferProcessStarted.getRole().equals(IConstants.ROLE_CONSUMER)
                && DataTransferFormat.HTTP_PULL.format().equals(transferProcessStarted.getFormat())) {
            publisher.publishEvent(new AutoTransferDownloadEvent(transferProcessStarted.getId()));
        }
        // HTTP_PUSH on PROVIDER receiving a start message in SUSPENDED state (Case B resume):
        // trigger the upload to consumer's S3
        if (transferProcessStarted.getRole().equals(IConstants.ROLE_PROVIDER)
                && DataTransferFormat.HTTP_PUSH.format().equals(transferProcessStarted.getFormat())
                && TransferState.SUSPENDED.equals(transferProcessRequested.getState())) {
            publisher.publishEvent(new AutoTransferDownloadEvent(transferProcessStarted.getId()));
        }
```

- [ ] **Step 7.6: Update DataTransferService constructor**

```java
    public DataTransferService(TransferProcessRepository transferProcessRepository,
                               TransferRequestMessageRepository transferRequestMessageRepository,
                               AuditEventPublisher publisher,
                               OkHttpRestClient okHttpRestClient,
                               DataTransferProperties transferProperties,
                               TemporaryBucketUserService temporaryBucketUserService,
                               FieldEncryptionService fieldEncryptionService,
                               CancellationRegistry cancellationRegistry,
                               TransferArtifactStateRepository transferArtifactStateRepository) {
        super(transferProcessRepository, publisher, okHttpRestClient, transferRequestMessageRepository,
                transferProperties, temporaryBucketUserService, fieldEncryptionService,
                cancellationRegistry, transferArtifactStateRepository);
    }
```

Add imports:
```java
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
```

- [ ] **Step 7.7: Update TCKDataTransferService constructor**

Add `CancellationRegistry cancellationRegistry` and `TransferArtifactStateRepository transferArtifactStateRepository` to the constructor signature and pass to `super()`. Keep all existing field assignments.

Add imports:
```java
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
```

- [ ] **Step 7.8: Run AbstractDataTransferService tests**

```bash
mvn -pl data-transfer -am -Dtest=AbstractDataTransferServiceTest test
```
Expected: 4/4 PASS.

- [ ] **Step 7.9: Run all data-transfer unit tests**

```bash
mvn -pl data-transfer -am test
```
Expected: All PASS.

- [ ] **Step 7.10: Checkstyle**

```bash
mvn -pl data-transfer -am validate
```
Expected: BUILD SUCCESS.

- [ ] **Step 7.11: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/service/AbstractDataTransferService.java \
        data-transfer/src/main/java/it/eng/datatransfer/service/DataTransferService.java \
        data-transfer/src/main/java/it/eng/datatransfer/service/TCKDataTransferService.java \
        data-transfer/src/test/java/it/eng/datatransfer/service/AbstractDataTransferServiceTest.java
git commit -m "feat(transfer): suspendedBy validation + auto-trigger in AbstractDataTransferService

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 8: HttpPullTransferStrategy — Range header + cancellation

**Files:**
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategy.java`

- [ ] **Step 8.1: Add new fields and constructor parameters**

Add:
```java
    private final TransferArtifactStateRepository transferArtifactStateRepository;
    private final CancellationRegistry cancellationRegistry;
```

Extend the `@Autowired` constructor with `TransferArtifactStateRepository transferArtifactStateRepository` and `CancellationRegistry cancellationRegistry` at the end. Assign them in the constructor body.

Add imports:
```java
import it.eng.datatransfer.exceptions.PresignedUrlExpiredException;
import it.eng.datatransfer.exceptions.TransferCancelledException;
import it.eng.datatransfer.model.TransferArtifactState;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.service.CancellationRegistry;
import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import java.util.concurrent.atomic.AtomicBoolean;
```

- [ ] **Step 8.2: Replace transfer() to load checkpoint + wire cancellation**

```java
    @Override
    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PULL transfer for process {}", transferProcess.getId());

        String authorization = extractAuthorization(transferProcess);

        // Load existing checkpoint (0 bytes = first-time download)
        TransferArtifactState checkpoint = transferArtifactStateRepository
                .findById(transferProcess.getId())
                .orElseGet(() -> TransferArtifactState.Builder.newInstance()
                        .id(transferProcess.getId()).downloadedBytes(0).build());

        long rangeStart = checkpoint.getDownloadedBytes();
        if (rangeStart > 0) {
            log.info("Resuming HTTP PULL for process {} from byte offset {}", transferProcess.getId(), rangeStart);
            // Reset multipart tracking for the fresh upload that starts from scratch
            checkpoint.setUploadId(null);
        }
        transferArtifactStateRepository.save(checkpoint);

        // Register cancellation token before starting — deregistered in DataTransferAPIService.downloadData().whenComplete()
        AtomicBoolean cancellationToken = cancellationRegistry.register(transferProcess.getId());

        String transferProcessId = transferProcess.getId();
        UploadCheckpointCallback checkpointCallback = new UploadCheckpointCallback() {
            @Override
            public void onUploadStarted(String uploadId) {
                TransferArtifactState state = transferArtifactStateRepository.findById(transferProcessId)
                        .orElseGet(() -> TransferArtifactState.Builder.newInstance().id(transferProcessId).build());
                state.setUploadId(uploadId);
                transferArtifactStateRepository.save(state);
            }
            @Override
            public void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) {
                // totalBytesUploaded is relative to this multipart; add rangeStart for absolute offset
                TransferArtifactState state = transferArtifactStateRepository.findById(transferProcessId)
                        .orElseGet(() -> TransferArtifactState.Builder.newInstance().id(transferProcessId).build());
                state.setDownloadedBytes(rangeStart + totalBytesUploaded);
                transferArtifactStateRepository.save(state);
            }
        };

        return downloadAndUploadToS3(
                transferProcess.getDataAddress().getEndpoint(),
                authorization,
                transferProcessId,
                rangeStart,
                cancellationToken,
                checkpointCallback
        ).thenAccept(key -> log.info("Stored transfer process id - {} data!", key));
    }
```

- [ ] **Step 8.3: Update downloadAndUploadToS3() to accept new parameters and add Range header**

Change the signature to:
```java
    private CompletableFuture<String> downloadAndUploadToS3(String presignedUrl,
                                                            String authorization,
                                                            String key,
                                                            long rangeStart,
                                                            AtomicBoolean cancellationToken,
                                                            UploadCheckpointCallback checkpointCallback)
```

In the HTTP connection setup section, after setting the authorization header, add:
```java
                // Add Range header for resume scenarios
                if (rangeStart > 0) {
                    connection.setRequestProperty("Range", "bytes=" + rangeStart + "-");
                    log.info("Added Range header bytes={}- for key: {}", rangeStart, key);
                }
```

After the response code check, add `206` as a valid response code alongside `200`:
```java
                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    connection.disconnect();
                    throw new PresignedUrlExpiredException(key);
                }
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != 206) {
                    connection.disconnect();
                    throw new DataTransferAPIException("Failed to get stream. HTTP response code: " + responseCode);
                }
```

In the `s3ClientService.uploadFile(...)` call, change from 4-param to 6-param:
```java
                return s3ClientService.uploadFile(connection.getInputStream(), destinationS3Properties,
                        contentType, contentDisposition, cancellationToken, checkpointCallback);
```

In the catch block, add `PresignedUrlExpiredException` and `TransferCancelledException` to rethrow without wrapping:
```java
            } catch (PresignedUrlExpiredException | TransferCancelledException e) {
                HttpURLConnection c = connectionRef.get();
                if (c != null) c.disconnect();
                throw e;
            }
```

- [ ] **Step 8.4: Compile and run data-transfer tests**

```bash
mvn -pl data-transfer -am compile && mvn -pl data-transfer -am test
```
Expected: BUILD SUCCESS, all PASS.

- [ ] **Step 8.5: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategy.java
git commit -m "feat(transfer): Range header + cancellation + checkpoint in HttpPullTransferStrategy

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 9: HttpPushTransferStrategy — Range header + cancellation

**Files:**
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategy.java`

- [ ] **Step 9.1: Add new fields and constructor parameters**

Same additions as HttpPull in Task 8. Add:
```java
    private final TransferArtifactStateRepository transferArtifactStateRepository;
    private final CancellationRegistry cancellationRegistry;
```

Extend the `@Autowired` constructor to accept and assign them.

Add the same imports as in Task 8.

- [ ] **Step 9.2: Replace the public transfer() method**

```java
    @Override
    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        Map<String, String> destinationS3Properties = buildDestinationProperties(transferProcess);

        TransferArtifactState checkpoint = transferArtifactStateRepository
                .findById(transferProcess.getId())
                .orElseGet(() -> TransferArtifactState.Builder.newInstance()
                        .id(transferProcess.getId()).downloadedBytes(0).build());

        long rangeStart = checkpoint.getDownloadedBytes();
        if (rangeStart > 0) {
            log.info("Resuming HTTP PUSH for process {} from byte offset {}", transferProcess.getId(), rangeStart);
            checkpoint.setUploadId(null);
        }
        transferArtifactStateRepository.save(checkpoint);

        AtomicBoolean cancellationToken = cancellationRegistry.register(transferProcess.getId());

        String transferProcessId = transferProcess.getId();
        UploadCheckpointCallback checkpointCallback = new UploadCheckpointCallback() {
            @Override
            public void onUploadStarted(String uploadId) {
                TransferArtifactState state = transferArtifactStateRepository.findById(transferProcessId)
                        .orElseGet(() -> TransferArtifactState.Builder.newInstance().id(transferProcessId).build());
                state.setUploadId(uploadId);
                transferArtifactStateRepository.save(state);
            }
            @Override
            public void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) {
                TransferArtifactState state = transferArtifactStateRepository.findById(transferProcessId)
                        .orElseGet(() -> TransferArtifactState.Builder.newInstance().id(transferProcessId).build());
                state.setDownloadedBytes(rangeStart + totalBytesUploaded);
                transferArtifactStateRepository.save(state);
            }
        };

        // Always generate a fresh presigned URL for PUSH (provider controls the source)
        String presignedUrl = s3ClientService.generateGetPresignedUrl(
                s3Properties.getBucketName(), transferProcess.getDatasetId(), java.time.Duration.ofDays(1L));

        return transfer(presignedUrl, destinationS3Properties, rangeStart, cancellationToken, checkpointCallback)
                .thenAccept(key -> log.info("Pushed transfer process id - {} data!", key));
    }
```

- [ ] **Step 9.3: Update private transfer() to accept rangeStart + callbacks**

Change the private `transfer(String presignedUrl, Map<String, String> destinationS3Properties)` signature to:

```java
    private CompletableFuture<String> transfer(String presignedUrl,
                                               Map<String, String> destinationS3Properties,
                                               long rangeStart,
                                               AtomicBoolean cancellationToken,
                                               UploadCheckpointCallback checkpointCallback)
```

In the connection setup, after the HTTPS check, add:
```java
                if (rangeStart > 0) {
                    connection.setRequestProperty("Range", "bytes=" + rangeStart + "-");
                    log.info("Added Range header bytes={}- for push presignedUrl: {}", rangeStart, presignedUrl);
                }
```

Change the `responseCode` check similarly to Task 8 (403 → `PresignedUrlExpiredException`, accept 206).

Change `s3ClientService.uploadFile(...)` to 6-param.

Add the same rethrow catch block for `PresignedUrlExpiredException | TransferCancelledException`.

- [ ] **Step 9.4: Compile and run data-transfer tests**

```bash
mvn -pl data-transfer -am compile && mvn -pl data-transfer -am test
```
Expected: BUILD SUCCESS, all PASS.

- [ ] **Step 9.5: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategy.java
git commit -m "feat(transfer): Range header + cancellation + checkpoint in HttpPushTransferStrategy

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 10: DataTransferAPIService — suspend signal + exception handling + resume auto-trigger

**Files:**
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java`

This is the longest single-file change. Read each sub-step carefully and compile after each logical group.

- [ ] **Step 10.1: Add constructor dependencies**

Add fields:
```java
    private final CancellationRegistry cancellationRegistry;
    private final TransferArtifactStateRepository transferArtifactStateRepository;
```

Extend the constructor with `CancellationRegistry cancellationRegistry` and `TransferArtifactStateRepository transferArtifactStateRepository` at the end. Assign them.

Add imports:
```java
import it.eng.datatransfer.exceptions.PresignedUrlExpiredException;
import it.eng.datatransfer.exceptions.TransferCancelledException;
import it.eng.datatransfer.model.TransferArtifactState;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.service.CancellationRegistry;
```

- [ ] **Step 10.2: Update suspendTransfer() — record suspendedBy + signal registry**

In `suspendTransfer()`, immediately after `stateTransitionCheck(TransferState.SUSPENDED, transferProcess);`, add:

```java
        // Signal any running upload/download on this JVM (sender side also may have download, e.g. PULL consumer)
        cancellationRegistry.signal(transferProcess.getId());
        // Record that this connector's role initiated the suspension
        TransferArtifactState artifactState = transferArtifactStateRepository.findById(transferProcess.getId())
                .orElseGet(() -> TransferArtifactState.Builder.newInstance()
                        .id(transferProcess.getId()).build());
        artifactState.setSuspendedBy(transferProcess.getRole());
        transferArtifactStateRepository.save(artifactState);
```

In the success branch (after receiving 200 from peer and saving the suspended state), add the `TRANSFER_PAUSED` audit event. Find the existing audit event call near the end of the success block and add after it:
```java
            publisher.publishEvent(AuditEventType.TRANSFER_PAUSED,
                    "Transfer paused by " + transferProcess.getRole() + " for process " + transferProcess.getId(),
                    Map.of("transferProcessId", transferProcess.getId(),
                            "suspendedBy", transferProcess.getRole(),
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid()));
```

- [ ] **Step 10.3: Update startTransfer() — suspendedBy validation + resume event + Case A auto-trigger**

In `startTransfer()`, after `stateTransitionCheck(TransferState.STARTED, transferProcess);`, add:

```java
        // If resuming from SUSPENDED, only the suspending party may resume
        if (TransferState.SUSPENDED.equals(transferProcess.getState())) {
            TransferArtifactState artifactState = transferArtifactStateRepository
                    .findById(transferProcess.getId()).orElse(null);
            if (artifactState != null && artifactState.getSuspendedBy() != null
                    && !artifactState.getSuspendedBy().equals(transferProcess.getRole())) {
                throw new DataTransferAPIException(
                        "Resume rejected: suspended by " + artifactState.getSuspendedBy()
                        + " but this connector's role is " + transferProcess.getRole());
            }
        }
```

In the success branch, after saving `transferProcessStarted`, add Case A auto-trigger and the `TRANSFER_RESUMED` audit event:

```java
            // Case A resume: active party sent the start — auto-trigger download on this JVM
            if (TransferState.SUSPENDED.equals(transferProcess.getState())) {
                boolean isPullConsumer = IConstants.ROLE_CONSUMER.equals(transferProcessStarted.getRole())
                        && DataTransferFormat.HTTP_PULL.format().equals(transferProcessStarted.getFormat());
                boolean isPushProvider = IConstants.ROLE_PROVIDER.equals(transferProcessStarted.getRole())
                        && DataTransferFormat.HTTP_PUSH.format().equals(transferProcessStarted.getFormat());
                if (isPullConsumer || isPushProvider) {
                    log.info("Case A resume for process {}. Auto-triggering download.", transferProcessStarted.getId());
                    final String tpIdForResume = transferProcessStarted.getId();
                    CompletableFuture.runAsync(() -> {
                        try {
                            downloadData(tpIdForResume).join();
                        } catch (Exception e) {
                            log.error("Auto-triggered download failed after Case A resume for process {}: {}",
                                    tpIdForResume, e.getMessage());
                        }
                    });
                }
                publisher.publishEvent(AuditEventType.TRANSFER_RESUMED,
                        "Transfer resumed by " + transferProcessStarted.getRole()
                                + " for process " + transferProcessStarted.getId(),
                        Map.of("transferProcessId", transferProcessStarted.getId(),
                                "role", transferProcessStarted.getRole(),
                                "consumerPid", transferProcessStarted.getConsumerPid(),
                                "providerPid", transferProcessStarted.getProviderPid()));
            }
```

- [ ] **Step 10.4: Update downloadData().whenComplete() — handle new exception types**

In the `whenComplete` handler inside `downloadData()`, replace the generic `else { // error }` block with:

```java
                    } else {
                        // Unwrap CompletionException to find root cause
                        Throwable cause = throwable;
                        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                            cause = cause.getCause();
                        }

                        // Always deregister — the strategy registered the token before calling us
                        cancellationRegistry.deregister(transferProcessId);

                        if (cause instanceof TransferCancelledException) {
                            log.info("Transfer {} stopped gracefully by suspension signal. Checkpoint retained.", transferProcessId);
                            transferProcessRepository.save(transferProcessDownloading.withIsDownloadInProgress(false));
                            publisher.publishEvent(AuditEventType.TRANSFER_PAUSED,
                                    "Transfer paused (download stopped) for process " + transferProcessId,
                                    Map.of("transferProcessId", transferProcessId,
                                            "consumerPid", transferProcessDownloading.getConsumerPid(),
                                            "providerPid", transferProcessDownloading.getProviderPid()));
                        } else if (cause instanceof PresignedUrlExpiredException) {
                            log.warn("Presigned URL expired for process {}. Terminating transfer.", transferProcessId);
                            transferProcessRepository.save(transferProcessDownloading.withIsDownloadInProgress(false));
                            publisher.publishEvent(AuditEventType.TRANSFER_URL_EXPIRED,
                                    "Presigned URL expired for process " + transferProcessId,
                                    Map.of("transferProcessId", transferProcessId,
                                            "consumerPid", transferProcessDownloading.getConsumerPid(),
                                            "providerPid", transferProcessDownloading.getProviderPid()));
                            try {
                                terminateTransferWithReason(transferProcessId, "409", "download URL expired");
                            } catch (Exception te) {
                                log.error("Failed to send termination after URL expiry for process {}: {}",
                                        transferProcessId, te.getMessage());
                            }
                        } else {
                            log.error("Transfer process {} data transmission interrupted: {}",
                                    transferProcessId, throwable.getMessage());
                            transferProcessRepository.save(transferProcessDownloading.withIsDownloadInProgress(false));
                            publisher.publishEvent(AuditEventType.TRANSFER_FAILED,
                                    "Data transfer failed for process " + transferProcessDownloading.getId(),
                                    Map.of("role", IConstants.ROLE_PROTOCOL,
                                            "transferProcess", transferProcessDownloading,
                                            "consumerPid", transferProcessDownloading.getConsumerPid(),
                                            "providerPid", transferProcessDownloading.getProviderPid(),
                                            "errorMessage", throwable.getMessage()));
                        }
                    }
```

Also add `cancellationRegistry.deregister(transferProcessId)` at the start of the success branch:
```java
                    if (throwable == null) {
                        cancellationRegistry.deregister(transferProcessId);
                        // ... rest of existing success handling ...
```

- [ ] **Step 10.5: Add terminateTransferWithReason() private helper**

```java
    /**
     * Sends a {@link it.eng.datatransfer.model.TransferTerminationMessage} to the peer
     * and transitions the local TransferProcess to {@link TransferState#TERMINATED}.
     *
     * @param transferProcessId the internal MongoDB ID of the TransferProcess
     * @param code              the termination code (e.g. {@code "409"})
     * @param reason            human-readable reason string
     */
    private void terminateTransferWithReason(String transferProcessId, String code, String reason) {
        TransferProcess transferProcess = transferProcessRepository.findById(transferProcessId)
                .orElseThrow(() -> new DataTransferAPIException(
                        "TransferProcess not found: " + transferProcessId));

        TransferTerminationMessage msg = TransferTerminationMessage.Builder.newInstance()
                .consumerPid(transferProcess.getConsumerPid())
                .providerPid(transferProcess.getProviderPid())
                .code(code)
                .reason(List.of(reason))
                .build();

        String address;
        if (IConstants.ROLE_CONSUMER.equals(transferProcess.getRole())) {
            address = DataTransferCallback.getProviderDataTransferTermination(
                    transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
        } else {
            address = DataTransferCallback.getConsumerDataTransferTermination(
                    transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
        }

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
                address,
                TransferSerializer.serializeProtocolJsonNode(msg),
                credentialUtils.getConnectorCredentials());

        if (response.isSuccess()) {
            transferProcessRepository.save(transferProcess.copyWithNewTransferState(TransferState.TERMINATED));
            log.info("Transfer process {} terminated after URL expiry.", transferProcessId);
        } else {
            log.error("Failed to send termination for process {}: {}", transferProcessId, response.getMessage());
        }
    }
```

- [ ] **Step 10.6: Compile**

```bash
mvn -pl data-transfer -am compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 10.7: Run all data-transfer unit tests**

```bash
mvn -pl data-transfer -am test
```
Expected: All PASS.

- [ ] **Step 10.8: Checkstyle**

```bash
mvn -pl data-transfer -am validate
```
Expected: BUILD SUCCESS.

- [ ] **Step 10.9: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java
git commit -m "feat(transfer): suspend signal, url-expiry termination, and Case A resume auto-trigger in DataTransferAPIService

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 11: Unit tests — DataTransferAPIService suspend/resume/expiry paths

**Files:**
- Modify: `data-transfer/src/test/java/it/eng/datatransfer/service/api/DataTransferAPIServiceTest.java`

- [ ] **Step 11.1: Add new mocks to test class**

Add at the field level (alongside existing `@Mock` fields):
```java
    @Mock
    private CancellationRegistry cancellationRegistry;
    @Mock
    private TransferArtifactStateRepository transferArtifactStateRepository;
```

Mockito `@InjectMocks` will inject them automatically through the constructor since `DataTransferAPIService` uses constructor injection.

- [ ] **Step 11.2: Add test for suspendTransfer() signalling**

```java
    @Test
    @DisplayName("suspendTransfer signals CancellationRegistry and records suspendedBy after successful peer response")
    void suspendTransferSignalsCancellationRegistryAndRecordsSuspendedBy() {
        TransferProcess tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(tp.getId())).thenReturn(Optional.of(tp));
        when(apiResponse.isSuccess()).thenReturn(true);
        when(okHttpRestClient.sendRequestProtocol(anyString(), any(), any())).thenReturn(apiResponse);
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transferArtifactStateRepository.findById(tp.getId())).thenReturn(Optional.empty());
        when(transferArtifactStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.suspendTransfer(tp.getId());

        verify(cancellationRegistry).signal(tp.getId());
        verify(transferArtifactStateRepository).save(
                argThat(s -> tp.getRole().equals(s.getSuspendedBy())));
    }
```

- [ ] **Step 11.3: Add test for downloadData() handling TransferCancelledException**

```java
    @Test
    @DisplayName("downloadData handles TransferCancelledException by keeping checkpoint and resetting in-progress flag")
    void downloadDataHandlesCancelledException() throws Exception {
        TransferProcess tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(tp.getId())).thenReturn(Optional.of(tp));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cancellationRegistry.register(tp.getId())).thenReturn(new AtomicBoolean(false));
        // Strategy completes with TransferCancelledException
        when(transferStrategyFactory.getStrategy(any()))
                .thenReturn(tProcess -> CompletableFuture.failedFuture(
                        new java.util.concurrent.CompletionException(
                                new TransferCancelledException(tp.getId()))));

        service.downloadData(tp.getId()).get();

        // in-progress flag reset
        verify(transferProcessRepository, atLeastOnce())
                .save(argThat(saved -> !saved.isDownloadInProgress()));
        // token deregistered
        verify(cancellationRegistry).deregister(tp.getId());
        // TRANSFER_PAUSED audit event
        verify(publisher).publishEvent(eq(AuditEventType.TRANSFER_PAUSED), anyString(), any());
        // No completion message to peer
        verify(okHttpRestClient, never()).sendRequestProtocol(anyString(), any(), any());
    }
```

Add imports:
```java
import it.eng.datatransfer.exceptions.PresignedUrlExpiredException;
import it.eng.datatransfer.exceptions.TransferCancelledException;
import it.eng.datatransfer.model.TransferArtifactState;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.service.CancellationRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
```

- [ ] **Step 11.4: Add test for downloadData() handling PresignedUrlExpiredException**

```java
    @Test
    @DisplayName("downloadData handles PresignedUrlExpiredException by sending a 409 termination message")
    void downloadDataHandlesPresignedUrlExpiredException() throws Exception {
        TransferProcess tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(tp.getId())).thenReturn(Optional.of(tp));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cancellationRegistry.register(tp.getId())).thenReturn(new AtomicBoolean(false));
        when(apiResponse.isSuccess()).thenReturn(true);
        when(okHttpRestClient.sendRequestProtocol(anyString(), any(), any())).thenReturn(apiResponse);
        when(transferStrategyFactory.getStrategy(any()))
                .thenReturn(tProcess -> CompletableFuture.failedFuture(
                        new java.util.concurrent.CompletionException(
                                new PresignedUrlExpiredException(tp.getId()))));

        service.downloadData(tp.getId()).get();

        verify(publisher).publishEvent(eq(AuditEventType.TRANSFER_URL_EXPIRED), anyString(), any());
        // Termination request must be sent to peer
        verify(okHttpRestClient, atLeastOnce())
                .sendRequestProtocol(contains("termination"), any(), any());
        verify(cancellationRegistry).deregister(tp.getId());
    }
```

- [ ] **Step 11.5: Run DataTransferAPIService tests**

```bash
mvn -pl data-transfer -am -Dtest=DataTransferAPIServiceTest test
```
Expected: All PASS.

- [ ] **Step 11.6: Commit**

```bash
git add data-transfer/src/test/java/it/eng/datatransfer/service/api/DataTransferAPIServiceTest.java
git commit -m "test(transfer): suspend-signal, url-expiry, and resume unit tests for DataTransferAPIService

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 12: Integration tests — suspend/resume and URL-expiry flows

**Files:**
- Create: `connector/src/test/java/it/eng/connector/integration/datatransfer/DataTransferSuspendResumeIT.java`
- Create: `connector/src/test/java/it/eng/connector/integration/datatransfer/DataTransferUrlExpiryIT.java`

Both extend `BaseIntegrationTest` (MongoDB + MinIO Testcontainers, WireMock, MockMvc on port 8080).

- [ ] **Step 12.1: Create DataTransferSuspendResumeIT**

```java
// connector/src/test/java/it/eng/connector/integration/datatransfer/DataTransferSuspendResumeIT.java
package it.eng.connector.integration.datatransfer;

import com.github.tomakehurst.wiremock.WireMockServer;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.CancellationRegistry;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.wiremock.spring.InjectWireMock;

import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DataTransferSuspendResumeIT extends BaseIntegrationTest {

    @InjectWireMock
    private WireMockServer wiremock;

    @Autowired private TransferProcessRepository transferProcessRepository;
    @Autowired private TransferArtifactStateRepository transferArtifactStateRepository;
    @Autowired private CancellationRegistry cancellationRegistry;

    @AfterEach
    void cleanup() {
        transferProcessRepository.deleteAll();
        transferArtifactStateRepository.deleteAll();
    }

    @Test
    @DisplayName("suspendTransfer: CancellationRegistry is signalled and TransferArtifactState records suspendedBy")
    @WithUserDetails("admin")
    void suspendTransferSignalsCancellationAndPersistsSuspendedBy() throws Exception {
        TransferProcess tp = savedStartedConsumerPullTp();
        String tpId = tp.getId();
        // Simulate an in-progress download by registering a token
        cancellationRegistry.register(tpId);

        wiremock.stubFor(post(urlPathMatching(".*/transfers/.*/suspension"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        mockMvc.perform(put(ApiEndpoints.DATA_TRANSFER_V1 + "/" + tpId + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Token is now signalled (the download loop would deregister after detecting the signal)
        Optional<TransferArtifactState> stateOpt = transferArtifactStateRepository.findById(tpId);
        assertTrue(stateOpt.isPresent(), "TransferArtifactState must be persisted on suspend");
        assertEquals(IConstants.ROLE_CONSUMER, stateOpt.get().getSuspendedBy(),
                "suspendedBy must equal the sender role (CONSUMER called suspend)");

        TransferProcess suspended = transferProcessRepository.findById(tpId).orElseThrow();
        assertEquals(TransferState.SUSPENDED, suspended.getState());
    }

    @Test
    @DisplayName("startTransfer on SUSPENDED transfer rejected when wrong party attempts resume")
    @WithUserDetails("admin")
    void resumeByWrongPartyIsRejected() throws Exception {
        TransferProcess tp = savedSuspendedConsumerPullTp();
        // Provider suspended it — consumer cannot resume
        TransferArtifactState state = TransferArtifactState.Builder.newInstance()
                .id(tp.getId()).suspendedBy(IConstants.ROLE_PROVIDER).build();
        transferArtifactStateRepository.save(state);

        mockMvc.perform(put(ApiEndpoints.DATA_TRANSFER_V1 + "/" + tp.getId() + "/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    // helpers

    private TransferProcess savedStartedConsumerPullTp() {
        TransferProcess tp = TransferProcess.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .consumerPid("urn:uuid:consumer-sr-test")
                .providerPid("urn:uuid:provider-sr-test")
                .agreementId("urn:uuid:agreement-sr-test")
                .state(TransferState.STARTED)
                .role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format())
                .datasetId("dataset-sr-test")
                .callbackAddress(wiremock.baseUrl())
                .build();
        return transferProcessRepository.save(tp);
    }

    private TransferProcess savedSuspendedConsumerPullTp() {
        TransferProcess tp = TransferProcess.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .consumerPid("urn:uuid:consumer-sr2-test")
                .providerPid("urn:uuid:provider-sr2-test")
                .agreementId("urn:uuid:agreement-sr2-test")
                .state(TransferState.SUSPENDED)
                .role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format())
                .datasetId("dataset-sr2-test")
                .callbackAddress(wiremock.baseUrl())
                .build();
        return transferProcessRepository.save(tp);
    }
}
```

- [ ] **Step 12.2: Create DataTransferUrlExpiryIT**

```java
// connector/src/test/java/it/eng/connector/integration/datatransfer/DataTransferUrlExpiryIT.java
package it.eng.connector.integration.datatransfer;

import com.github.tomakehurst.wiremock.WireMockServer;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.tools.model.IConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.wiremock.spring.InjectWireMock;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataTransferUrlExpiryIT extends BaseIntegrationTest {

    @InjectWireMock
    private WireMockServer wiremock;

    @Autowired private TransferProcessRepository transferProcessRepository;

    @AfterEach
    void cleanup() {
        transferProcessRepository.deleteAll();
    }

    @Test
    @DisplayName("downloadData with expired presigned URL (HTTP 403) terminates the transfer with code 409")
    @WithUserDetails("admin")
    void downloadWithExpiredUrlTerminatesTransfer() throws Exception {
        String presignedUrl = wiremock.baseUrl() + "/expired-object";

        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(presignedUrl)
                .endpointType("https://w3id.org/idsa/v4.1/HTTP")
                .build();

        String tpId = UUID.randomUUID().toString();
        TransferProcess tp = TransferProcess.Builder.newInstance()
                .id(tpId)
                .consumerPid("urn:uuid:consumer-expiry")
                .providerPid("urn:uuid:provider-expiry")
                .agreementId("urn:uuid:agreement-expiry")
                .state(TransferState.STARTED)
                .role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format())
                .datasetId("dataset-expiry")
                .callbackAddress(wiremock.baseUrl())
                .dataAddress(dataAddress)
                .build();
        transferProcessRepository.save(tp);

        // Presigned URL returns 403
        wiremock.stubFor(get(urlPathEqualTo("/expired-object"))
                .willReturn(aResponse().withStatus(403)));
        // Peer accepts the termination message
        wiremock.stubFor(post(urlPathMatching(".*/transfers/.*/termination"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        // Trigger download directly via the service — the async download will detect 403
        // Use the existing download endpoint or trigger downloadData via the API
        // (the exact API path depends on how downloadData is exposed; adjust if needed)
        // POST to the data download endpoint:
        // mockMvc.perform(post(ApiEndpoints.DATA_TRANSFER_V1 + "/" + tpId + "/data") ...

        // For simplicity, find and invoke the service via the application context
        it.eng.datatransfer.service.api.DataTransferAPIService apiService =
                applicationContext.getBean(it.eng.datatransfer.service.api.DataTransferAPIService.class);
        apiService.downloadData(tpId); // fire and forget; whenComplete handles the rest

        // Wait for async termination
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            TransferProcess result = transferProcessRepository.findById(tpId).orElseThrow();
            assertEquals(TransferState.TERMINATED, result.getState(),
                    "Transfer should be TERMINATED after URL expiry");
        });

        // Termination message was sent to peer
        wiremock.verify(postRequestedFor(urlPathMatching(".*/transfers/.*/termination")));
    }
}
```

- [ ] **Step 12.3: Run integration tests (requires Docker)**

```bash
mvn -pl connector -am -Dit.test="DataTransferSuspendResumeIT,DataTransferUrlExpiryIT" verify \
    -Dskip.surefire.tests=true
```
Expected: Both ITs PASS.

- [ ] **Step 12.4: Run full verify**

```bash
mvn clean verify
```
Expected: BUILD SUCCESS — all unit + integration tests PASS.

- [ ] **Step 12.5: Commit**

```bash
git add connector/src/test/java/it/eng/connector/integration/datatransfer/DataTransferSuspendResumeIT.java \
        connector/src/test/java/it/eng/connector/integration/datatransfer/DataTransferUrlExpiryIT.java
git commit -m "test(transfer): integration tests for suspend/resume and URL-expiry termination

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Self-Review Checklist

Run after all tasks are complete:

- [ ] `mvn clean verify` passes
- [ ] `mvn validate` passes (Checkstyle)
- [ ] `CancellationRegistry.deregister()` called on ALL exit paths in `downloadData().whenComplete()`
- [ ] `suspendTransfer()` API: signals registry AND records `suspendedBy = transferProcess.getRole()` before 200 OK success check
- [ ] `suspendDataTransfer()` protocol: records `suspendedBy = opposite role` AND signals registry
- [ ] `startDataTransfer()` protocol: validates `suspendedBy == senderRole` for SUSPENDED→STARTED; clears `suspendedBy` on success
- [ ] `startTransfer()` API: validates `suspendedBy == local role` for SUSPENDED→STARTED
- [ ] `S3SyncUploadStrategy`: aborts multipart before throwing `TransferCancelledException`
- [ ] `S3AsyncUploadStrategy`: aborts multipart before throwing `TransferCancelledException`
- [ ] `HttpPullTransferStrategy`: `Range: bytes=N-` header added only when `rangeStart > 0`; 403 → `PresignedUrlExpiredException`; 206 accepted alongside 200
- [ ] `HttpPushTransferStrategy`: same as HttpPull
- [ ] `terminateTransferWithReason()` sends code `"409"` and reason `"download URL expired"`
- [ ] Three new `AuditEventType` values present: `TRANSFER_PAUSED`, `TRANSFER_RESUMED`, `TRANSFER_URL_EXPIRED`
- [ ] `TransferArtifactState.getSuspendedBy()` available (Lombok `@Getter` on class or field-level)
- [ ] No dangling `@Override` on the old 4-param `uploadFile` in `S3ClientServiceImpl` (it became a `default`)
- [ ] `DataTransferService` and `TCKDataTransferService` constructors both pass `CancellationRegistry` and `TransferArtifactStateRepository` to `super()`
