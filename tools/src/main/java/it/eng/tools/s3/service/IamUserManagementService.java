package it.eng.tools.s3.service;

import it.eng.tools.s3.model.BucketCredentialsEntity;

public interface IamUserManagementService {

    void createUser(BucketCredentialsEntity bucketCredentials);

    void attachPolicyToUser(BucketCredentialsEntity bucketCredentials);

    void attachTemporaryPolicy(String accessKey, String policyName, String policyJson);

    void deleteUser(String accessKey);

    void deletePolicy(String policyName);
}
