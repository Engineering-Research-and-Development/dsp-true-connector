package it.eng.tools.s3.service;

import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.repository.BucketCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3BucketServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private BucketCredentialsRepository bucketCredentialsRepository;

    private S3BucketService s3BucketService;

    @BeforeEach
    void setUp() {
        s3BucketService = new S3BucketService(s3Client, s3Properties, bucketCredentialsRepository);
    }

    @Test
    @DisplayName("createSecureBucket - should create a new bucket with policy and credentials")
    void createSecureBucket_WithNoExistingPolicy_ShouldCreateNewPolicy() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenThrow(AwsServiceException.class);
        when(bucketCredentialsRepository.save(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BucketCredentials result = s3BucketService.createSecureBucket(bucketName);

        // Assert
        assertNotNull(result);
        assertNotNull(result.accessKey());
        assertNotNull(result.secretKey());
        assertEquals(bucketName, result.bucketName());
        assertTrue(result.accessKey().startsWith("GetBucketUser-"));

        // Verify bucket creation
        verify(s3Client).createBucket(any(CreateBucketRequest.class));

        // Verify policy creation
        ArgumentCaptor<PutBucketPolicyRequest> policyCaptor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(s3Client).putBucketPolicy(policyCaptor.capture());

        String policy = policyCaptor.getValue().policy();
        assertTrue(policy.contains("\"Version\": \"2012-10-17\""));
        assertTrue(policy.contains("\"Effect\": \"Allow\""));
        assertTrue(policy.contains(bucketName));
        assertTrue(policy.contains(result.accessKey()));

        // Verify credentials storage
        verify(bucketCredentialsRepository).save(any(BucketCredentialsEntity.class));
    }

    @Test
    @DisplayName("createSecureBucket - should append new policy to existing policy")
    void createSecureBucket_WithExistingPolicy_ShouldAppendNewPolicy() {
        // Arrange
        String bucketName = "test-bucket";
        String existingPolicy = """
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Sid": "ExistingPolicy",
                            "Effect": "Allow",
                            "Principal": {"AWS": ["existing-user"]},
                            "Action": ["s3:GetObject"],
                            "Resource": ["arn:aws:s3:::test-bucket/*"]
                        }
                    ]
                }""";

        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy(existingPolicy).build());
        when(bucketCredentialsRepository.save(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BucketCredentials result = s3BucketService.createSecureBucket(bucketName);

        // Assert
        assertNotNull(result);
        assertNotNull(result.accessKey());
        assertNotNull(result.secretKey());
        assertEquals(bucketName, result.bucketName());

        // Verify bucket creation
        verify(s3Client).createBucket(any(CreateBucketRequest.class));

        // Verify policy update
        ArgumentCaptor<PutBucketPolicyRequest> policyCaptor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(s3Client).putBucketPolicy(policyCaptor.capture());

        String updatedPolicy = policyCaptor.getValue().policy();
        assertTrue(updatedPolicy.contains("ExistingPolicy")); // Contains existing policy
        assertTrue(updatedPolicy.contains("AllowTemporaryAccess")); // Contains new policy
        assertTrue(updatedPolicy.contains(result.accessKey())); // Contains new access key
        assertTrue(updatedPolicy.contains(bucketName));

        // Verify there are two statement entries
        int statementCount = updatedPolicy.split("\"Sid\"").length - 1;
        assertEquals(2, statementCount);

        // Verify credentials storage
        verify(bucketCredentialsRepository).save(any(BucketCredentialsEntity.class));
    }

    @Test
    @DisplayName("createSecureBucket - should throw exception if bucket name is invalid")
    void createSecureBucket_WhenBucketCreationFails_ShouldThrowException() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(S3Exception.builder().message("Bucket creation failed").build());

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> s3BucketService.createSecureBucket(bucketName));

        verify(bucketCredentialsRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSecureBucket - should throw exception if policy update fails")
    void createSecureBucket_WhenPolicyUpdateFails_ShouldThrowException() {
        // Arrange
        String bucketName = "test-bucket";
        // MinIO returns {} when no policy exists, while AWS throws NoSuchBucketPolicyException
        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder()
                        .policy("{}")
                        .build());
        when(s3Client.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Policy update failed")
                        .build());

        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3BucketService.createSecureBucket(bucketName));
        assertEquals("Failed to update bucket policy", exception.getMessage());

        verify(s3Client).createBucket(any(CreateBucketRequest.class));
        verify(s3Client).getBucketPolicy(any(GetBucketPolicyRequest.class));
        verify(s3Client).putBucketPolicy(any(PutBucketPolicyRequest.class));
        verify(bucketCredentialsRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSecureBucket - should throw exception if bucket name is empty")
    void createSecureBucket_WithEmptyBucketName_ShouldThrowException() {
        // Arrange
        String bucketName = "";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3BucketService.createSecureBucket(bucketName));
        assertEquals("Bucket name cannot be empty", exception.getMessage());
    }

    @Test
    @DisplayName("createSecureBucket - should throw exception if bucket name is invalid")
    void createSecureBucket_WithInvalidBucketName_ShouldThrowException() {
        // Arrange
        String bucketName = "Invalid.Bucket.Name";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3BucketService.createSecureBucket(bucketName));
        assertEquals("Invalid bucket name format", exception.getMessage());
    }

    @Test
    @DisplayName("createSecureBucket - should create a new bucket with empty policy")
    void createSecureBucket_WithExistingEmptyPolicy_ShouldCreateNewPolicy() {
        // Arrange
        String bucketName = "test-bucket";
        String emptyPolicy = """
                {
                    "Version": "2012-10-17",
                    "Statement": []
                }""";

        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy(emptyPolicy).build());
        when(bucketCredentialsRepository.save(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BucketCredentials result = s3BucketService.createSecureBucket(bucketName);

        // Assert
        assertNotNull(result);
        ArgumentCaptor<PutBucketPolicyRequest> policyCaptor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(s3Client).putBucketPolicy(policyCaptor.capture());
        String updatedPolicy = policyCaptor.getValue().policy();
        assertTrue(updatedPolicy.contains("AllowTemporaryAccess"));
        assertEquals(1, updatedPolicy.split("\"Sid\"").length - 1);
    }

    // cleanup bucket

    @Test
    @DisplayName("cleanupBucket - should delete all files and bucket policy")
    void cleanupBucket_WithFilesAndPolicy_ShouldDeleteEverything() {
        // Arrange
        String bucketName = "test-bucket";
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("file1.txt").build(),
                        S3Object.builder().key("file2.txt").build()
                )
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(listResponse);

        // Act
        s3BucketService.cleanupBucket(bucketName);

        // Assert
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
        verify(s3Client).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3Client).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("cleanupBucket - should delete bucket and policy when no files exist")
    void cleanupBucket_WithEmptyBucket_ShouldDeleteBucketAndPolicy() {
        // Arrange
        String bucketName = "test-bucket";
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
                .contents(Collections.emptyList())  // Empty list
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(listResponse);

        // Act
        s3BucketService.cleanupBucket(bucketName);

        // Assert
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(s3Client).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3Client).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("cleanupBucket - should handle empty bucket policy gracefully")
    void cleanupBucket_WithPaginatedResults_ShouldDeleteAllFiles() {
        // Arrange
        String bucketName = "test-bucket";
        ListObjectsV2Response firstPage = ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("file1.txt").build())
                .isTruncated(true)
                .nextContinuationToken("token")
                .build();

        ListObjectsV2Response secondPage = ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("file2.txt").build())
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(firstPage)
                .thenReturn(secondPage);

        // Act
        s3BucketService.cleanupBucket(bucketName);

        // Assert
        verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
        verify(s3Client).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3Client).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("cleanupBucket - should throw exeption with non existent bucket")
    void cleanupBucket_WhenBucketNotFound_ShouldThrowException() {
        // Arrange
        String bucketName = "non-existent-bucket";
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(NoSuchBucketException.class);

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> s3BucketService.cleanupBucket(bucketName));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(s3Client, never()).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3Client, never()).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("cleanupBucket - should throw exception when insufficient permissions")
    void cleanupBucket_WithInsufficientPermissions_ShouldThrowException() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("file1.txt").build())
                        .isTruncated(false)
                        .build());

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Access Denied")
                        .statusCode(403)
                        .build());

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> s3BucketService.cleanupBucket(bucketName));

        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(s3Client, never()).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3Client, never()).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("cleanupBucket - should thwo exception when bucket policy does not exist")
    void cleanupBucket_WhenPolicyDeletionFails_ShouldStillTryToDeleteBucket() {
        // Arrange
        String bucketName = "test-bucket";
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
                .contents(Collections.emptyList())  // Empty list
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(listResponse);
        when(s3Client.deleteBucketPolicy(any(DeleteBucketPolicyRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("No policy exists")
                        .statusCode(404)
                        .build());

        // Act
        assertThrows(RuntimeException.class,
                () -> s3BucketService.cleanupBucket(bucketName));

        // Assert
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3Client, never()).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("cleanupBucket - should handle concurrent modification gracefully")
    void cleanupBucket_WithConcurrentModification_ShouldHandleGracefully() {
        // Arrange
        String bucketName = "test-bucket";
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("file1.txt").build())
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(listResponse);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Object does not exist")
                        .statusCode(404)
                        .build());

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> s3BucketService.cleanupBucket(bucketName));
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, times(0)).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3Client, times(0)).deleteBucket(any(DeleteBucketRequest.class));
    }

    // generate presigned url
    @Test
    @DisplayName("generatePresignedUrl - should generate a valid presigned URL")
    void generatePresignedUrl_Success() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);
        BucketCredentialsEntity credentials = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(bucketName)
                .accessKey("testKey")
                .secretKey("testSecret")
                .build();

        when(bucketCredentialsRepository.findByBucketName(bucketName))
                .thenReturn(Optional.of(credentials));
        when(s3Properties.getEndpoint()).thenReturn("http://localhost:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");

        // Act
        String url = s3BucketService.generatePresignedUrl(bucketName, objectKey, expiration);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains(bucketName));
        assertTrue(url.contains(objectKey));
    }

    @Test
    @DisplayName("generatePresignedUrl - should throw exception when bucket credentials are not found")
    void generatePresignedUrl_WhenCredentialsNotFound_ShouldThrowException() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);

        when(bucketCredentialsRepository.findByBucketName(bucketName))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3BucketService.generatePresignedUrl(bucketName, objectKey, expiration));

        assertEquals("No credentials found for bucket: " + bucketName, exception.getMessage());
    }

    @Test
    @DisplayName("generatePresignedUrl - should throw exception when S3 properties are not configured")
    void generatePresignedUrl_WhenPresignerFails_ShouldThrowException() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofMinutes(5);
        BucketCredentialsEntity credentials = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(bucketName)
                .accessKey("testKey")
                .secretKey("testSecret")
                .build();

        when(bucketCredentialsRepository.findByBucketName(bucketName))
                .thenReturn(Optional.of(credentials));
        when(s3Properties.getEndpoint()).thenReturn("invalid-endpoint");
        when(s3Properties.getRegion()).thenReturn("us-east-1");

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> s3BucketService.generatePresignedUrl(bucketName, objectKey, expiration));
    }

    @Test
    @DisplayName("generatePresignedUrl - should throw exception if expiration is too long")
    void generatePresignedUrl_WithLongExpiration_ShouldThrowException() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        Duration expiration = Duration.ofDays(8); // AWS maximum is 7 days
        BucketCredentialsEntity credentials = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(bucketName)
                .accessKey("testKey")
                .secretKey("testSecret")
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3BucketService.generatePresignedUrl(bucketName, objectKey, expiration));
        assertEquals("Expiration duration cannot exceed 7 days", exception.getMessage());
    }

    @Test
    @DisplayName("generatePresignedUrl - should throw exception if object name is empty")
    void generatePresignedUrl_WithEmptyObjectKey_ShouldThrowException() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "";
        Duration expiration = Duration.ofMinutes(5);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3BucketService.generatePresignedUrl(bucketName, objectKey, expiration));
        assertEquals("Object key cannot be empty", exception.getMessage());
    }

    @Test
    @DisplayName("generatePresignedUrl - should throw exception if expiration is null")
    void generatePresignedUrl_WithNullExpiration_ShouldThrowException() {
        // Arrange
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        Duration expiration = null;

        // Act & Assert
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> s3BucketService.generatePresignedUrl(bucketName, objectKey, expiration));
        assertEquals("Expiration duration cannot be null", exception.getMessage());
    }

}
