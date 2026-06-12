package it.eng.tools.s3.service;

import it.eng.tools.exception.S3ServerException;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.TemporaryBucketUser;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.repository.TemporaryBucketUserRepository;
import it.eng.tools.service.FieldEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class TemporaryBucketUserService {

    private static final String TEMP_USER_PREFIX = "TempUser-";
    private static final String TEMP_POLICY_PREFIX = "temp-tp-policy-";

    private final IamUserManagementService iamUserManagementService;
    private final TemporaryBucketUserRepository temporaryBucketUserRepository;
    private final FieldEncryptionService fieldEncryptionService;
    private final S3Properties s3Properties;

    public TemporaryBucketUserService(IamUserManagementService iamUserManagementService,
                                      TemporaryBucketUserRepository temporaryBucketUserRepository,
                                      FieldEncryptionService fieldEncryptionService,
                                      S3Properties s3Properties) {
        this.iamUserManagementService = iamUserManagementService;
        this.temporaryBucketUserRepository = temporaryBucketUserRepository;
        this.fieldEncryptionService = fieldEncryptionService;
        this.s3Properties = s3Properties;
    }

    /**
     * Creates a temporary Minio user scoped to a single object key within a bucket.
     * The generated IAM policy allows only {@code s3:PutObject} on the exact resource
     * {@code arn:aws:s3:::<bucketName>/<objectKey>}.
     * The returned entity contains the <em>plain</em> (unencrypted) secret key for immediate
     * use in the DataAddress; the value stored in MongoDB is encrypted.
     * <p>
     * If attaching the policy or persisting to MongoDB fails, the IAM user is deleted
     * as a compensating action to avoid orphaned resources.
     *
     * @param transferProcessId the transfer process ID — used as the document {@code @Id}
     * @param bucketName        the bucket that holds the object
     * @param objectKey         the exact object key the temporary user is allowed to write
     * @return the persisted {@link TemporaryBucketUser} with a plain secret key
     */
    public TemporaryBucketUser createTemporaryUser(String transferProcessId, String bucketName, String objectKey) {
        return createTemporaryUser(transferProcessId, bootstrapManagementCredentials(bucketName), bucketName, objectKey);
    }

    /**
     * Creates a temporary Minio user using the supplied management credentials.
     *
     * @param transferProcessId the transfer process ID — used as the document {@code @Id}
     * @param managementCredentials the credentials used for Minio IAM administration
     * @param bucketName the bucket that holds the object
     * @param objectKey the exact object key the temporary user is allowed to write
     * @return the persisted {@link TemporaryBucketUser} with a plain secret key
     */
    public TemporaryBucketUser createTemporaryUser(String transferProcessId,
                                                   BucketCredentialsEntity managementCredentials,
                                                   String bucketName,
                                                   String objectKey) {
        String accessKey = TEMP_USER_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String plainSecretKey = UUID.randomUUID().toString();

        log.info("Creating temporary bucket user {} for transfer process {}", accessKey, transferProcessId);

        // Reuse the existing createUser path via a thin BucketCredentialsEntity adapter
        BucketCredentialsEntity adapter = BucketCredentialsEntity.Builder.newInstance()
                .accessKey(accessKey)
                .secretKey(plainSecretKey)
                .bucketName(bucketName)
                .build();
        iamUserManagementService.createUser(adapter);

        // Attach policy and persist; compensate by deleting the IAM user on any failure
        // to avoid orphaned resources that cannot be cleaned up later
        String policyName = TEMP_POLICY_PREFIX + transferProcessId;
        try {
            // Attach a minimal PutObject-only policy scoped to the exact object key
            String policyJson = createTemporaryUserPolicy(bucketName, objectKey);
            log.debug("Attaching temporary policy {} to user {}", policyName, accessKey);
            iamUserManagementService.attachTemporaryPolicy(managementCredentials, accessKey, policyName, policyJson);

            // Persist with encrypted secret key
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
            // Compensate: remove the IAM user so it does not become an orphaned resource
            log.error("Failed to complete temporary user setup for {}; compensating by deleting IAM user {}", transferProcessId, accessKey);
            try {
                iamUserManagementService.deleteUser(managementCredentials, accessKey);
            } catch (Exception compensationEx) {
                log.error("Compensation failed — IAM user {} may be orphaned", accessKey, compensationEx);
            }
            throw new S3ServerException("Failed to create temporary user for transfer process: " + transferProcessId, e);
        }

        // Return entity with plain secret key for immediate use in DataAddress
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
     * Deletes the temporary Minio user, its associated policy, and the MongoDB document.
     * The IAM user is removed first so that the policy is no longer attached to any entity;
     * Minio rejects policy deletion with {@code XMinioIAMPolicyInUse} when the policy is still
     * assigned to a user. After the user is gone the policy is deleted, and finally the MongoDB
     * record is removed. Errors during Minio-side deletion are logged but do not propagate —
     * the MongoDB record is always removed so stale entries do not accumulate.
     *
     * @param transferProcessId the transfer process ID
     */
    public void deleteTemporaryUser(String transferProcessId) {
        temporaryBucketUserRepository.findById(transferProcessId).ifPresent(entity -> {
            log.info("Cleaning up temporary bucket user {} for transfer process {}", entity.getAccessKey(), transferProcessId);
            String policyName = TEMP_POLICY_PREFIX + transferProcessId;
            BucketCredentialsEntity managementCredentials = bootstrapManagementCredentials(entity.getBucketName());
            // Delete the user first so the policy is no longer attached, then delete the policy.
            // Minio rejects policy deletion with XMinioIAMPolicyInUse if the policy is still
            // assigned to a user; removing the user first releases that attachment.
            try {
                iamUserManagementService.deleteUser(managementCredentials, entity.getAccessKey());
            } catch (Exception e) {
                log.warn("Could not delete temporary Minio user {}: {}", entity.getAccessKey(), e.getMessage());
            }
            try {
                iamUserManagementService.deletePolicy(managementCredentials, policyName);
            } catch (Exception e) {
                log.warn("Could not delete temporary Minio policy {}: {}", policyName, e.getMessage());
            }
            temporaryBucketUserRepository.deleteById(transferProcessId);
            log.info("Temporary bucket user {} cleaned up for transfer process {}", entity.getAccessKey(), transferProcessId);
        });
    }

    private BucketCredentialsEntity bootstrapManagementCredentials(String bucketName) {
        String accessKey = s3Properties.getAccessKey();
        String secretKey = s3Properties.getSecretKey();
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new S3ServerException("Missing bootstrap S3 management credentials in application.properties");
        }
        return BucketCredentialsEntity.Builder.newInstance()
                .bucketName(bucketName)
                .accessKey(accessKey)
                .secretKey(secretKey)
                .build();
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
