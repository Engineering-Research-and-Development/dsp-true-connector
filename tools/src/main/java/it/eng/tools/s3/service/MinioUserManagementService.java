package it.eng.tools.s3.service;

import io.minio.admin.MinioAdminClient;
import io.minio.admin.UserInfo;
import it.eng.tools.exception.S3ServerException;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Minio-specific IAM user management.
 * Only active when MinioAdminClient bean exists.
 */
@Component
@ConditionalOnBean(MinioAdminClient.class)
@Slf4j
public class MinioUserManagementService implements IamUserManagementService {

    private final MinioAdminClient minioAdminClient;

    public MinioUserManagementService(MinioAdminClient minioAdminClient) {
        this.minioAdminClient = minioAdminClient;
        log.info("MinioUserManagementService initialized - Minio IAM enabled");
    }

    @Override
    public void createUser(BucketCredentialsEntity bucketCredentials) {
        try {
            // Check if user already exists
            if (minioAdminClient.getUserInfo(bucketCredentials.getAccessKey()) != null) {
                log.info("User {} already exists, skipping creation.", bucketCredentials.getAccessKey());
            }
        } catch (Exception e) {
            // User doesn't exist, create it
            try {
                minioAdminClient.addUser(bucketCredentials.getAccessKey(), UserInfo.Status.ENABLED, bucketCredentials.getSecretKey(), null, null);
                log.info("User {} created successfully", bucketCredentials.getAccessKey());
            } catch (Exception createError) {
                log.error("Failed to create user {}: {}", bucketCredentials.getAccessKey(), createError.getMessage());
                throw new S3ServerException("Failed to create user", createError);
            }
        }
    }

    @Override
    public void attachPolicyToUser(BucketCredentialsEntity bucketCredentials) {
        // Create and attach policy
        String policyName = "policy-" + bucketCredentials.getBucketName();

        try {
            // TODO Check if policy already exists
            String policyJson = createUserPolicy(bucketCredentials.getBucketName());
            log.debug("Creating policy {} with content: {}", policyName, policyJson);
            minioAdminClient.addCannedPolicy(policyName, policyJson);

            // Attach policy to user (correct order: userOrGroupName, policyName, isGroup)
            log.debug("Attaching policy {} to user {}", policyName, bucketCredentials.getAccessKey());
            minioAdminClient.setPolicy(bucketCredentials.getAccessKey(), false, policyName);
        } catch (Exception e) {
            log.error("Error checking policy existence: {}", e.getMessage());
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
}
