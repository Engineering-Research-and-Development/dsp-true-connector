package it.eng.dataplane.s3.service;

import it.eng.dataplane.s3.model.BucketCredentialsEntity;

/**
 * Service interface for managing IAM users in S3-compatible backends.
 */
public interface IamUserManagementService {

    /**
     * Creates a new IAM user for the given bucket credentials.
     *
     * @param bucketCredentials the credentials describing the user to create
     */
    void createUser(BucketCredentialsEntity bucketCredentials);

    /**
     * Attaches an access policy to the IAM user described by the given credentials.
     *
     * @param bucketCredentials the credentials identifying the user and bucket
     */
    void attachPolicyToUser(BucketCredentialsEntity bucketCredentials);

    /**
     * Attaches a temporary named policy to the given user.
     *
     * @param accessKey  the access key of the user
     * @param policyName the name to assign to the policy
     * @param policyJson the JSON body of the policy to attach
     */
    void attachTemporaryPolicy(String accessKey, String policyName, String policyJson);

    /**
     * Deletes the IAM user identified by the given access key.
     *
     * @param accessKey the access key of the user to delete
     */
    void deleteUser(String accessKey);

    /**
     * Deletes the named IAM policy.
     *
     * @param policyName the name of the policy to delete
     */
    void deletePolicy(String policyName);
}
