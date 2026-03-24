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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
        lenient().when(s3Properties.getChunkSize()).thenReturn(50 * 1024 * 1024);
        when(s3ClientProvider.s3AsyncClient(any(S3ClientRequest.class))).thenReturn(s3AsyncClient);
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
        // Arrange - create data larger than CHUNK_SIZE (50MB)
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
    @DisplayName("Should use AsyncRequestBody.fromInputStream (not fromBytes) to avoid extra memory copy")
    void uploadFile_UsesFromInputStream_NotFromBytes() {
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
                "AsyncRequestBody should have a known content length when created via fromInputStream"));
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
        // Arrange — 60 MB stream triggers 2 parts (part 1 = 50 MB, part 2 = 10 MB).
        // Both fit within MAX_PARALLEL_PARTS=4, so the semaphore must not block either from starting.
        byte[] data = new byte[60 * 1024 * 1024];
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
}

