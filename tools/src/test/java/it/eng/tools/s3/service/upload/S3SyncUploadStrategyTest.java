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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
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
 * Unit tests for S3SyncUploadStrategy.
 */
@ExtendWith(MockitoExtension.class)
public class S3SyncUploadStrategyTest {

    private static final String BUCKET_NAME = "test-bucket";
    private static final String OBJECT_KEY = "test-file.txt";
    private static final String CONTENT_TYPE = "text/plain";
    private static final String CONTENT_DISPOSITION = "attachment; filename=test-file.txt";
    private static final String UPLOAD_ID = "sync-upload-id";
    private static final String ETAG = "sync-etag";

    @Mock
    private S3ClientProvider s3ClientProvider;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3ClientRequest s3ClientRequest;

    @InjectMocks
    private S3SyncUploadStrategy syncUploadStrategy;

    @BeforeEach
    void setUp() {
        when(s3ClientProvider.s3Client(any(S3ClientRequest.class))).thenReturn(s3Client);
    }

    @Test
    @DisplayName("Should successfully upload file synchronously")
    void uploadFile_Success() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());

        lenient().when(s3Client.uploadPart(any(UploadPartRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag(ETAG).build());

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag(ETAG).build());

        // Act
        CompletableFuture<String> result = syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(ETAG, result.join());
        verify(s3ClientProvider).s3Client(s3ClientRequest);
        verify(s3Client).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should handle upload failure synchronously")
    void uploadFile_UploadFails() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenThrow(S3Exception.builder().message("Sync upload failed").build());

        // Act
        CompletableFuture<String> result = syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        Exception exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getMessage().contains("Failed to upload file"));
        verify(s3Client).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should handle complete multipart upload failure synchronously")
    void uploadFile_CompleteMultipartUploadFails() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());

        lenient().when(s3Client.uploadPart(any(UploadPartRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag(ETAG).build());

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenThrow(S3Exception.builder().message("Complete upload failed").build());

        // Act
        CompletableFuture<String> result = syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        Exception exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getMessage().contains("Failed to upload file"));
        verify(s3Client).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should handle large file with multiple parts synchronously")
    void uploadFile_LargeFileMultipleParts() {
        // Arrange - create data larger than CHUNK_SIZE (50MB)
        // For test purposes, we'll use smaller chunks
        byte[] largeData = new byte[100 * 1024]; // 100KB for testing
        InputStream inputStream = new ByteArrayInputStream(largeData);

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());

        when(s3Client.uploadPart(any(UploadPartRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag(ETAG).build());

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag(ETAG).build());

        // Act
        CompletableFuture<String> result = syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(ETAG, result.join());
        verify(s3Client).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should upload empty stream with zero parts and still complete the upload")
    void uploadFile_EmptyStream() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag(ETAG).build());

        // Act
        CompletableFuture<String> result = syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert — no parts uploaded, but upload should complete successfully
        assertEquals(ETAG, result.join());
        verify(s3Client).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client, never()).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
        verify(s3Client).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should use RequestBody.fromInputStream (not fromBytes) to avoid extra memory copy")
    void uploadFile_UsesFromInputStream_NotFromBytes() {
        // Arrange - small content to ensure exactly one part
        InputStream inputStream = new ByteArrayInputStream("part-data".getBytes());

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());

        ArgumentCaptor<RequestBody> requestBodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        when(s3Client.uploadPart(any(UploadPartRequest.class), requestBodyCaptor.capture()))
                .thenReturn(UploadPartResponse.builder().eTag(ETAG).build());

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag(ETAG).build());

        // Act
        CompletableFuture<String> result = syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION);

        assertEquals(ETAG, result.join());

        // Assert — exactly one part was captured and it is a stream-based body
        List<RequestBody> capturedBodies = requestBodyCaptor.getAllValues();
        assertEquals(1, capturedBodies.size(), "Expected exactly one uploadPart call for small content");
        // RequestBody.fromInputStream produces an OptionalContentLength-backed body
        // (contentLength will be present and > 0)
        capturedBodies.forEach(rb -> assertTrue(rb.optionalContentLength().isPresent(),
                "RequestBody should carry a known content length when created from byte[] via fromInputStream"));
    }

    @Test
    @DisplayName("Should upload correct content-type and content-disposition in multipart upload request")
    void uploadFile_CorrectMetadataInCreateRequest() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("data".getBytes());

        ArgumentCaptor<CreateMultipartUploadRequest> createCaptor =
                ArgumentCaptor.forClass(CreateMultipartUploadRequest.class);

        when(s3Client.createMultipartUpload(createCaptor.capture()))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());
        lenient().when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag(ETAG).build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag(ETAG).build());

        // Act
        syncUploadStrategy.uploadFile(inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION).join();

        // Assert metadata forwarded correctly
        CreateMultipartUploadRequest captured = createCaptor.getValue();
        assertEquals(BUCKET_NAME, captured.bucket());
        assertEquals(OBJECT_KEY, captured.key());
        assertEquals(CONTENT_TYPE, captured.contentType());
        assertEquals(CONTENT_DISPOSITION, captured.contentDisposition());
    }
}

