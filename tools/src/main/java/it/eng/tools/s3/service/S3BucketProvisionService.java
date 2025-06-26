package it.eng.tools.s3.service;

import it.eng.tools.s3.configuration.S3ClientProvider;
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
public class S3BucketProvisionService {
    private final S3ClientProvider s3ClientProvider;
    private final S3Properties s3Properties;
    private final BucketCredentialsRepository bucketCredentialsRepository;
    private final IamUserManagementService iamUserManagementService;

    public S3BucketProvisionService(S3ClientProvider s3ClientProvider, S3Properties s3Properties,
                                    BucketCredentialsRepository bucketCredentialsRepository,
                                    IamUserManagementService iamUserManagementService) {
        this.s3ClientProvider = s3ClientProvider;
        this.s3Properties = s3Properties;
        this.bucketCredentialsRepository = bucketCredentialsRepository;
        this.iamUserManagementService = iamUserManagementService;
    }

    public BucketCredentialsEntity createSecureBucket(String bucketName) {
        validateBucketName(bucketName);
        log.info("Create secure bucket {}", bucketName);
        // Generate temporary credentials
        String accessKey = "GetBucketUser-" + UUID.randomUUID().toString().substring(0, 8);
        String secretKey = UUID.randomUUID().toString();

        BucketCredentialsEntity bucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(bucketName)
                .accessKey(accessKey)
                .secretKey(secretKey)
                .build();

        iamUserManagementService.createUser(bucketCredentials);
        iamUserManagementService.attachPolicyToUser(bucketCredentials);

        // Create bucket
        createBucket(bucketName);

        // Attach bucket policy
        updateBucketPolicy(bucketName, accessKey);

        // Store credentials
        return bucketCredentialsRepository.save(bucketCredentials);
    }

    private void createBucket(String bucketName) {
        try {
            s3ClientProvider.adminS3Client().createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        } catch (BucketAlreadyExistsException e) {
            log.warn("Bucket {} already exists", bucketName);
        }
    }

    private void attachBucketPolicy(String bucketName, String accessKey) {
        String policy = String.format("""
                {
                    "Version": "2012-10-17",
                    "Sid": "AllowTemporaryAccess-%s",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Principal": {
                                "AWS": ["arn:aws:iam::*:user/%s"]
                            },
                            "Action": [
                                "s3:GetObject",
                                "s3:PutObject",
                                "s3:DeleteObject"
                            ],
                            "Resource": ["arn:aws:s3:::%s/*"]
                        }
                    ]
                }
                """, UUID.randomUUID().toString().substring(0, 8), accessKey, bucketName);

        s3ClientProvider.adminS3Client().putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(bucketName)
                .policy(policy)
                .build());
    }

    private void updateBucketPolicy(String bucketName, String accessKey) {
        try {
            S3Client s3ClientAdmin = s3ClientProvider.adminS3Client();
            // Try to get existing policy
            GetBucketPolicyRequest getPolicyRequest = GetBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .build();

            String existingPolicy = null;
            try {
                String policy = s3ClientAdmin.getBucketPolicy(getPolicyRequest).policy();
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
                            "AWS": ["arn:aws:iam::*:user/%s"]
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
            s3ClientAdmin.putBucketPolicy(policyRequest);
            log.info("Update secure bucket {} whit policy", bucketName);
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

            S3Client s3ClientAdmin = s3ClientProvider.adminS3Client();
            do {
                listResponse = s3ClientAdmin.listObjectsV2(listRequest);
                for (S3Object object : listResponse.contents()) {
                    DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(object.key())
                            .build();
                    s3ClientAdmin.deleteObject(deleteRequest);
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
            s3ClientAdmin.deleteBucketPolicy(deletePolicyRequest);

            // Delete bucket
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3ClientAdmin.deleteBucket(deleteBucketRequest);
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
