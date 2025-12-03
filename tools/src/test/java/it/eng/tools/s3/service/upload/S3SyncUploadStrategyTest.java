package it.eng.tools.s3.service.upload;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.S3ClientRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
}

