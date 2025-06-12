package it.eng.tools.s3.service;

import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.repository.BucketCredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class S3BucketService {
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final BucketCredentialsRepository bucketCredentialsRepository;

    public S3BucketService(S3Client s3Client, S3Properties s3Properties, BucketCredentialsRepository bucketCredentialsRepository) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.bucketCredentialsRepository = bucketCredentialsRepository;
    }

    public BucketCredentials createSecureBucket(String bucketName) {
        validateBucketName(bucketName);
        // Generate temporary credentials
        String accessKey = "GetBucketUser-" + UUID.randomUUID().toString().substring(0, 8);
        String secretKey = UUID.randomUUID().toString();

        // Create bucket
        CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();
        s3Client.createBucket(createBucketRequest);

        // Update bucket policy while preserving existing policies
        updateBucketPolicy(bucketName, accessKey);

        // Store credentials
        bucketCredentialsRepository.save(BucketCredentialsEntity.Builder.newInstance()
                .bucketName(bucketName)
                .accessKey(accessKey)
                .secretKey(secretKey)
                .build());

        return new BucketCredentials(accessKey, secretKey, bucketName);
    }

    private void updateBucketPolicy(String bucketName, String accessKey) {
        try {
            // Try to get existing policy
            GetBucketPolicyRequest getPolicyRequest = GetBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .build();

            String existingPolicy = null;
            try {
                String policy = s3Client.getBucketPolicy(getPolicyRequest).policy();
                // Treat empty JSON object as no policy for MinIO compatibility
                if (!policy.equals("{}")) {
                    existingPolicy = policy;
                }
            } catch (AwsServiceException e) {
                // AWS throws exception when no policy exists
                log.info("No existing policy found for bucket: {}", bucketName);
            }

            String newStatement = String.format("""
                    {
                        "Sid": "AllowTemporaryAccess-%s",
                        "Effect": "Allow",
                        "Principal": {
                            "AWS": ["%s"]
                        },
                        "Action": [
                            "s3:GetObject",
                            "s3:PutObject"
                        ],
                        "Resource": [
                            "arn:aws:s3:::%s/*"
                        ]
                    }
                    """, UUID.randomUUID().toString().substring(0, 8), accessKey, bucketName);

            String finalPolicy;
            if (existingPolicy != null && !existingPolicy.isEmpty()) {
                // Extract existing statements and combine with new statement
                int statementsStart = existingPolicy.indexOf("\"Statement\"");
                int statementsArrayStart = existingPolicy.indexOf("[", statementsStart);
                int statementsArrayEnd = existingPolicy.lastIndexOf("]");

                String existingStatements = existingPolicy.substring(statementsArrayStart + 1, statementsArrayEnd);

                finalPolicy = String.format("""
                                {
                                    "Version": "2012-10-17",
                                    "Statement": [
                                        %s%s%s
                                    ]
                                }
                                """,
                        existingStatements,
                        existingStatements.trim().isEmpty() ? "" : ",",
                        newStatement);
            } else {
                // Create new policy with single statement
                finalPolicy = String.format("""
                        {
                            "Version": "2012-10-17",
                            "Statement": [
                                %s
                            ]
                        }
                        """, newStatement);
            }

            // Apply updated policy
            PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(finalPolicy)
                    .build();
            s3Client.putBucketPolicy(policyRequest);

        } catch (Exception e) {
            log.error("Failed to update bucket policy for bucket: {}", bucketName, e);
            throw new RuntimeException("Failed to update bucket policy", e);
        }
    }

    public String generatePresignedUrl(String bucketName, String objectKey,
                                       Duration expiration) {

        validatePresignedUrlParams(objectKey, expiration);
        BucketCredentialsEntity credentials = bucketCredentialsRepository.findByBucketName(bucketName)
                .orElseThrow(() -> new RuntimeException("No credentials found for bucket: " + bucketName));

        return generatePresignedUrl(bucketName, objectKey,
                credentials.getAccessKey(),
                credentials.getSecretKey(),
                expiration);
    }

    private String generatePresignedUrl(String bucketName, String objectKey,
                                        String accessKey, String secretKey,
                                        Duration expiration) {

        try (S3Presigner presigner = S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .endpointOverride(URI.create(s3Properties.getEndpoint()))
                .region(Region.of(s3Properties.getRegion()))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            return presigner.presignGetObject(presignRequest)
                    .url()
                    .toString();
        }
    }

    public void cleanupBucket(String bucketName) {
        try {
            // Delete all objects
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();
            ListObjectsV2Response listResponse;
            do {
                listResponse = s3Client.listObjectsV2(listRequest);
                for (S3Object object : listResponse.contents()) {
                    DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(object.key())
                            .build();
                    s3Client.deleteObject(deleteRequest);
                }
                listRequest = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .continuationToken(listResponse.nextContinuationToken())
                        .build();
            } while (listResponse.isTruncated());

            // Delete bucket policy
            DeleteBucketPolicyRequest deletePolicyRequest = DeleteBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.deleteBucketPolicy(deletePolicyRequest);

            // Delete bucket
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.deleteBucket(deleteBucketRequest);
        } catch (Exception e) {
            log.error("Failed to cleanup bucket: {}", bucketName, e);
            throw new RuntimeException("Failed to cleanup bucket", e);
        }
    }

    private void validateBucketName(String bucketName) {
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalArgumentException("Bucket name cannot be empty");
        }
        if (!bucketName.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$")) {
            throw new IllegalArgumentException("Invalid bucket name format");
        }
    }

    private void validatePresignedUrlParams(String objectKey, Duration expiration) {
        if (objectKey == null || objectKey.isEmpty()) {
            throw new IllegalArgumentException("Object key cannot be empty");
        }
        if (expiration == null) {
            throw new NullPointerException("Expiration duration cannot be null");
        }
        if (expiration.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Expiration duration cannot exceed 7 days");
        }
    }
}
