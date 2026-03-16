package it.eng.tools.s3.service;

import io.minio.admin.MinioAdminClient;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * AWS S3 IAM user management service - no-op implementation.
 *
 * This service is activated when MinioAdminClient bean does NOT exist,
 * indicating AWS S3 mode. IAM users must be pre-configured externally.
 */
@Component
@ConditionalOnMissingBean(MinioAdminClient.class)
@Slf4j
public class AwsUserManagementService implements IamUserManagementService {
    /**
     * @param accessKey
     * @param policyName
     * @param policyJson
     */
    @Override
    public void attachTemporaryPolicy(String accessKey, String policyName, String policyJson) {

    }

    /**
     * @param accessKey
     */
    @Override
    public void deleteUser(String accessKey) {

    }

    /**
     * @param policyName
     */
    @Override
    public void deletePolicy(String policyName) {

    }

    public AwsUserManagementService() {
        log.info("AwsUserManagementService initialized - using pre-configured AWS credentials");
    }

    @Override
    public void createUser(BucketCredentialsEntity bucketCredentials) {
        log.info("AWS S3 mode - IAM user creation skipped. Bucket: {}", bucketCredentials.getBucketName());
    }

    @Override
    public void attachPolicyToUser(BucketCredentialsEntity bucketCredentials) {
        log.info("AWS S3 mode - policy attachment skipped. Bucket: {}", bucketCredentials.getBucketName());
    }
}

