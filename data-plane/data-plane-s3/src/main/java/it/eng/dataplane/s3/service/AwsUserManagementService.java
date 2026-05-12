package it.eng.dataplane.s3.service;

import io.minio.admin.MinioAdminClient;
import it.eng.dataplane.s3.model.BucketCredentialsEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * AWS S3 IAM user management service — no-op implementation.
 *
 * <p>Active when a {@link MinioAdminClient} bean does NOT exist,
 * indicating AWS S3 mode where IAM users must be pre-configured externally.
 */
@Component
@ConditionalOnMissingBean(MinioAdminClient.class)
@Slf4j
public class AwsUserManagementService implements IamUserManagementService {

    /**
     * Constructs the service.
     */
    public AwsUserManagementService() {
        log.info("AwsUserManagementService initialized - using pre-configured AWS credentials");
    }

    /**
     * {@inheritDoc}
     * <p>No-op in AWS mode.
     */
    @Override
    public void createUser(BucketCredentialsEntity bucketCredentials) {
        log.info("AWS S3 mode - IAM user creation skipped. Bucket: {}", bucketCredentials.getBucketName());
    }

    /**
     * {@inheritDoc}
     * <p>No-op in AWS mode.
     */
    @Override
    public void attachPolicyToUser(BucketCredentialsEntity bucketCredentials) {
        log.info("AWS S3 mode - policy attachment skipped. Bucket: {}", bucketCredentials.getBucketName());
    }

    /**
     * {@inheritDoc}
     * <p>No-op in AWS mode.
     */
    @Override
    public void attachTemporaryPolicy(String accessKey, String policyName, String policyJson) {
        log.info("AWS S3 mode - temporary policy attachment skipped for user: {}", accessKey);
    }

    /**
     * {@inheritDoc}
     * <p>No-op in AWS mode.
     */
    @Override
    public void deleteUser(String accessKey) {
        log.info("AWS S3 mode - user deletion skipped for: {}", accessKey);
    }

    /**
     * {@inheritDoc}
     * <p>No-op in AWS mode.
     */
    @Override
    public void deletePolicy(String policyName) {
        log.info("AWS S3 mode - policy deletion skipped for: {}", policyName);
    }
}
