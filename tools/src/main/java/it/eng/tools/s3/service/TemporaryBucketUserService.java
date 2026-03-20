package it.eng.tools.s3.service;

import it.eng.tools.exception.S3ServerException;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.TemporaryBucketUser;
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

    public TemporaryBucketUserService(IamUserManagementService iamUserManagementService,
                                      TemporaryBucketUserRepository temporaryBucketUserRepository,
                                      FieldEncryptionService fieldEncryptionService) {
        this.iamUserManagementService = iamUserManagementService;
        this.temporaryBucketUserRepository = temporaryBucketUserRepository;
        this.fieldEncryptionService = fieldEncryptionService;
    }

    /**
     * Creates a temporary Minio user scoped to a single object key within a bucket.
     * The generated IAM policy allows only {@code s3:PutObject} on the exact resource
     * {@code arn:aws:s3:::<bucketName>/<objectKey>}.
     * The returned entity contains the <em>plain</em> (unencrypted) secret key for immediate
     * use in the DataAddress; the value stored in MongoDB is encrypted.
     *
     * @param transferProcessId the transfer process ID — used as the document {@code @Id}
     * @param bucketName        the bucket that holds the object
     * @param objectKey         the exact object key the temporary user is allowed to write
     * @return the persisted {@link TemporaryBucketUser} with a plain secret key
     */
    public TemporaryBucketUser createTemporaryUser(String transferProcessId, String bucketName, String objectKey) {
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

        // Attach a minimal PutObject-only policy scoped to the exact object key
        String policyName = TEMP_POLICY_PREFIX + transferProcessId;
        String policyJson = createTemporaryUserPolicy(bucketName, objectKey);
        log.debug("Attaching temporary policy {} to user {}", policyName, accessKey);
        iamUserManagementService.attachTemporaryPolicy(accessKey, policyName, policyJson);

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
     * Errors during Minio-side deletion are logged but do not propagate — the MongoDB
     * record is always removed so stale entries do not accumulate.
     *
     * @param transferProcessId the transfer process ID
     */
    public void deleteTemporaryUser(String transferProcessId) {
        temporaryBucketUserRepository.findById(transferProcessId).ifPresent(entity -> {
            String policyName = TEMP_POLICY_PREFIX + transferProcessId;
            try {
                iamUserManagementService.deleteUser(entity.getAccessKey());
            } catch (Exception e) {
                log.warn("Could not delete temporary Minio user {}: {}", entity.getAccessKey(), e.getMessage());
            }
            try {
                iamUserManagementService.deletePolicy(policyName);
            } catch (Exception e) {
                log.warn("Could not delete temporary Minio policy {}: {}", policyName, e.getMessage());
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


