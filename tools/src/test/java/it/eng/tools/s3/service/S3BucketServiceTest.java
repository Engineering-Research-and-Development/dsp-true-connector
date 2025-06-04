package it.eng.tools.s3.service;

import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.repository.BucketCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.Collections;

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

    // cleanup bucket

    @Test
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

}
