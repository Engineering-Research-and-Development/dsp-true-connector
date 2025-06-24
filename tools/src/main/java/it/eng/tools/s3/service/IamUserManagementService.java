package it.eng.tools.s3.service;

public interface IamUserManagementService {

    void createUser(BucketCredentials bucketCredentials);

    void attachPolicyToUser(BucketCredentials bucketCredentials);
}
