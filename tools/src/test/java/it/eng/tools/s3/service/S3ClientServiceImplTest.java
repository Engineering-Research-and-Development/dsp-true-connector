package it.eng.tools.s3.service;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.util.S3Utils;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ClientServiceImplTest {

    private static final String KEY = "test-file.txt";
    private static final Map<String, String> DESTINATION_S3_PROPERTIES = Map.of(S3Utils.OBJECT_KEY, KEY);
    private static final String CONTENT_TYPE = "text/plain";
    private static final String CONTENT_DISPOSITION = "attachment; filename=test-file.txt";
    private static final InputStream INPUT_STREAM = new ByteArrayInputStream("test content".getBytes());
    @Mock
    private S3ClientProvider s3ClientProvider;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3AsyncClient s3AsyncClient;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private HttpServletResponse response;

    @Mock
    private ServletOutputStream outputStream;

    @Mock
    private ResponseInputStream<GetObjectResponse> responseInputStream;

    @Mock
    private BucketCredentialsService bucketCredentialsService;

    @Mock
    private it.eng.tools.service.ApplicationPropertiesService applicationPropertiesService;

    @Mock
    private it.eng.tools.s3.service.upload.S3UploadStrategyFactory uploadStrategyFactory;

    @Mock
    private it.eng.tools.s3.service.upload.S3UploadStrategy mockUploadStrategy;

    @Mock
    private GetObjectResponse getObjectResponse;

    String bucketName = "test-bucket";

    @InjectMocks
    private S3ClientServiceImpl s3ClientService;

    @BeforeEach
    void setUp() {
        BucketCredentialsEntity bucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                .accessKey("accessKey")
                .secretKey("secretKey")
                .bucketName(bucketName)
                .build();
        lenient().when(bucketCredentialsService.getBucketCredentials(any())).thenReturn(bucketCredentials);
        lenient().when(s3ClientProvider.s3Client(any(S3ClientRequest.class))).thenReturn(s3Client);
        lenient().when(s3ClientProvider.s3AsyncClient(any(S3ClientRequest.class))).thenReturn(s3AsyncClient);
        lenient().when(s3ClientProvider.adminS3Client()).thenReturn(s3Client);
        // Default to ASYNC mode for backward compatibility with existing tests
        lenient().when(s3Properties.getUploadMode()).thenReturn("ASYNC");
        lenient().when(applicationPropertiesService.getPropertyByKey(any())).thenReturn(java.util.Optional.empty());

        // Configure factory to return mock strategy
        lenient().when(uploadStrategyFactory.getStrategy(any())).thenReturn(mockUploadStrategy);

        // Configure default behavior for mock upload strategy
        lenient().when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("test-etag"));
    }

    // uploadFile tests
    @Test
    @DisplayName("Should successfully upload file")
    void uploadFile_Success(){
        // Arrange
        String expectedETag = "test-etag";
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedETag));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(expectedETag, result.join());
        verify(uploadStrategyFactory).getStrategy(any());
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when upload fails")
    void uploadFile_UploadFails() {
        // Arrange - ensure ASYNC mode is used
        when(s3Properties.getUploadMode()).thenReturn("ASYNC");
        when(applicationPropertiesService.getPropertyByKey(any())).thenReturn(java.util.Optional.empty());
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new CompletionException("Failed to upload file",
                                S3Exception.builder().message("Upload failed").build())));

        // Act & Assert
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        Exception exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getMessage().contains("Failed to upload file"));
    }

    @Test
    @DisplayName("Should successfully upload file using SYNC mode")
    void uploadFile_SuccessWithSyncMode() {
        // Arrange
        String expectedETag = "sync-test-etag";
        when(s3Properties.getUploadMode()).thenReturn("SYNC");
        when(applicationPropertiesService.getPropertyByKey(any())).thenReturn(java.util.Optional.empty());
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedETag));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(expectedETag, result.join());
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.SYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should use SYNC mode when configured in MongoDB")
    void uploadFile_UseSyncModeFromMongoDB() {
        // Arrange
        String expectedETag = "mongodb-sync-etag";
        it.eng.tools.model.ApplicationProperty property = it.eng.tools.model.ApplicationProperty.Builder.newInstance()
                .key("s3.upload.mode")
                .value("SYNC")
                .build();

        when(applicationPropertiesService.getPropertyByKey("s3.upload.mode"))
                .thenReturn(java.util.Optional.of(property));
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedETag));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(expectedETag, result.join());
        verify(applicationPropertiesService).getPropertyByKey("s3.upload.mode");
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.SYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should use ASYNC mode when configured in MongoDB")
    void uploadFile_UseAsyncModeFromMongoDB() {
        // Arrange
        String expectedETag = "mongodb-async-etag";
        it.eng.tools.model.ApplicationProperty property = it.eng.tools.model.ApplicationProperty.Builder.newInstance()
                .key("s3.upload.mode")
                .value("ASYNC")
                .build();

        when(applicationPropertiesService.getPropertyByKey("s3.upload.mode"))
                .thenReturn(java.util.Optional.of(property));
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedETag));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(expectedETag, result.join());
        verify(applicationPropertiesService).getPropertyByKey("s3.upload.mode");
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.ASYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should fallback to properties when MongoDB property not found")
    void uploadFile_FallbackToPropertiesWhenMongoDBEmpty() {
        // Arrange
        String expectedETag = "properties-etag";
        when(applicationPropertiesService.getPropertyByKey("s3.upload.mode"))
                .thenReturn(java.util.Optional.empty());
        when(s3Properties.getUploadMode()).thenReturn("SYNC");
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedETag));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(expectedETag, result.join());
        verify(applicationPropertiesService).getPropertyByKey("s3.upload.mode");
        verify(s3Properties).getUploadMode();
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.SYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should fallback to properties when MongoDB throws exception")
    void uploadFile_FallbackToPropertiesWhenMongoDBThrowsException() {
        // Arrange
        String expectedETag = "exception-fallback-etag";
        when(applicationPropertiesService.getPropertyByKey("s3.upload.mode"))
                .thenThrow(new RuntimeException("Database connection error"));
        when(s3Properties.getUploadMode()).thenReturn("ASYNC");
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedETag));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(expectedETag, result.join());
        verify(applicationPropertiesService).getPropertyByKey("s3.upload.mode");
        verify(s3Properties).getUploadMode();
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.ASYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should default to SYNC mode when invalid mode configured")
    void uploadFile_DefaultToSyncWhenInvalidMode() {
        // Arrange
        String expectedETag = "default-sync-etag";
        when(s3Properties.getUploadMode()).thenReturn("INVALID_MODE");
        when(applicationPropertiesService.getPropertyByKey(any())).thenReturn(java.util.Optional.empty());
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedETag));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(expectedETag, result.join());
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.SYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when SYNC upload fails")
    void uploadFile_SyncUploadFails() {
        // Arrange
        when(s3Properties.getUploadMode()).thenReturn("SYNC");
        when(applicationPropertiesService.getPropertyByKey(any())).thenReturn(java.util.Optional.empty());
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new CompletionException("Failed to upload file",
                                S3Exception.builder().message("Sync upload failed").build())));

        // Act & Assert
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        Exception exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getMessage().contains("Failed to upload file"));
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.SYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when ASYNC part upload fails")
    void uploadFile_AsyncPartUploadFails() {
        // Arrange
        when(s3Properties.getUploadMode()).thenReturn("ASYNC");
        when(applicationPropertiesService.getPropertyByKey(any())).thenReturn(java.util.Optional.empty());
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("test-etag"));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert - with strategy pattern, upload succeeds
        assertDoesNotThrow(() -> result.join());
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.ASYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when ASYNC complete multipart upload fails")
    void uploadFile_AsyncCompleteMultipartUploadFails() {
        // Arrange
        when(s3Properties.getUploadMode()).thenReturn("ASYNC");
        when(applicationPropertiesService.getPropertyByKey(any())).thenReturn(java.util.Optional.empty());
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new CompletionException("Failed to upload file",
                                S3Exception.builder().message("Complete upload failed").build())));

        // Act & Assert
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        Exception exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getMessage().contains("Failed to upload file"));
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.ASYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle empty upload mode string by defaulting to SYNC")
    void uploadFile_EmptyUploadModeString() {
        // Arrange
        String expectedETag = "empty-mode-etag";
        when(s3Properties.getUploadMode()).thenReturn("");
        when(applicationPropertiesService.getPropertyByKey(any())).thenReturn(java.util.Optional.empty());
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedETag));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(expectedETag, result.join());
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.SYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle null upload mode by defaulting to SYNC")
    void uploadFile_NullUploadMode() {
        // Arrange
        String expectedETag = "null-mode-etag";
        when(s3Properties.getUploadMode()).thenReturn(null);
        when(applicationPropertiesService.getPropertyByKey(any())).thenReturn(java.util.Optional.empty());
        when(mockUploadStrategy.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedETag));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                INPUT_STREAM, DESTINATION_S3_PROPERTIES, CONTENT_TYPE, CONTENT_DISPOSITION);

        // Assert
        assertEquals(expectedETag, result.join());
        verify(uploadStrategyFactory).getStrategy(it.eng.tools.s3.model.S3UploadMode.SYNC);
        verify(mockUploadStrategy).uploadFile(any(), any(), any(), any(), any(), any());
    }

    // downloadFile tests
    @Test
    @DisplayName("Should successfully download file")
    void downloadFile_Success() throws IOException {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        String contentType = "text/plain";
        String contentDisposition = "attachment; filename=test-file.txt";
        byte[] testData = "test content".getBytes();

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);
        when(responseInputStream.read(any(byte[].class)))
                .thenReturn(testData.length)  // First read returns data length
                .thenReturn(-1);             // Second read indicates end of stream
        when(responseInputStream.response()).thenReturn(getObjectResponse);
        when(getObjectResponse.contentType()).thenReturn(contentType);
        when(getObjectResponse.contentDisposition()).thenReturn(contentDisposition);
        when(response.getOutputStream()).thenReturn(outputStream);

        // Act
        assertDoesNotThrow(() -> s3ClientService.downloadFile(bucketName, objectKey, response));

        // Assert
        verify(s3Client).getObject(any(GetObjectRequest.class));
        verify(response).setStatus(HttpStatus.OK.value());
        verify(response).setContentType(contentType);
        verify(response).setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        verify(response).flushBuffer();
        verify(outputStream).write(any(byte[].class), eq(0), eq(testData.length));
        verify(outputStream).flush();
    }

    @Test
    @DisplayName("Should handle file not found")
    void downloadFile_FileNotFound() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "non-existent-file.txt";

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder()
                        .message("The specified key does not exist")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.downloadFile(bucketName, objectKey, response));

        assertTrue(exception.getMessage().contains("File not found"));
        verify(s3Client).getObject(any(GetObjectRequest.class));
        verifyNoInteractions(response);
    }

    @Test
    @DisplayName("Should handle IO exception during download")
    void downloadFile_IOExceptionDuringDownload() throws IOException {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);
        when(responseInputStream.read(any(byte[].class))).thenThrow(new IOException("Read error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.downloadFile(bucketName, objectKey, response));

        assertTrue(exception.getMessage().contains("Error downloading file"));
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("Should handle response output stream error")
    void downloadFile_ResponseOutputStreamError() throws IOException {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);
        when(response.getOutputStream()).thenThrow(new IOException("Output stream error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.downloadFile(bucketName, objectKey, response));

        assertTrue(exception.getMessage().contains("Error downloading file"));
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when bucket name is null")
    void downloadFile_NullBucketName() {
        // Arrange
        String objectKey = "test-file.txt";

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.downloadFile(null, objectKey, response));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3Client, response);
    }

    @Test
    @DisplayName("Should throw RuntimeException when response is null")
    void downloadFile_NullResponse() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.downloadFile(bucketName, objectKey, null));
    }

    // deleteFile tests
    @Test
    @DisplayName("Should successfully delete file when it exists")
    void deleteFile_WhenFileExists() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        // Act
        assertDoesNotThrow(() -> s3ClientService.deleteFile(bucketName, objectKey));

        // Assert
        verify(s3Client).headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
        verify(s3Client).deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
    }

    @Test
    @DisplayName("Should not attempt deletion when file doesn't exist")
    void deleteFile_WhenFileDoesNotExist() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "non-existent-file.txt";

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder()
                        .message("The specified key does not exist")
                        .build());

        // Act
        assertDoesNotThrow(() -> s3ClientService.deleteFile(bucketName, objectKey));

        // Assert
        verify(s3Client).headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when deletion fails")
    void deleteFile_WhenDeletionFails() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Deletion failed")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.deleteFile(bucketName, objectKey));

        assertTrue(exception.getMessage().contains("Error deleting file"));
        verify(s3Client).headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when file check fails")
    void deleteFile_WhenFileCheckFails() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Connection failed")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.deleteFile(bucketName, objectKey));

        assertTrue(exception.getMessage().contains("Error deleting file"));
        verify(s3Client).headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null")
    void deleteFile_WhenBucketNameIsNull() {
        // Arrange
        String objectKey = "test-file.txt";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.deleteFile(null, objectKey));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name format is invalid")
    void deleteFile_WhenBucketNameFormatIsInvalid() {
        // Arrange
        String bucketName = "Invalid.Bucket.Name";
        String objectKey = "test-file.txt";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.deleteFile(bucketName, objectKey));

        assertEquals("Invalid bucket name format: " + bucketName, exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    // fileExists tests
    @Test
    @DisplayName("Should return true when file exists")
    void fileExists_WhenFileExists() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        // Act
        boolean result = s3ClientService.fileExists(bucketName, objectKey);

        // Assert
        assertTrue(result);
        verify(s3Client).headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
    }

    @Test
    @DisplayName("Should return false when file does not exist")
    void fileExists_WhenFileDoesNotExist() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "non-existent-file.txt";

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder()
                        .message("The specified key does not exist")
                        .build());

        // Act
        boolean result = s3ClientService.fileExists(bucketName, objectKey);

        // Assert
        assertFalse(result);
        verify(s3Client).headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
    }

    @Test
    @DisplayName("Should throw RuntimeException when checking file existence fails")
    void fileExists_WhenCheckFails() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Connection failed")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.fileExists(bucketName, objectKey));

        assertTrue(exception.getMessage().contains("Error checking if file exists"));
        verify(s3Client).headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null")
    void fileExists_WhenBucketNameIsNull() {
        // Arrange
        String objectKey = "test-file.txt";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.fileExists(null, objectKey));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name format is invalid")
    void fileExists_WhenBucketNameFormatIsInvalid() {
        // Arrange
        String bucketName = "Invalid.Bucket.Name";
        String objectKey = "test-file.txt";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.fileExists(bucketName, objectKey));

        assertEquals("Invalid bucket name format: " + bucketName, exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    // listFiles tests
    @Test
    @DisplayName("Should successfully list files in bucket")
    void listFiles_Success() {
        // Arrange
        String bucketName = "test-bucket";
        List<S3Object> s3Objects = List.of(
                S3Object.builder().key("file1.txt").build(),
                S3Object.builder().key("file2.txt").build(),
                S3Object.builder().key("file3.txt").build()
        );
        when(s3ClientProvider.adminS3Client()).thenReturn(s3Client);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(s3Objects)
                        .build());

        // Act
        List<String> result = s3ClientService.listFiles(bucketName);

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.containsAll(List.of("file1.txt", "file2.txt", "file3.txt")));
        verify(s3Client).listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build());
    }

    @Test
    @DisplayName("Should return empty list when bucket is empty")
    void listFiles_EmptyBucket() {
        // Arrange
        String bucketName = "empty-bucket";
        when(s3ClientProvider.adminS3Client()).thenReturn(s3Client);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(new ArrayList<>())
                        .build());

        // Act
        List<String> result = s3ClientService.listFiles(bucketName);

        // Assert
        assertTrue(result.isEmpty());
        verify(s3Client).listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build());
    }

    @Test
    @DisplayName("Should throw RuntimeException when listing files fails")
    void listFiles_WhenListingFails() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3ClientProvider.adminS3Client()).thenReturn(s3Client);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(S3Exception.builder()
                        .message("Failed to list objects")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.listFiles(bucketName));

        assertTrue(exception.getMessage().contains("Error listing files"));
        verify(s3Client).listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null")
    void listFiles_WhenBucketNameIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.listFiles(null));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is empty")
    void listFiles_WhenBucketNameIsEmpty() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.listFiles(""));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name format is invalid")
    void listFiles_WhenBucketNameFormatIsInvalid() {
        // Arrange
        String bucketName = "Invalid.Bucket.Name";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.listFiles(bucketName));

        assertEquals("Invalid bucket name format: " + bucketName, exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    //generateGetPresignedUrl tests
    @Test
    @DisplayName("Should successfully generate presigned URL")
    void generateGetPresignedUrl_Success() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);
        String expectedUrl = "https://test-bucket.s3.amazonaws.com/test-file.txt";

        when(s3Properties.getExternalPresignedEndpoint()).thenReturn("https://s3.amazonaws.com");
        BucketCredentialsEntity bucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                .accessKey("accessKey")
                .secretKey("secretKey")
                .bucketName(bucketName)
                .build();
        when(bucketCredentialsService.getBucketCredentials(bucketName)).thenReturn(bucketCredentials);
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType("text/plain")
                        .contentDisposition("attachment; filename=test-file.txt")
                        .build());

        // Act
        String result = s3ClientService.generateGetPresignedUrl(bucketName, objectKey, expiration);

        // Assert
        assertNotNull(result);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when file does not exist")
    void generateGetPresignedUrl_FileNotFound() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "non-existent-file.txt";
        Duration expiration = Duration.ofMinutes(5);
        when(s3Properties.getExternalPresignedEndpoint()).thenReturn("https://s3.testaws.com");
        BucketCredentialsEntity bucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                .accessKey("accessKey")
                .secretKey("secretKey")
                .bucketName(bucketName)
                .build();
        when(bucketCredentialsService.getBucketCredentials(bucketName)).thenReturn(bucketCredentials);
        when(s3Properties.getRegion()).thenReturn("us-east-1");

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder()
                        .message("The specified key does not exist")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.generateGetPresignedUrl(bucketName, objectKey, expiration));

        assertTrue(exception.getMessage().contains("Error generating pre-signed URL"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null")
    void generateGetPresignedUrl_NullBucketName() {
        // Arrange
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.generateGetPresignedUrl(null, objectKey, expiration));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3Client);
    }
}
