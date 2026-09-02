package it.eng.tools.s3.service;

import it.eng.tools.s3.model.BucketCredentialsEntity;
import lombok.extern.slf4j.Slf4j;

/**
 * AWS S3 IAM user management service.
 *
 * <p>Plain (non-Spring) class instantiated by {@link DynamicIamUserManagementService}
 * when the target endpoint is AWS. Temporary-user operations are not yet fully implemented
 * for AWS ÔÇö {@code attachTemporaryPolicy}, {@code deleteUser}, and {@code deletePolicy}
 * throw {@link UnsupportedOperationException} until the AWS credential-generation flow
 * is redesigned.</p>
 */
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
