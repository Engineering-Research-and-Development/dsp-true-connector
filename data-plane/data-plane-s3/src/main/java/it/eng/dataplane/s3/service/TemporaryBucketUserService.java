package it.eng.dataplane.s3.service;

import it.eng.dataplane.s3.exception.S3ServerException;
import it.eng.dataplane.s3.model.BucketCredentialsEntity;
import it.eng.dataplane.s3.model.TemporaryBucketUser;
import it.eng.dataplane.s3.repository.TemporaryBucketUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for creating and managing short-lived IAM users scoped to a single S3 object.
 */
@Service
@Slf4j
public class TemporaryBucketUserService {

    private static final String TEMP_USER_PREFIX = "TempUser-";
    private static final String TEMP_POLICY_PREFIX = "temp-tp-policy-";

    private final IamUserManagementService iamUserManagementService;
    private final TemporaryBucketUserRepository temporaryBucketUserRepository;
    private final FieldEncryptionService fieldEncryptionService;

    /**
     * Constructs the service.
     *
     * @param iamUserManagementService      the IAM user management service
     * @param temporaryBucketUserRepository the MongoDB repository for temporary users
     * @param fieldEncryptionService        the encryption service
     */
    public TemporaryBucketUserService(IamUserManagementService iamUserManagementService,
                                      TemporaryBucketUserRepository temporaryBucketUserRepository,
                                      FieldEncryptionService fieldEncryptionService) {
        this.iamUserManagementService = iamUserManagementService;
        this.temporaryBucketUserRepository = temporaryBucketUserRepository;
        this.fieldEncryptionService = fieldEncryptionService;
    }

    /**
     * Creates a temporary IAM user scoped to a single object key within a bucket.
     * The generated policy allows only {@code s3:PutObject} on the exact resource
     * {@code arn:aws:s3:::&lt;bucketName&gt;/&lt;objectKey&gt;}.
     * The returned entity contains the plain (unencrypted) secret key for immediate use;
     * the value stored in MongoDB is encrypted.
     * <p>
     * If attaching the policy or persisting to MongoDB fails, the IAM user is deleted
     * as a compensating action to avoid orphaned resources.
     *
     * @param transferProcessId the transfer process ID — used as the MongoDB document {@code _id}
     * @param bucketName        the bucket that holds the object
     * @param objectKey         the exact object key the temporary user is allowed to write
     * @return the persisted {@link TemporaryBucketUser} with a plain secret key
     */
    public TemporaryBucketUser createTemporaryUser(String transferProcessId, String bucketName, String objectKey) {
        String accessKey = TEMP_USER_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String plainSecretKey = UUID.randomUUID().toString();

        log.info("Creating temporary bucket user {} for transfer process {}", accessKey, transferProcessId);

        BucketCredentialsEntity adapter = BucketCredentialsEntity.Builder.newInstance()
                .accessKey(accessKey)
                .secretKey(plainSecretKey)
                .bucketName(bucketName)
                .build();
        iamUserManagementService.createUser(adapter);

        String policyName = TEMP_POLICY_PREFIX + transferProcessId;
        try {
            String policyJson = createTemporaryUserPolicy(bucketName, objectKey);
            log.debug("Attaching temporary policy {} to user {}", policyName, accessKey);
            iamUserManagementService.attachTemporaryPolicy(accessKey, policyName, policyJson);

            TemporaryBucketUser entity = TemporaryBucketUser.Builder.newInstance()
                    .transferProcessId(transferProcessId)
                    .accessKey(accessKey)
                    .secretKey(fieldEncryptionService.encrypt(plainSecretKey))
                    .bucketName(bucketName)
                    .objectKey(objectKey)
                    .build();
            temporaryBucketUserRepository.save(entity);
            log.info("Temporary bucket user {} persisted for transfer process {}", accessKey, transferProcessId);
        } catch (Exception e) {
            log.error("Failed to complete temporary user setup for {}; compensating by deleting IAM user {}", transferProcessId, accessKey);
            try {
                iamUserManagementService.deleteUser(accessKey);
            } catch (Exception compensationEx) {
                log.error("Compensation failed — IAM user {} may be orphaned", accessKey, compensationEx);
            }
            throw new S3ServerException("Failed to create temporary user for transfer process: " + transferProcessId, e);
        }

        return TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(transferProcessId)
                .accessKey(accessKey)
                .secretKey(plainSecretKey)
                .bucketName(bucketName)
                .objectKey(objectKey)
                .build();
    }

    /**
     * Loads the temporary user for a transfer process and returns it with the decrypted secret key.
     *
     * @param transferProcessId the transfer process ID
     * @return the {@link TemporaryBucketUser} with a plain secret key
     */
    public TemporaryBucketUser getTemporaryUser(String transferProcessId) {
        TemporaryBucketUser entity = temporaryBucketUserRepository.findById(transferProcessId)
                .orElseThrow(() -> {
                    log.error("Temporary bucket user not found for transfer process: {}", transferProcessId);
                    return new S3ServerException("Temporary bucket user not found for transfer process: " + transferProcessId);
                });
        return TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(entity.getTransferProcessId())
                .accessKey(entity.getAccessKey())
                .secretKey(fieldEncryptionService.decrypt(entity.getSecretKey()))
                .bucketName(entity.getBucketName())
                .objectKey(entity.getObjectKey())
                .build();
    }

    /**
     * Deletes the temporary IAM user, its associated policy, and the MongoDB document.
     * The IAM user is removed first so the policy is no longer attached to any entity;
     * then the policy is deleted, and finally the MongoDB record is removed.
     * Errors during IAM-side deletion are logged but do not propagate.
     *
     * @param transferProcessId the transfer process ID
     */
    public void deleteTemporaryUser(String transferProcessId) {
        temporaryBucketUserRepository.findById(transferProcessId).ifPresent(entity -> {
            log.info("Cleaning up temporary bucket user {} for transfer process {}", entity.getAccessKey(), transferProcessId);
            String policyName = TEMP_POLICY_PREFIX + transferProcessId;
            try {
                iamUserManagementService.deleteUser(entity.getAccessKey());
            } catch (Exception e) {
                log.warn("Could not delete temporary IAM user {}: {}", entity.getAccessKey(), e.getMessage());
            }
            try {
                iamUserManagementService.deletePolicy(policyName);
            } catch (Exception e) {
                log.warn("Could not delete temporary IAM policy {}: {}", policyName, e.getMessage());
            }
            temporaryBucketUserRepository.deleteById(transferProcessId);
            log.info("Temporary bucket user {} cleaned up for transfer process {}", entity.getAccessKey(), transferProcessId);
        });
    }

    private String createTemporaryUserPolicy(String bucketName, String objectKey) {
        return String.format("""
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Action": [
                                "s3:PutObject"
                            ],
                            "Resource": [
                                "arn:aws:s3:::%s/%s"
                            ]
                        }
                    ]
                }
                """, bucketName, objectKey);
    }
}
