package it.eng.tools.s3.service;

import it.eng.tools.s3.model.BucketCredentialsEntity;

public interface IamUserManagementService {

    /**
     * Creates a new IAM user with the given credentials.
     * Implementations that use a pre-configured static admin client (control plane) implement this.
     *
     * @param bucketCredentials the new user's access key and secret key
     */
    void createUser(BucketCredentialsEntity bucketCredentials);

    /**
     * Creates a new IAM user using explicit management credentials to authenticate the admin
     * operation. Required on the data plane where no static admin client is available.
     * Defaults to {@link #createUser(BucketCredentialsEntity)} for implementations that use
     * a pre-configured admin client and do not need per-request management credentials.
     *
     * @param bucketCredentials     the new user's access key and secret key
     * @param managementCredentials admin credentials (access key, secret key, endpoint) used
     *                              to build the admin client; may be null for CP implementations
     */
    default void createUser(BucketCredentialsEntity bucketCredentials, BucketCredentialsEntity managementCredentials) {
        createUser(bucketCredentials);
    }

    void attachPolicyToUser(BucketCredentialsEntity bucketCredentials);

    void attachTemporaryPolicy(BucketCredentialsEntity managementCredentials, String accessKey,
                               String policyName, String policyJson);

    void deleteUser(BucketCredentialsEntity managementCredentials, String accessKey);

    void deletePolicy(BucketCredentialsEntity managementCredentials, String policyName);
}
