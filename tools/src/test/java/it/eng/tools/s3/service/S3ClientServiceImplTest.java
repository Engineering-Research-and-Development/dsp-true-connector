package it.eng.tools.s3.service;

import it.eng.tools.s3.properties.S3Properties;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ClientServiceImplTest {

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
    private GetObjectResponse getObjectResponse;

    @InjectMocks
    private S3ClientServiceImpl s3ClientService;

    @Test
    @DisplayName("Should create bucket when it doesn't exist")
    void createBucket_WhenBucketDoesNotExist() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock bucket check - bucket doesn't exist
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("Bucket does not exist").build());

        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());

        // Act
        assertDoesNotThrow(() -> s3ClientService.createBucket(bucketName));

        // Assert
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("Should not create bucket when it already exists")
    void createBucket_WhenBucketExists() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock bucket check - bucket exists
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        // Act
        assertDoesNotThrow(() -> s3ClientService.createBucket(bucketName));

        // Assert
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when bucket creation fails")
    void createBucket_WhenCreationFails() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock bucket check - bucket doesn't exist
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("Bucket does not exist").build());

        // Mock creation failure
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(S3Exception.builder().message("Creation failed").build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.createBucket(bucketName));

        assertTrue(exception.getMessage().contains("Error creating bucket"));
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when bucket existence check fails")
    void createBucket_WhenExistenceCheckFails() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock bucket check failure
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().message("Connection failed").build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.createBucket(bucketName));

        assertTrue(exception.getMessage().contains("Error creating bucket"));
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null")
    void createBucket_WhenBucketNameIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.createBucket(null));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is empty")
    void createBucket_WhenBucketNameIsEmpty() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.createBucket(""));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    // deleteBucket tests
    @Test
    @DisplayName("Should delete bucket when it exists")
    void deleteBucket_WhenBucketExists() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock bucket check - bucket exists
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        when(s3Client.deleteBucket(any(DeleteBucketRequest.class)))
                .thenReturn(DeleteBucketResponse.builder().build());

        // Act
        assertDoesNotThrow(() -> s3ClientService.deleteBucket(bucketName));

        // Assert
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("Should not attempt to delete bucket when it doesn't exist")
    void deleteBucket_WhenBucketDoesNotExist() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock bucket check - bucket doesn't exist
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder()
                        .message("Bucket does not exist")
                        .build());

        // Act
        assertDoesNotThrow(() -> s3ClientService.deleteBucket(bucketName));

        // Assert
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when bucket deletion fails")
    void deleteBucket_WhenDeletionFails() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock bucket check - bucket exists
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        // Mock deletion failure
        when(s3Client.deleteBucket(any(DeleteBucketRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Deletion failed")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.deleteBucket(bucketName));

        assertTrue(exception.getMessage().contains("Error deleting bucket"));
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when bucket check fails with unexpected error")
    void deleteBucket_WhenBucketCheckFails() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock bucket check failure
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Connection failed")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.deleteBucket(bucketName));

        assertTrue(exception.getMessage().contains("Error deleting bucket"));
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null")
    void deleteBucket_WhenBucketNameIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.deleteBucket(null));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is empty")
    void deleteBucket_WhenBucketNameIsEmpty() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.deleteBucket(""));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name format is invalid")
    void deleteBucket_WhenBucketNameFormatIsInvalid() {
        // Arrange
        String bucketName = "Invalid.Bucket.Name";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.deleteBucket(bucketName));

        assertEquals("Invalid bucket name format: " + bucketName, exception.getMessage());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).deleteBucket(any(DeleteBucketRequest.class));
    }

    //bucketExists test cases
    @Test
    @DisplayName("Should return true when bucket exists")
    void bucketExists_WhenBucketExists() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        // Act
        boolean result = s3ClientService.bucketExists(bucketName);

        // Assert
        assertTrue(result);
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("Should return false when bucket does not exist")
    void bucketExists_WhenBucketDoesNotExist() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder()
                        .message("The specified bucket does not exist")
                        .build());

        // Act
        boolean result = s3ClientService.bucketExists(bucketName);

        // Assert
        assertFalse(result);
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when checking bucket existence fails")
    void bucketExists_WhenCheckFails() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Connection timeout")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.bucketExists(bucketName));

        assertTrue(exception.getMessage().contains("Error checking if bucket exists"));
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null")
    void bucketExists_WhenBucketNameIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.bucketExists(null));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is empty")
    void bucketExists_WhenBucketNameIsEmpty() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.bucketExists(""));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name format is invalid")
    void bucketExists_WhenBucketNameFormatIsInvalid() {
        // Arrange
        String bucketName = "Invalid.Bucket.Name";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.bucketExists(bucketName));

        assertEquals("Invalid bucket name format: " + bucketName, exception.getMessage());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
    }

    // uploadFile tests
    @Test
    @DisplayName("Should successfully upload file")
    void uploadFile_Success() {
        // Arrange
        String bucketName = "test-bucket";
        String key = "test-file.txt";
        String contentType = "text/plain";
        String contentDisposition = "attachment; filename=test-file.txt";
        String expectedETag = "test-etag";
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        when(s3Properties.getBucketName()).thenReturn(bucketName);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CreateMultipartUploadResponse.builder().uploadId("test-upload-id").build()));
        when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        UploadPartResponse.builder().eTag(expectedETag).build()));
        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        CompleteMultipartUploadResponse.builder().eTag(expectedETag).build()));

        // Act
        CompletableFuture<String> result = s3ClientService.uploadFile(
                inputStream, bucketName, key, contentType, contentDisposition);

        // Assert
        assertEquals(expectedETag, result.join());
        verify(s3AsyncClient).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3AsyncClient).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("Should throw exception when upload fails")
    void uploadFile_UploadFails() {
        // Arrange
        String bucketName = "test-bucket";
        String key = "test-file.txt";
        String contentType = "text/plain";
        String contentDisposition = "attachment; filename=test-file.txt";
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        when(s3Properties.getBucketName()).thenReturn(bucketName);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        S3Exception.builder().message("Upload failed").build()));

        // Act & Assert
        CompletableFuture<String> result = s3ClientService.uploadFile(
                inputStream, bucketName, key, contentType, contentDisposition);

        Exception exception = assertThrows(CompletionException.class, result::join);
        assertTrue(exception.getMessage().contains("Upload failed"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null")
    void uploadFile_NullBucketName() {
        // Arrange
        String key = "test-file.txt";
        String contentType = "text/plain";
        String contentDisposition = "attachment; filename=test-file.txt";
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.uploadFile(inputStream, null, key, contentType, contentDisposition));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3AsyncClient);
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
        assertThrows(RuntimeException.class,
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
    void generatePresignedGETUrl_Success() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);
        String expectedUrl = "https://s3.amazonaws.com/test-bucket/test-file.txt";

        when(s3Properties.getExternalPresignedEndpoint()).thenReturn("https://s3.amazonaws.com");
        when(s3Properties.getAccessKey()).thenReturn("testAccessKey");
        when(s3Properties.getSecretKey()).thenReturn("testSecretKey");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType("text/plain")
                        .contentDisposition("attachment; filename=test-file.txt")
                        .build());

        // Act
        String result = s3ClientService.generatePresignedGETUrl(bucketName, objectKey, expiration);

        // Assert
        assertNotNull(result);
        assertTrue(StringUtils.startsWith(result,expectedUrl));
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when object key is null")
    void generatePresignedGETUrl_NullObjectKey() {
        // Arrange
        String bucketName = "test-bucket";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.generatePresignedGETUrl(bucketName, null, expiration));

        assertEquals("Object key cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when object key is empty")
    void generatePresignedGETUrl_EmptyObjectKey() {
        // Arrange
        String bucketName = "test-bucket";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.generatePresignedGETUrl(bucketName, "", expiration));

        assertEquals("Object key cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Should throw RuntimeException when expiration is null")
    void generatePresignedGETUrl_NullExpiration() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        when(s3Properties.getExternalPresignedEndpoint()).thenReturn("https://s3.amazonaws.com");
        when(s3Properties.getAccessKey()).thenReturn("testAccessKey");
        when(s3Properties.getSecretKey()).thenReturn("testSecretKey");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType("text/plain")
                        .contentDisposition("attachment; filename=test-file.txt")
                        .build());

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> s3ClientService.generatePresignedGETUrl(bucketName, objectKey, null));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name format is invalid")
    void generatePresignedGETUrl_InvalidBucketName() {
        // Arrange
        String bucketName = "Invalid.Bucket.Name";
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.generatePresignedGETUrl(bucketName, objectKey, expiration));

        assertEquals("Invalid bucket name format: " + bucketName, exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    // generatePresignedPUTUrl tests
    @Test
    @DisplayName("Should successfully generate presigned PUT URL")
    void generatePresignedPUTUrl_Success() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);

        when(s3Properties.getExternalPresignedEndpoint()).thenReturn("https://s3.amazonaws.com");
        when(s3Properties.getAccessKey()).thenReturn("testAccessKey");
        when(s3Properties.getSecretKey()).thenReturn("testSecretKey");
        when(s3Properties.getRegion()).thenReturn("us-east-1");

        // Act
        String result = s3ClientService.generatePresignedPUTUrl(bucketName, objectKey, expiration);

        // Assert
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null for PUT URL")
    void generatePresignedPUTUrl_NullBucketName() {
        // Arrange
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.generatePresignedPUTUrl(null, objectKey, expiration));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when object key is null for PUT URL")
    void generatePresignedPUTUrl_NullObjectKey() {
        // Arrange
        String bucketName = "test-bucket";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.generatePresignedPUTUrl(bucketName, null, expiration));

        assertEquals("Object key cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when object key is empty for PUT URL")
    void generatePresignedPUTUrl_EmptyObjectKey() {
        // Arrange
        String bucketName = "test-bucket";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.generatePresignedPUTUrl(bucketName, "", expiration));

        assertEquals("Object key cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw RuntimeException when generating PUT URL fails")
    void generatePresignedPUTUrl_GenerationFails() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.generatePresignedPUTUrl(bucketName, objectKey, expiration));

        assertTrue(exception.getMessage().contains("Error generating pre-signed PUT URL"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name format is invalid for PUT URL")
    void generatePresignedPUTUrl_InvalidBucketName() {
        // Arrange
        String bucketName = "Invalid.Bucket.Name";
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.generatePresignedPUTUrl(bucketName, objectKey, expiration));

        assertEquals("Invalid bucket name format: " + bucketName, exception.getMessage());
        verifyNoInteractions(s3Client);
    }
    @Test
    @DisplayName("Should throw RuntimeException when file does not exist")
    void generatePresignedGETUrl_FileNotFound() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "non-existent-file.txt";
        Duration expiration = Duration.ofMinutes(5);
        when(s3Properties.getExternalPresignedEndpoint()).thenReturn("https://s3.testaws.com");
        when(s3Properties.getAccessKey()).thenReturn("testAccessKey");
        when(s3Properties.getSecretKey()).thenReturn("testSecretKey");
        when(s3Properties.getRegion()).thenReturn("us-east-1");

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder()
                        .message("The specified key does not exist")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3ClientService.generatePresignedGETUrl(bucketName, objectKey, expiration));

        assertTrue(exception.getMessage().contains("Error generating pre-signed URL"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null")
    void generatePresignedGETUrl_NullBucketName() {
        // Arrange
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3ClientService.generatePresignedGETUrl(null, objectKey, expiration));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verifyNoInteractions(s3Client);
    }
}
