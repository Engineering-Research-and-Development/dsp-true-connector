package it.eng.dataplane.s3.service;

import io.minio.admin.MinioAdminClient;
import io.minio.admin.UserInfo;
import it.eng.dataplane.s3.exception.S3ServerException;
import it.eng.dataplane.s3.model.BucketCredentialsEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Minio-specific IAM user management.
 * Only active when a {@link MinioAdminClient} bean exists.
 */
@Component
@ConditionalOnBean(MinioAdminClient.class)
@Slf4j
public class MinioUserManagementService implements IamUserManagementService {

    private final MinioAdminClient minioAdminClient;

    /**
     * Constructs the service with the Minio admin client.
     *
     * @param minioAdminClient the MinIO admin client
     */
    public MinioUserManagementService(MinioAdminClient minioAdminClient) {
        this.minioAdminClient = minioAdminClient;
        log.info("MinioUserManagementService initialized - Minio IAM enabled");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createUser(BucketCredentialsEntity bucketCredentials) {
        try {
            if (minioAdminClient.getUserInfo(bucketCredentials.getAccessKey()) != null) {
                log.info("User {} already exists, skipping creation.", bucketCredentials.getAccessKey());
            }
        } catch (Exception e) {
            try {
                minioAdminClient.addUser(bucketCredentials.getAccessKey(), UserInfo.Status.ENABLED, bucketCredentials.getSecretKey(), null, null);
                log.info("User {} created successfully", bucketCredentials.getAccessKey());
            } catch (Exception createError) {
                log.error("Failed to create user {}: {}", bucketCredentials.getAccessKey(), createError.getMessage());
                throw new S3ServerException("Failed to create user", createError);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void attachPolicyToUser(BucketCredentialsEntity bucketCredentials) {
        String policyName = "policy-" + bucketCredentials.getBucketName();
        try {
            String policyJson = createUserPolicy(bucketCredentials.getBucketName());
            log.debug("Creating policy {} with content: {}", policyName, policyJson);
            minioAdminClient.addCannedPolicy(policyName, policyJson);
            log.debug("Attaching policy {} to user {}", policyName, bucketCredentials.getAccessKey());
            minioAdminClient.setPolicy(bucketCredentials.getAccessKey(), false, policyName);
        } catch (Exception e) {
            log.error("Error attaching policy to user: {}", e.getMessage());
            throw new S3ServerException("Error attaching policy to user", e);
        }
    }

    private String createUserPolicy(String bucketName) {
        return String.format("""
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Action": [
                                "s3:ListBucket",
                                "s3:GetObject",
                                "s3:PutObject",
                                "s3:DeleteObject"
                            ],
                            "Resource": [
                                "arn:aws:s3:::%s",
                                "arn:aws:s3:::%s/*"
                            ]
                        }
                    ]
                }
                """, bucketName, bucketName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void attachTemporaryPolicy(String accessKey, String policyName, String policyJson) {
        try {
            log.debug("Creating temporary policy {} with content: {}", policyName, policyJson);
            minioAdminClient.addCannedPolicy(policyName, policyJson);
            log.debug("Attaching temporary policy {} to user {}", policyName, accessKey);
            minioAdminClient.setPolicy(accessKey, false, policyName);
        } catch (Exception e) {
            log.error("Failed to attach temporary policy {} to user {}: {}", policyName, accessKey, e.getMessage());
            throw new S3ServerException("Failed to attach temporary policy to user", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUser(String accessKey) {
        try {
            minioAdminClient.deleteUser(accessKey);
            log.info("User {} deleted successfully", accessKey);
        } catch (Exception e) {
            log.error("Failed to delete user {}: {}", accessKey, e.getMessage());
            throw new S3ServerException("Failed to delete user", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deletePolicy(String policyName) {
        try {
            minioAdminClient.removeCannedPolicy(policyName);
            log.info("Policy {} deleted successfully", policyName);
        } catch (Exception e) {
            log.error("Failed to delete policy {}: {}", policyName, e.getMessage());
            throw new S3ServerException("Failed to delete policy", e);
        }
    }
}
