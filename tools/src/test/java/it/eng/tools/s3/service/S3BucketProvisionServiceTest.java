package it.eng.tools.s3.service;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3BucketProvisionServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3ClientProvider s3ClientProvider;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private BucketCredentialsService bucketCredentialsService;

    @Mock
    private IamUserManagementService iamUserManagementService;

    private S3BucketProvisionService s3BucketProvisionService;

    @BeforeEach
    void setUp() {
        s3BucketProvisionService = new S3BucketProvisionService(s3ClientProvider, s3Properties,
                bucketCredentialsService, iamUserManagementService);
        lenient().when(s3ClientProvider.adminS3Client()).thenReturn(s3Client);
    }

    @Test
    @DisplayName("createSecureBucket - should create a new bucket with policy and credentials")
    void createSecureBucket_WithNoExistingPolicy_ShouldCreateNewPolicy() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock IAM service calls
        doNothing().when(iamUserManagementService).createUser(any(BucketCredentialsEntity.class));
        doNothing().when(iamUserManagementService).attachPolicyToUser(any(BucketCredentialsEntity.class));

        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());
        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy("{}").build());

        when(bucketCredentialsService.saveBucketCredentials(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BucketCredentialsEntity result = s3BucketProvisionService.createSecureBucket(bucketName);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getAccessKey());
        assertNotNull(result.getSecretKey());
        assertEquals(bucketName, result.getBucketName());
        assertTrue(result.getAccessKey().startsWith("GetBucketUser-"));

        // Verify bucket creation
        verify(s3Client).createBucket(any(CreateBucketRequest.class));

        // Verify policy creation
        ArgumentCaptor<PutBucketPolicyRequest> policyCaptor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(s3Client).putBucketPolicy(policyCaptor.capture());

        String policy = policyCaptor.getValue().policy();
        assertTrue(policy.contains("\"Version\": \"2012-10-17\""));
        assertTrue(policy.contains("\"Effect\": \"Allow\""));
        assertTrue(policy.contains(bucketName));
        assertTrue(policy.contains(result.getAccessKey()));

        // Verify credentials storage
        verify(bucketCredentialsService).saveBucketCredentials(any(BucketCredentialsEntity.class));
    }

    @Test
    @DisplayName("createSecureBucket - should handle when bucket already exists")
    void createSecureBucket_WhenBucketExists_ShouldContinueWithPolicyUpdate() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(BucketAlreadyExistsException.builder().build());

        // Mock IAM service calls
        doNothing().when(iamUserManagementService).createUser(any(BucketCredentialsEntity.class));
        doNothing().when(iamUserManagementService).attachPolicyToUser(any(BucketCredentialsEntity.class));

        when(bucketCredentialsService.saveBucketCredentials(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy("{}").build());
        // Act
        BucketCredentialsEntity result = s3BucketProvisionService.createSecureBucket(bucketName);

        // Assert
        assertNotNull(result);
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
        verify(iamUserManagementService).createUser(any(BucketCredentialsEntity.class));
        verify(iamUserManagementService).attachPolicyToUser(any(BucketCredentialsEntity.class));
        verify(bucketCredentialsService).saveBucketCredentials(any(BucketCredentialsEntity.class));
    }

    @Test
    @DisplayName("createSecureBucket - should throw exception if bucket name is null")
    void createSecureBucket_WithNullBucketName_ShouldThrowException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3BucketProvisionService.createSecureBucket(null));
        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
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
        when(bucketCredentialsService.saveBucketCredentials(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BucketCredentialsEntity result = s3BucketProvisionService.createSecureBucket(bucketName);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getAccessKey());
        assertNotNull(result.getSecretKey());
        assertEquals(bucketName, result.getBucketName());

        // Verify bucket creation
        verify(s3Client).createBucket(any(CreateBucketRequest.class));

        // Verify policy update
        ArgumentCaptor<PutBucketPolicyRequest> policyCaptor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(s3Client).putBucketPolicy(policyCaptor.capture());

        String updatedPolicy = policyCaptor.getValue().policy();
        assertTrue(updatedPolicy.contains("ExistingPolicy")); // Contains existing policy
        assertTrue(updatedPolicy.contains("AllowTemporaryAccess")); // Contains new policy
        assertTrue(updatedPolicy.contains(result.getAccessKey())); // Contains new access key
        assertTrue(updatedPolicy.contains(bucketName));

        // Verify there are two statement entries
        int statementCount = updatedPolicy.split("\"Sid\"").length - 1;
        assertEquals(2, statementCount);

        // Verify credentials storage
        verify(bucketCredentialsService).saveBucketCredentials(any(BucketCredentialsEntity.class));
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
                () -> s3BucketProvisionService.createSecureBucket(bucketName));

        verify(bucketCredentialsService, never()).saveBucketCredentials(any());
    }

    @Test
    @DisplayName("createSecureBucket - should throw exception if policy update fails")
    void createSecureBucket_WhenPolicyUpdateFails_ShouldThrowException() {
        // Arrange
        String bucketName = "test-bucket";
        // Mock IAM service calls first
        doNothing().when(iamUserManagementService).createUser(any(BucketCredentialsEntity.class));
        doThrow(new RuntimeException("Failed to attach policy"))
                .when(iamUserManagementService).attachPolicyToUser(any(BucketCredentialsEntity.class));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3BucketProvisionService.createSecureBucket(bucketName));
        assertEquals("Failed to attach policy", exception.getMessage());

        verify(bucketCredentialsService, never()).saveBucketCredentials(any());
    }

    @Test
    @DisplayName("createSecureBucket - should handle policy update failure with existing policy")
    void createSecureBucket_WhenUpdatingExistingPolicy_ShouldThrowException() {
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

        // Mock IAM service calls
        doNothing().when(iamUserManagementService).createUser(any(BucketCredentialsEntity.class));
        doNothing().when(iamUserManagementService).attachPolicyToUser(any(BucketCredentialsEntity.class));

        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy(existingPolicy).build());
        when(s3Client.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenThrow(S3Exception.builder().message("Failed to update policy").build());

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> s3BucketProvisionService.createSecureBucket(bucketName));

        verify(bucketCredentialsService, never()).saveBucketCredentials(any());
    }

    @Test
    @DisplayName("createSecureBucket - should throw exception if bucket name is empty")
    void createSecureBucket_WithEmptyBucketName_ShouldThrowException() {
        // Arrange
        String bucketName = "";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3BucketProvisionService.createSecureBucket(bucketName));
        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("createSecureBucket - should throw exception if bucket name is invalid")
    void createSecureBucket_WithInvalidBucketName_ShouldThrowException() {
        // Arrange
        String bucketName = "Invalid.Bucket.Name";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3BucketProvisionService.createSecureBucket(bucketName));
        assertEquals("Invalid bucket name format: " + bucketName, exception.getMessage());
    }

    @Test
    @DisplayName("createSecureBucket - should handle policy get failure")
    void createSecureBucket_WhenGetPolicyFails_ShouldCreateNewPolicy() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock IAM service calls
        doNothing().when(iamUserManagementService).createUser(any(BucketCredentialsEntity.class));
        doNothing().when(iamUserManagementService).attachPolicyToUser(any(BucketCredentialsEntity.class));

        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenThrow(S3Exception.builder().message("No policy exists").build());

        when(bucketCredentialsService.saveBucketCredentials(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BucketCredentialsEntity result = s3BucketProvisionService.createSecureBucket(bucketName);

        // Assert
        assertNotNull(result);
        verify(s3Client).putBucketPolicy(any(PutBucketPolicyRequest.class));
        verify(bucketCredentialsService).saveBucketCredentials(any(BucketCredentialsEntity.class));
    }

    @Test
    @DisplayName("createSecureBucket - should create a new bucket with empty policy")
    void createSecureBucket_WithExistingEmptyPolicy_ShouldCreateNewPolicy() {
        // Arrange
        String bucketName = "test-bucket";

        // Mock IAM service calls
        doNothing().when(iamUserManagementService).createUser(any(BucketCredentialsEntity.class));
        doNothing().when(iamUserManagementService).attachPolicyToUser(any(BucketCredentialsEntity.class));

        // Mock bucket creation
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());
        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy("{}").build());

        when(bucketCredentialsService.saveBucketCredentials(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BucketCredentialsEntity result = s3BucketProvisionService.createSecureBucket(bucketName);

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
        s3BucketProvisionService.cleanupBucket(bucketName);

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
        s3BucketProvisionService.cleanupBucket(bucketName);

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
        s3BucketProvisionService.cleanupBucket(bucketName);

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
                () -> s3BucketProvisionService.cleanupBucket(bucketName));

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
                () -> s3BucketProvisionService.cleanupBucket(bucketName));

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
                () -> s3BucketProvisionService.cleanupBucket(bucketName));

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
                () -> s3BucketProvisionService.cleanupBucket(bucketName));
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

        when(bucketCredentialsService.getBucketCredentials(bucketName))
                .thenReturn(credentials);
        when(s3Properties.getEndpoint()).thenReturn("http://localhost:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");

        // Act
        String url = s3BucketProvisionService.generatePresignedUrl(bucketName, objectKey, expiration);

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

        when(bucketCredentialsService.getBucketCredentials(bucketName)).thenThrow(new IllegalArgumentException(
                "No credentials found for bucket: " + bucketName));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3BucketProvisionService.generatePresignedUrl(bucketName, objectKey, expiration));

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

        when(bucketCredentialsService.getBucketCredentials(bucketName))
                .thenReturn(credentials);
        when(s3Properties.getEndpoint()).thenReturn("invalid-endpoint");
        when(s3Properties.getRegion()).thenReturn("us-east-1");

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> s3BucketProvisionService.generatePresignedUrl(bucketName, objectKey, expiration));
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
                () -> s3BucketProvisionService.generatePresignedUrl(bucketName, objectKey, expiration));
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
                () -> s3BucketProvisionService.generatePresignedUrl(bucketName, objectKey, expiration));
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
                () -> s3BucketProvisionService.generatePresignedUrl(bucketName, objectKey, expiration));
        assertEquals("Expiration duration cannot be null", exception.getMessage());
    }

    // bucket exists
    //bucketExists test cases
    @Test
    @DisplayName("Should return true when bucket exists")
    void bucketExists_WhenBucketExists() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3ClientProvider.adminS3Client()).thenReturn(s3Client);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        // Act
        boolean result = s3BucketProvisionService.bucketExists(bucketName);

        // Assert
        assertTrue(result);
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("Should return false when bucket does not exist")
    void bucketExists_WhenBucketDoesNotExist() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3ClientProvider.adminS3Client()).thenReturn(s3Client);

        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder()
                        .message("The specified bucket does not exist")
                        .build());

        // Act
        boolean result = s3BucketProvisionService.bucketExists(bucketName);

        // Assert
        assertFalse(result);
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when checking bucket existence fails")
    void bucketExists_WhenCheckFails() {
        // Arrange
        String bucketName = "test-bucket";
        when(s3ClientProvider.adminS3Client()).thenReturn(s3Client);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Connection timeout")
                        .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> s3BucketProvisionService.bucketExists(bucketName));

        assertTrue(exception.getMessage().contains("Error checking if bucket exists"));
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is null")
    void bucketExists_WhenBucketNameIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3BucketProvisionService.bucketExists(null));

        assertEquals("Bucket name cannot be null or empty", exception.getMessage());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when bucket name is empty")
    void bucketExists_WhenBucketNameIsEmpty() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> s3BucketProvisionService.bucketExists(""));

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
                () -> s3BucketProvisionService.bucketExists(bucketName));

        assertEquals("Invalid bucket name format: " + bucketName, exception.getMessage());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
    }


}
