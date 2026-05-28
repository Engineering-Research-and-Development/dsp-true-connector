package it.eng.tools.s3.service.upload;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for S3AsyncUploadStrategy.
 */
@ExtendWith(MockitoExtension.class)
public class S3AsyncUploadStrategyTest {

    private static final String BUCKET_NAME = "test-bucket";
    private static final String OBJECT_KEY = "test-file.txt";
    private static final String CONTENT_TYPE = "text/plain";
    private static final String CONTENT_DISPOSITION = "attachment; filename=test-file.txt";
    private static final String UPLOAD_ID = "async-upload-id";
    private static final String ETAG = "async-etag";

    @Mock
    private S3ClientProvider s3ClientProvider;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private S3AsyncClient s3AsyncClient;

    @Mock
    private S3ClientRequest s3ClientRequest;

    @InjectMocks
    private S3AsyncUploadStrategy asyncUploadStrategy;

    @BeforeEach
    void setUp() {
        lenient().when(s3Properties.getChunkSize()).thenReturn(10 * 1024 * 1024);
        lenient().when(s3ClientProvider.s3AsyncClient(any(S3ClientRequest.class))).thenReturn(s3AsyncClient);
    }

    @Test
    @DisplayName("Should successfully upload file asynchronously")
    void uploadFile_Success() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        lenient().when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UploadPartResponse.builder().eTag(ETAG).build()));

        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(ETAG).build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(ETAG, result.join());
        verify(s3ClientProvider).s3AsyncClient(s3ClientRequest);
        verify(s3AsyncClient).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3AsyncClient).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should handle upload failure asynchronously")
    void uploadFile_UploadFails() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        S3Exception.builder().message("Async upload failed").build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        Exception exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getMessage().contains("Failed to upload file"));
        verify(s3AsyncClient).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3AsyncClient, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should handle complete multipart upload failure asynchronously")
    void uploadFile_CompleteMultipartUploadFails() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        lenient().when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UploadPartResponse.builder().eTag(ETAG).build()));

        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        S3Exception.builder().message("Complete upload failed").build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        Exception exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getMessage().contains("Failed to upload file"));
        verify(s3AsyncClient).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3AsyncClient).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should handle large file with multiple parts asynchronously")
    void uploadFile_LargeFileMultipleParts() {
        // Arrange - create data larger than CHUNK_SIZE (10MB)
        // For test purposes, we'll use smaller chunks
        byte[] largeData = new byte[100 * 1024]; // 100KB for testing
        InputStream inputStream = new ByteArrayInputStream(largeData);

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UploadPartResponse.builder().eTag(ETAG).build()));

        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(ETAG).build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(ETAG, result.join());
        verify(s3AsyncClient).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3AsyncClient).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should handle part upload failure asynchronously")
    void uploadFile_PartUploadFails() {
        // Arrange
        byte[] largeData = new byte[60 * 1024 * 1024]; // 60MB to ensure multiple parts
        InputStream inputStream = new ByteArrayInputStream(largeData);

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        S3Exception.builder().message("Part upload failed").build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert - should fail when part upload fails
        Exception exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getMessage().contains("Failed to") || exception.getCause() instanceof S3Exception);
        verify(s3AsyncClient).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should upload empty stream with zero parts and still complete the upload")
    void uploadFile_EmptyStream() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(ETAG).build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert — no parts uploaded, upload still completes
        assertEquals(ETAG, result.join());
        verify(s3AsyncClient).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3AsyncClient, never()).uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class));
        verify(s3AsyncClient).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should use AsyncRequestBody.fromBytes to build each part request body")
    void uploadFile_UsesFromBytes() {
        // Arrange — small content ensures exactly one part
        InputStream inputStream = new ByteArrayInputStream("part-data".getBytes());

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        ArgumentCaptor<AsyncRequestBody> bodyCaptor = ArgumentCaptor.forClass(AsyncRequestBody.class);
        when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), bodyCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(
                        UploadPartResponse.builder().eTag(ETAG).build()));

        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(ETAG).build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        assertEquals(ETAG, result.join());

        // Assert — exactly one part was uploaded; the body carries a known content length
        List<AsyncRequestBody> capturedBodies = bodyCaptor.getAllValues();
        assertEquals(1, capturedBodies.size(), "Expected exactly one uploadPart call for small content");
        capturedBodies.forEach(body -> assertTrue(body.contentLength().isPresent(),
                "AsyncRequestBody should have a known content length when created via fromBytes"));
    }

    @Test
    @DisplayName("Should upload correct content-type and content-disposition in multipart upload request")
    void uploadFile_CorrectMetadataInCreateRequest() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("data".getBytes());

        ArgumentCaptor<CreateMultipartUploadRequest> createCaptor =
                ArgumentCaptor.forClass(CreateMultipartUploadRequest.class);

        when(s3AsyncClient.createMultipartUpload(createCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        lenient().when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UploadPartResponse.builder().eTag(ETAG).build()));

        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(ETAG).build()));

        // Act
        asyncUploadStrategy.uploadFile(inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION).join();

        // Assert
        CreateMultipartUploadRequest captured = createCaptor.getValue();
        assertEquals(BUCKET_NAME, captured.bucket());
        assertEquals(OBJECT_KEY, captured.key());
        assertEquals(CONTENT_TYPE, captured.contentType());
        assertEquals(CONTENT_DISPOSITION, captured.contentDisposition());
    }

    @Test
    @DisplayName("Should respect semaphore — no more than MAX_PARALLEL_PARTS parts in flight simultaneously")
    void uploadFile_BoundedParallelism_WithSemaphore() throws InterruptedException {
        // Arrange — 15 MB stream triggers 2 parts (part 1 = 10 MB, part 2 = 5 MB).
        // Both fit within MAX_PARALLEL_PARTS=4, so the semaphore must not block either from starting.
        byte[] data = new byte[15 * 1024 * 1024];
        InputStream inputStream = new ByteArrayInputStream(data);

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger maxObservedInFlight = new AtomicInteger(0);
        // Counts down each time a part upload is started; lets the test detect when both are in flight.
        CountDownLatch allPartsStarted = new CountDownLatch(2);
        // Gate that holds every part-upload future until the test deliberately releases them,
        // ensuring both parts remain in-flight at the same time rather than completing immediately.
        CountDownLatch releaseGate = new CountDownLatch(1);

        when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenAnswer(inv -> {
                    int current = inFlight.incrementAndGet();
                    maxObservedInFlight.updateAndGet(prev -> Math.max(prev, current));
                    allPartsStarted.countDown();
                    // Return a future that does NOT complete until the gate is opened,
                    // so multiple parts can be simultaneously in-flight.
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            releaseGate.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        inFlight.decrementAndGet();
                        return UploadPartResponse.builder().eTag(ETAG).build();
                    });
                });

        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(ETAG).build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Both parts should start before either one completes (gate is still closed).
        assertTrue(allPartsStarted.await(10, TimeUnit.SECONDS),
                "Both part uploads should have started within the timeout");

        // Assert — at peak, both parts are genuinely in flight at the same time.
        assertEquals(2, maxObservedInFlight.get(),
                "Both parts should be in flight simultaneously (semaphore allows up to MAX_PARALLEL_PARTS=4)");
        assertTrue(maxObservedInFlight.get() <= 4,
                "Max in-flight parts (" + maxObservedInFlight.get() + ") exceeded MAX_PARALLEL_PARTS=4");

        // Release the gate so all parts can finish and the upload can complete.
        releaseGate.countDown();

        assertEquals(ETAG, result.join());
        verify(s3AsyncClient, times(2)).uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when completedParts and partSizes sizes differ")
    void resumableUploadRequest_rejectsInconsistentPartListsAndPartSizes() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResumableUploadRequest(
                        UPLOAD_ID,
                        List.of(CompletedPart.builder().partNumber(1).eTag(ETAG).build()),
                        List.of(),      // wrong size
                        0L,
                        new AtomicBoolean(false),
                        UploadCheckpointCallback.noop()),
                "Constructor must reject completedParts/partSizes size mismatch");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when confirmedBytes is non-zero with empty completedParts")
    void resumableUploadRequest_rejectsNonZeroConfirmedBytesWithNoParts() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResumableUploadRequest(
                        null,
                        List.of(),
                        List.of(),
                        1L,
                        new AtomicBoolean(false),
                        UploadCheckpointCallback.noop()),
                "Constructor must reject confirmedBytes > 0 when completedParts is empty");
    }

    @Test
    @DisplayName("confirmedBytes acts as floor: callback reports at least confirmedBytes on resume")
    void asyncUpload_confirmedBytesUsedAsFloorWhenResuming() {
        // Arrange: resume with 2 × 100-byte parts already confirmed.
        int chunkSize = 100;
        when(s3Properties.getChunkSize()).thenReturn(chunkSize);

        byte[] newData = new byte[chunkSize]; // one new part
        InputStream inputStream = new ByteArrayInputStream(newData);

        String existingUploadId = "resume-async-upload";
        CompletedPart existingPart1 = CompletedPart.builder().partNumber(1).eTag("etag-1").build();
        CompletedPart existingPart2 = CompletedPart.builder().partNumber(2).eTag("etag-2").build();
        long previousConfirmedBytes = 2L * chunkSize;

        List<Long> observedContiguous = new ArrayList<>();
        UploadCheckpointCallback callback = new UploadCheckpointCallback() {
            @Override
            public void onMultipartCreated(String uploadId) { }

            @Override
            public void onPartCompleted(int partNumber, String eTag, long partSize, long contiguousConfirmedBytes) {
                observedContiguous.add(contiguousConfirmedBytes);
            }
        };

        ResumableUploadRequest resumable = new ResumableUploadRequest(
                existingUploadId,
                List.of(existingPart1, existingPart2),
                List.of((long) chunkSize, (long) chunkSize),
                previousConfirmedBytes,
                new AtomicBoolean(false),
                callback);

        when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UploadPartResponse.builder().eTag("etag-3").build()));
        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(ETAG).build()));

        // Act
        asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION, resumable).join();

        // Assert — after new part 3 completes contiguous = 3 × chunkSize (≥ floor of 200)
        assertEquals(1, observedContiguous.size(), "Exactly one new part was uploaded");
        assertEquals(3L * chunkSize, observedContiguous.get(0),
                "Contiguous bytes after resuming and uploading part 3 must reflect all 3 parts");
    }

    @Test
    @DisplayName("Should reuse existing uploadId and not call createMultipartUpload")
    void asyncUpload_reusesExistingUploadId() {
        // Arrange
        String existingUploadId = "existing-async-upload";
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        ResumableUploadRequest resumable = new ResumableUploadRequest(
                existingUploadId,
                List.of(),
                List.of(),
                0L,
                new AtomicBoolean(false),
                UploadCheckpointCallback.noop());

        lenient().when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UploadPartResponse.builder().eTag(ETAG).build()));

        ArgumentCaptor<CompleteMultipartUploadRequest> completeCaptor =
                ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        when(s3AsyncClient.completeMultipartUpload(completeCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(ETAG).build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION, resumable);

        // Assert
        assertEquals(ETAG, result.join());
        verify(s3AsyncClient, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        assertEquals(existingUploadId, completeCaptor.getValue().uploadId());
    }

    @Test
    @DisplayName("Should surface UploadPausedException when suspendRequested becomes true")
    void asyncUpload_throwsUploadPausedExceptionWhenSuspendRequested() {
        // Arrange — suspend flag set before the first loop iteration
        InputStream inputStream = new ByteArrayInputStream("data".getBytes());
        AtomicBoolean suspendRequested = new AtomicBoolean(true);

        ResumableUploadRequest resumable = new ResumableUploadRequest(
                null, List.of(), List.of(), 0L, suspendRequested, UploadCheckpointCallback.noop());

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION, resumable);

        // Assert — UploadPausedException is surfaced via CompletionException
        CompletionException ce = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(UploadPausedException.class, ce.getCause());
        UploadPausedException pex = (UploadPausedException) ce.getCause();
        assertEquals(UPLOAD_ID, pex.getUploadId(),
                "Exception must carry the uploadId from the multipart upload");
        assertTrue(pex.getCompletedParts().isEmpty(),
                "No parts should be completed when suspended before any part was uploaded");
    }

    @Test
    @DisplayName("Callback reports contiguous confirmed bytes even when async parts complete out of order")
    void asyncUpload_reportsContiguousBytesForOutOfOrderCompletion() throws InterruptedException {
        // Arrange — 3 parts of 100 bytes each so we can control completion order
        int chunkSize = 100;
        when(s3Properties.getChunkSize()).thenReturn(chunkSize);
        byte[] data = new byte[3 * chunkSize];
        InputStream inputStream = new ByteArrayInputStream(data);

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        // Promises for each of the 3 parts, indexed 0..2 (partNumber 1..3)
        @SuppressWarnings("unchecked")
        CompletableFuture<UploadPartResponse>[] partPromises = new CompletableFuture[3];
        for (int i = 0; i < 3; i++) {
            partPromises[i] = new CompletableFuture<>();
        }

        CountDownLatch allPartsSubmitted = new CountDownLatch(3);
        AtomicInteger callIdx = new AtomicInteger(0);
        when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenAnswer(inv -> {
                    int idx = callIdx.getAndIncrement();
                    allPartsSubmitted.countDown();
                    return partPromises[idx];
                });

        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(ETAG).build()));

        List<Long> observedContiguous = new ArrayList<>();
        UploadCheckpointCallback callback = new UploadCheckpointCallback() {
            @Override
            public void onMultipartCreated(String uploadId) { }

            @Override
            public void onPartCompleted(int partNumber, String eTag, long partSize, long contiguousConfirmedBytes) {
                observedContiguous.add(contiguousConfirmedBytes);
            }
        };

        ResumableUploadRequest resumable = new ResumableUploadRequest(
                null, List.of(), List.of(), 0L,
                new AtomicBoolean(false), callback);

        // Act
        CompletableFuture<String> uploadFuture = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION, resumable);

        // Wait for all 3 parts to be submitted before resolving any
        assertTrue(allPartsSubmitted.await(10, TimeUnit.SECONDS), "All 3 parts must be submitted");

        // Complete out of order: part 3, then part 1, then part 2
        partPromises[2].complete(UploadPartResponse.builder().eTag("etag-3").build());
        partPromises[0].complete(UploadPartResponse.builder().eTag("etag-1").build());
        partPromises[1].complete(UploadPartResponse.builder().eTag("etag-2").build());

        assertEquals(ETAG, uploadFuture.join());

        // Assert contiguous confirmed bytes reported correctly:
        // After part 3 completes: part 1 missing → contiguous = 0
        // After part 1 completes: part 2 missing → contiguous = chunkSize (100)
        // After part 2 completes: all done → contiguous = 3 * chunkSize (300)
        assertEquals(3, observedContiguous.size(), "Callback must be called once per part");
        assertEquals(0L, observedContiguous.get(0), "After part 3: no contiguous bytes from start");
        assertEquals((long) chunkSize, observedContiguous.get(1), "After part 1: contiguous = 1 chunk");
        assertEquals((long) (3 * chunkSize), observedContiguous.get(2), "After part 2: contiguous = 3 chunks");
    }
}

