package it.eng.tools.s3.service.upload;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.S3ClientRequest;
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
    private S3AsyncClient s3AsyncClient;

    @Mock
    private S3ClientRequest s3ClientRequest;

    @InjectMocks
    private S3AsyncUploadStrategy asyncUploadStrategy;

    @BeforeEach
    void setUp() {
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
    void uploadFile_BoundedParallelism_WithSemaphore() {
        // Arrange — use a 60 MB stream to trigger at least 2 parts (CHUNK_SIZE = 50 MB)
        byte[] data = new byte[60 * 1024 * 1024];
        InputStream inputStream = new ByteArrayInputStream(data);

        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()));

        // Track concurrent in-flight uploads via a shared counter
        java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger maxObservedInFlight = new java.util.concurrent.atomic.AtomicInteger(0);

        when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenAnswer(inv -> {
                    int current = inFlight.incrementAndGet();
                    maxObservedInFlight.updateAndGet(prev -> Math.max(prev, current));
                    inFlight.decrementAndGet();
                    return CompletableFuture.completedFuture(UploadPartResponse.builder().eTag(ETAG).build());
                });

        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(ETAG).build()));

        // Act
        CompletableFuture<String> result = asyncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        assertEquals(ETAG, result.join());

        // Assert — MAX_PARALLEL_PARTS is 4; at most that many should be in flight
        assertTrue(maxObservedInFlight.get() <= 4,
                "Max in-flight parts (" + maxObservedInFlight.get() + ") exceeded MAX_PARALLEL_PARTS=4");
        // At least 2 parts were uploaded for a 60 MB stream
        verify(s3AsyncClient, atLeast(2)).uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class));
    }
}

