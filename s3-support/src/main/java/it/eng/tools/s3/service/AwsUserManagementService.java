package it.eng.tools.s3.service;

import io.minio.admin.MinioAdminClient;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * AWS S3 IAM user management service.
 *
 * This service is activated when MinioAdminClient bean does NOT exist,
 * indicating AWS S3 mode.
 */
@Component
@ConditionalOnMissingBean(MinioAdminClient.class)
@Slf4j
public class AwsUserManagementService implements IamUserManagementService {
    private static final String TEMP_USER_REDESIGN_MESSAGE =
            "AWS temporary IAM user management is not implemented yet. "
                    + "TemporaryBucketUserService currently generates caller-chosen access/secret pairs, "
                    + "but AWS CreateAccessKey returns AWS-generated credentials. "
                    + "Redesign the temporary-user flow so AwsUserManagementService creates and returns the "
                    + "AWS-generated access key pair before enabling this path.";

    /**
     * The AWS temporary-user path needs a follow-up redesign before it can be implemented safely.
     *
     * @param managementCredentials the tenant-scoped management credentials
     * @param accessKey the temporary user's access key identifier
     * @param policyName the policy to attach
     * @param policyJson the policy document
     */
    @Override
    public void attachTemporaryPolicy(BucketCredentialsEntity managementCredentials, String accessKey,
                                      String policyName, String policyJson) {
        throw new UnsupportedOperationException(TEMP_USER_REDESIGN_MESSAGE);
    }

    /**
     * The AWS temporary-user path needs a follow-up redesign before it can be implemented safely.
     *
     * @param managementCredentials the tenant-scoped management credentials
     * @param accessKey the temporary user's access key identifier
     */
    @Override
    public void deleteUser(BucketCredentialsEntity managementCredentials, String accessKey) {
        throw new UnsupportedOperationException(TEMP_USER_REDESIGN_MESSAGE);
    }

    /**
     * The AWS temporary-user path needs a follow-up redesign before it can be implemented safely.
     *
     * @param managementCredentials the tenant-scoped management credentials
     * @param policyName the policy to remove
     */
    @Override
    public void deletePolicy(BucketCredentialsEntity managementCredentials, String policyName) {
        throw new UnsupportedOperationException(TEMP_USER_REDESIGN_MESSAGE);
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
