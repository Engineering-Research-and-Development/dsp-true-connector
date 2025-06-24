package it.eng.tools.s3.service;

import io.minio.admin.MinioAdminClient;
import io.minio.admin.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MinioUserManagementService implements IamUserManagementService {

    private final MinioAdminClient minioAdminClient;

    public MinioUserManagementService(MinioAdminClient minioAdminClient) {
        this.minioAdminClient = minioAdminClient;
    }

    @Override
    public void createUser(BucketCredentials bucketCredentials) {
        try {
            // Check if user already exists
            if (minioAdminClient.getUserInfo(bucketCredentials.accessKey()) != null) {
                log.info("User {} already exists, skipping creation.", bucketCredentials.accessKey());
            }
        } catch (Exception e) {
            // User doesn't exist, create it
            try {
                minioAdminClient.addUser(bucketCredentials.accessKey(), UserInfo.Status.ENABLED, bucketCredentials.secretKey(), null, null);
                log.info("User {} created successfully", bucketCredentials.accessKey());
            } catch (Exception createError) {
                log.error("Failed to create user {}: {}", bucketCredentials.accessKey(), createError.getMessage());
                throw new RuntimeException("Failed to create user", createError);
            }
        }
    }

    @Override
    public void attachPolicyToUser(BucketCredentials bucketCredentials) {
        // Create and attach policy
        String policyName = "policy-" + bucketCredentials.bucketName();

        try {
            // TODO Check if policy already exists
            String policyJson = createUserPolicy(bucketCredentials.bucketName());
            log.debug("Creating policy {} with content: {}", policyName, policyJson);
            minioAdminClient.addCannedPolicy(policyName, policyJson);

            // Attach policy to user (correct order: userOrGroupName, policyName, isGroup)
            log.debug("Attaching policy {} to user {}", policyName, bucketCredentials.accessKey());
            minioAdminClient.setPolicy(bucketCredentials.accessKey(), false, policyName);
        } catch (Exception e) {
            log.error("Error checking policy existence: {}", e.getMessage());
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
