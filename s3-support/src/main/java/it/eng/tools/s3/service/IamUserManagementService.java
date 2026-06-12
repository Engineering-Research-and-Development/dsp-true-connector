package it.eng.tools.s3.service;

import it.eng.tools.s3.model.BucketCredentialsEntity;

public interface IamUserManagementService {

    void createUser(BucketCredentialsEntity bucketCredentials);

    void attachPolicyToUser(BucketCredentialsEntity bucketCredentials);

    void attachTemporaryPolicy(BucketCredentialsEntity managementCredentials, String accessKey,
                               String policyName, String policyJson);

    void deleteUser(BucketCredentialsEntity managementCredentials, String accessKey);

    void deletePolicy(BucketCredentialsEntity managementCredentials, String policyName);
}
