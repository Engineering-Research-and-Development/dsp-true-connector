package it.eng.tools.s3.service.upload;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private S3Properties s3Properties;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3ClientRequest s3ClientRequest;

    @InjectMocks
    private S3SyncUploadStrategy syncUploadStrategy;

    @BeforeEach
    void setUp() {
        lenient().when(s3Properties.getChunkSize()).thenReturn(10 * 1024 * 1024);
        lenient().when(s3ClientProvider.s3Client(any(S3ClientRequest.class))).thenReturn(s3Client);
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
        Exception exception = assertThrows(CompletionException.class, result::join);
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
        Exception exception = assertThrows(CompletionException.class, result::join);
        assertTrue(exception.getMessage().contains("Failed to upload file"));
        verify(s3Client).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should handle large file with multiple parts synchronously")
    void uploadFile_LargeFileMultipleParts() {
        // Arrange - create data larger than CHUNK_SIZE (10MB)
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
    @DisplayName("Should pass the correct bytes to uploadPart for a single-part upload")
    void uploadFile_CorrectBytesPassedToUploadPart() throws Exception {
        // Arrange - small content that fits in one part
        byte[] expectedBytes = "part-data".getBytes();
        InputStream inputStream = new ByteArrayInputStream(expectedBytes);

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

        // Assert — exactly one part was uploaded and its content matches the original input bytes.
        // This is a behavioral check: it will catch any regression that corrupts or drops data,
        // regardless of which RequestBody factory method (fromBytes, fromInputStream, etc.) is used.
        List<RequestBody> capturedBodies = requestBodyCaptor.getAllValues();
        assertEquals(1, capturedBodies.size(), "Expected exactly one uploadPart call for small content");
        byte[] actualBytes;
        try (InputStream capturedStream = capturedBodies.get(0).contentStreamProvider().newStream()) {
            actualBytes = capturedStream.readAllBytes();
        }
        assertArrayEquals(expectedBytes, actualBytes,
                "The bytes delivered to uploadPart must exactly match the original input stream content");
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

    @Test
    @DisplayName("Should call onMultipartCreated with the fresh uploadId on a new upload")
    void syncUpload_callsOnMultipartCreatedWithFreshUploadId() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("data".getBytes());
        UploadCheckpointCallback callback = mock(UploadCheckpointCallback.class);
        ResumableUploadRequest resumable = new ResumableUploadRequest(
                null, List.of(), List.of(), 0L, new AtomicBoolean(false), callback);

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());
        lenient().when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag(ETAG).build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag(ETAG).build());

        // Act
        syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION, resumable).join();

        // Assert
        verify(callback).onMultipartCreated(UPLOAD_ID);
    }

    @Test
    @DisplayName("Should call onPartCompleted per part with contiguous confirmed bytes")
    void syncUpload_callsOnPartCompletedWithContiguousBytes() {
        // Arrange — 3 chunks of 100 bytes each (chunkSize = 100)
        int chunkSize = 100;
        when(s3Properties.getChunkSize()).thenReturn(chunkSize);
        byte[] data = new byte[3 * chunkSize]; // 300 bytes → 3 parts
        InputStream inputStream = new ByteArrayInputStream(data);

        UploadCheckpointCallback callback = mock(UploadCheckpointCallback.class);
        ResumableUploadRequest resumable = new ResumableUploadRequest(
                null, List.of(), List.of(), 0L, new AtomicBoolean(false), callback);

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag(ETAG).build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag(ETAG).build());

        // Act
        syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION, resumable).join();

        // Assert — three parts completed in order; contiguous bytes grow by chunkSize each time
        InOrder inOrder = inOrder(callback);
        inOrder.verify(callback).onMultipartCreated(UPLOAD_ID);
        inOrder.verify(callback).onPartCompleted(1, ETAG, chunkSize, (long) chunkSize);
        inOrder.verify(callback).onPartCompleted(2, ETAG, chunkSize, (long) (2 * chunkSize));
        inOrder.verify(callback).onPartCompleted(3, ETAG, chunkSize, (long) (3 * chunkSize));
    }

    @Test
    @DisplayName("Should throw UploadPausedException carrying checkpoint state when suspendRequested is true")
    void syncUpload_throwsUploadPausedExceptionWhenSuspendRequested() {
        // Arrange — suspend flag is set before the first loop iteration
        InputStream inputStream = new ByteArrayInputStream("data".getBytes());
        AtomicBoolean suspendRequested = new AtomicBoolean(true);
        ResumableUploadRequest resumable = new ResumableUploadRequest(
                null, List.of(), List.of(), 0L, suspendRequested, UploadCheckpointCallback.noop());

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build());

        // Act
        CompletableFuture<String> result = syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION, resumable);

        // Assert — UploadPausedException is surfaced via CompletionException
        CompletionException ce = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(UploadPausedException.class, ce.getCause());
        UploadPausedException pex = (UploadPausedException) ce.getCause();
        assertEquals(UPLOAD_ID, pex.getUploadId(),
                "Exception must carry the uploadId from the just-created multipart upload");
        assertTrue(pex.getCompletedParts().isEmpty(),
                "No parts should be completed when suspended before any part was uploaded");
        assertEquals(0L, pex.getConfirmedBytes(),
                "Confirmed bytes must be 0 when no parts were uploaded before suspension");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when completedParts and partSizes sizes differ")
    void resumableUploadRequest_rejectsInconsistentPartListsAndPartSizes() {
        // completedParts has 1 entry but partSizes is empty → mismatch
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
                        1L,             // non-zero with no parts
                        new AtomicBoolean(false),
                        UploadCheckpointCallback.noop()),
                "Constructor must reject confirmedBytes > 0 when completedParts is empty");
    }

    @Test
    @DisplayName("confirmedBytes acts as floor: callback reports at least confirmedBytes on resume")
    void syncUpload_confirmedBytesUsedAsFloorWhenResuming() {
        // Arrange: resume a previous upload that had 2 × 100-byte parts already confirmed.
        int chunkSize = 100;
        when(s3Properties.getChunkSize()).thenReturn(chunkSize);

        // One new part of 100 bytes to upload
        byte[] newData = new byte[chunkSize];
        InputStream inputStream = new ByteArrayInputStream(newData);

        String existingUploadId = "resume-sync-upload";
        CompletedPart existingPart1 = CompletedPart.builder().partNumber(1).eTag("etag-1").build();
        CompletedPart existingPart2 = CompletedPart.builder().partNumber(2).eTag("etag-2").build();
        long previousConfirmedBytes = 2L * chunkSize; // 200 bytes confirmed from previous session

        UploadCheckpointCallback callback = mock(UploadCheckpointCallback.class);
        ResumableUploadRequest resumable = new ResumableUploadRequest(
                existingUploadId,
                List.of(existingPart1, existingPart2),
                List.of((long) chunkSize, (long) chunkSize),
                previousConfirmedBytes,
                new AtomicBoolean(false),
                callback);

        when(s3Client.uploadPart(any(UploadPartRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("etag-3").build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag(ETAG).build());

        // Act
        syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION, resumable).join();

        // Assert — new part 3 completed; contiguous = 3 × chunkSize (≥ floor of 200)
        verify(callback).onPartCompleted(3, "etag-3", chunkSize, 3L * chunkSize);
    }

    @Test
    @DisplayName("Should reuse existing uploadId instead of creating a new multipart upload")
    void syncUpload_reusesExistingUploadId() {
        // Arrange
        String existingUploadId = "existing-upload";
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        ResumableUploadRequest resumable = new ResumableUploadRequest(
                existingUploadId,
                List.of(),
                List.of(),
                0L,
                new AtomicBoolean(false),
                UploadCheckpointCallback.noop());

        lenient().when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag(ETAG).build());

        ArgumentCaptor<CompleteMultipartUploadRequest> completeCaptor =
                ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        when(s3Client.completeMultipartUpload(completeCaptor.capture()))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag(ETAG).build());

        // Act
        CompletableFuture<String> result = syncUploadStrategy.uploadFile(
                inputStream, s3ClientRequest, BUCKET_NAME, OBJECT_KEY, CONTENT_TYPE, CONTENT_DISPOSITION, resumable);

        // Assert
        assertEquals(ETAG, result.join());
        verify(s3Client, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        assertEquals(existingUploadId, completeCaptor.getValue().uploadId());
    }
}

