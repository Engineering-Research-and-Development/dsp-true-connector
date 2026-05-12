package it.eng.dataplane.s3.service;

import it.eng.dataplane.s3.configuration.S3ClientProvider;
import it.eng.dataplane.s3.exception.S3ServerException;
import it.eng.dataplane.s3.model.BucketCredentialsEntity;
import it.eng.dataplane.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for provisioning S3 buckets and managing their credentials.
 */
@Service
@Slf4j
public class S3BucketProvisionService {

    private final S3ClientProvider s3ClientProvider;
    private final S3Properties s3Properties;
    private final BucketCredentialsService bucketCredentialsService;
    private final IamUserManagementService iamUserManagementService;

    /**
     * Constructs the service.
     *
     * @param s3ClientProvider         the S3 client provider
     * @param s3Properties             the S3 configuration properties
     * @param bucketCredentialsService service for persisting bucket credentials
     * @param iamUserManagementService service for IAM user management
     */
    public S3BucketProvisionService(S3ClientProvider s3ClientProvider, S3Properties s3Properties,
                                    BucketCredentialsService bucketCredentialsService,
                                    IamUserManagementService iamUserManagementService) {
        this.s3ClientProvider = s3ClientProvider;
        this.s3Properties = s3Properties;
        this.bucketCredentialsService = bucketCredentialsService;
        this.iamUserManagementService = iamUserManagementService;
    }

    /**
     * Creates a new S3 bucket and provisions credentials for it.
     *
     * @param bucketName the name of the bucket to create
     * @return the bucket credentials (plain secret key for immediate use)
     */
    public BucketCredentialsEntity createSecureBucket(String bucketName) {
        validateBucketName(bucketName);
        log.info("Create secure bucket {}", bucketName);
        createBucket(bucketName);
        return createBucketCredentials(bucketName);
    }

    /**
     * Provisions credentials for an existing bucket.
     *
     * @param bucketName the name of the existing bucket
     * @return the bucket credentials (plain secret key for immediate use)
     */
    public BucketCredentialsEntity createBucketCredentials(String bucketName) {
        validateBucketName(bucketName);
        log.info("Creating bucket credentials for existing bucket {}", bucketName);

        boolean isAws = isAwsEndpoint(s3Properties.getEndpoint());

        if (isAws) {
            log.info("AWS mode detected - reusing configured access key for bucket {}", bucketName);
            BucketCredentialsEntity bucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                    .bucketName(bucketName)
                    .accessKey(s3Properties.getAccessKey())
                    .secretKey(s3Properties.getSecretKey())
                    .build();
            bucketCredentialsService.saveBucketCredentials(bucketCredentials);
            s3ClientProvider.clearBucketCache(bucketName);
            return bucketCredentials;
        }

        String accessKey = "GetBucketUser-" + UUID.randomUUID().toString().substring(0, 8);
        String secretKey = UUID.randomUUID().toString();

        BucketCredentialsEntity bucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(bucketName)
                .accessKey(accessKey)
                .secretKey(secretKey)
                .build();

        iamUserManagementService.createUser(bucketCredentials);
        iamUserManagementService.attachPolicyToUser(bucketCredentials);
        updateBucketPolicy(bucketName, accessKey);

        BucketCredentialsEntity savedCredentials = bucketCredentialsService.saveBucketCredentials(bucketCredentials);
        s3ClientProvider.clearBucketCache(bucketName);
        return savedCredentials;
    }

    /**
     * Ensures that bucket credentials exist for the given bucket.
     * If the bucket exists but credentials are missing, creates them.
     * If the bucket does not exist, creates the bucket with credentials.
     * If both bucket and credentials exist, does nothing.
     *
     * @param bucketName the name of the bucket
     * @return the bucket credentials (existing or newly created)
     */
    public BucketCredentialsEntity ensureBucketCredentials(String bucketName) {
        validateBucketName(bucketName);
        log.info("Ensuring bucket credentials exist for bucket: {}", bucketName);

        if (bucketCredentialsService.bucketCredentialsExist(bucketName)) {
            log.info("Bucket credentials already exist for bucket: {}", bucketName);
            return bucketCredentialsService.getBucketCredentials(bucketName);
        }

        if (bucketExists(bucketName)) {
            log.info("Bucket {} exists but credentials are missing. Creating credentials...", bucketName);
            BucketCredentialsEntity credentials = createBucketCredentials(bucketName);
            log.info("Created bucket credentials for existing bucket: {}", bucketName);
            return credentials;
        }

        log.info("Bucket {} does not exist. Creating secure bucket with credentials...", bucketName);
        BucketCredentialsEntity credentials = createSecureBucket(bucketName);
        log.info("Created secure bucket with credentials: {}", bucketName);
        return credentials;
    }

    /**
     * Checks if a bucket with the specified name exists.
     *
     * @param bucketName the name of the bucket to check
     * @return true if the bucket exists, false otherwise
     */
    public boolean bucketExists(String bucketName) {
        validateBucketName(bucketName);
        try {
            S3Client s3Client = s3ClientProvider.adminS3Client();
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking if bucket {} exists: {}", bucketName, e.getMessage());
            throw new S3ServerException("Error checking if bucket exists: " + e.getMessage(), e);
        }
    }

    /**
     * Removes all objects, policy, and the bucket itself.
     *
     * @param bucketName the name of the bucket to clean up
     */
    public void cleanupBucket(String bucketName) {
        try {
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

            s3ClientAdmin.deleteBucketPolicy(DeleteBucketPolicyRequest.builder().bucket(bucketName).build());
            s3ClientAdmin.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error("Failed to cleanup bucket: {}", bucketName, e);
            throw new S3ServerException("Failed to cleanup bucket", e);
        }
    }

    private boolean isAwsEndpoint(String endpoint) {
        return endpoint == null || endpoint.isBlank()
                || endpoint.toLowerCase().contains(".amazonaws.com")
                || endpoint.toLowerCase().contains(".aws.");
    }

    private void createBucket(String bucketName) {
        try {
            String region = s3Properties.getRegion();
            String endpoint = s3Properties.getEndpoint();
            boolean isAws = isAwsEndpoint(endpoint);

            CreateBucketRequest.Builder requestBuilder = CreateBucketRequest.builder()
                    .bucket(bucketName);

            if (isAws && !"us-east-1".equals(region)) {
                log.info("Creating AWS S3 bucket {} in region {} with LocationConstraint", bucketName, region);
                requestBuilder.createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                                .locationConstraint(BucketLocationConstraint.fromValue(region))
                                .build());
            } else {
                log.info("Creating bucket {} (AWS: {}, Region: {})", bucketName, isAws, region);
            }

            s3ClientProvider.adminS3Client().createBucket(requestBuilder.build());
            log.info("Bucket {} created successfully", bucketName);

        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            log.warn("Bucket {} already exists, continuing...", bucketName);
        } catch (Exception e) {
            log.error("Failed to create bucket {}: {}", bucketName, e.getMessage());
            throw new S3ServerException("Failed to create bucket: " + e.getMessage(), e);
        }
    }

    private void updateBucketPolicy(String bucketName, String accessKey) {
        try {
            S3Client s3ClientAdmin = s3ClientProvider.adminS3Client();
            GetBucketPolicyRequest getPolicyRequest = GetBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .build();

            String existingPolicy = null;
            try {
                String policy = s3ClientAdmin.getBucketPolicy(getPolicyRequest).policy();
                if (!policy.equals("{}")) {
                    existingPolicy = policy;
                }
            } catch (AwsServiceException e) {
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
                finalPolicy = String.format("""
                        {
                            "Version": "2012-10-17",
                            "Statement": [
                                %s
                            ]
                        }
                        """, newStatement);
            }

            PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(finalPolicy)
                    .build();
            s3ClientAdmin.putBucketPolicy(policyRequest);
            log.info("Updated bucket {} policy", bucketName);
        } catch (Exception e) {
            log.error("Failed to update bucket policy for bucket: {}", bucketName, e);
            throw new S3ServerException("Failed to update bucket policy", e);
        }
    }

    private void validateBucketName(String bucketName) {
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalArgumentException("Bucket name cannot be null or empty");
        }
        if (!bucketName.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$")) {
            throw new IllegalArgumentException("Invalid bucket name format: " + bucketName);
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
