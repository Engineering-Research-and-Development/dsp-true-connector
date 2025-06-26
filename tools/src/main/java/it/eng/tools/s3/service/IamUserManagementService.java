package it.eng.tools.s3.service;

import it.eng.tools.s3.model.BucketCredentialsEntity;

public interface IamUserManagementService {

    void createUser(BucketCredentialsEntity bucketCredentials);

    void attachPolicyToUser(BucketCredentialsEntity bucketCredentials);
}
