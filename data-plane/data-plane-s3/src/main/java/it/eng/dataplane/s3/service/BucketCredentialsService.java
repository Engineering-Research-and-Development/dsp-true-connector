package it.eng.dataplane.s3.service;

import it.eng.dataplane.s3.exception.S3ServerException;
import it.eng.dataplane.s3.model.BucketCredentialsEntity;
import it.eng.dataplane.s3.repository.BucketCredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing per-bucket S3 credentials in MongoDB.
 * Secret keys are encrypted before persisting and decrypted on retrieval.
 */
@Service
@Slf4j
public class BucketCredentialsService {

    private final FieldEncryptionService fieldEncryptionService;
    private final BucketCredentialsRepository bucketCredentialsRepository;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param fieldEncryptionService      the encryption service for secret keys
     * @param bucketCredentialsRepository the MongoDB repository for credentials
     */
    public BucketCredentialsService(FieldEncryptionService fieldEncryptionService,
                                    BucketCredentialsRepository bucketCredentialsRepository) {
        this.fieldEncryptionService = fieldEncryptionService;
        this.bucketCredentialsRepository = bucketCredentialsRepository;
    }

    /**
     * Retrieves the credentials for the given bucket, decrypting the secret key.
     *
     * @param bucketName the bucket name
     * @return the credentials with a plain-text secret key
     * @throws S3ServerException if no credentials are found for the bucket
     */
    public BucketCredentialsEntity getBucketCredentials(String bucketName) {
        BucketCredentialsEntity bucketCredentials = bucketCredentialsRepository.findByBucketName(bucketName)
                .orElse(null);
        if (bucketCredentials == null) {
            log.error("Bucket credentials not found for bucket: {}", bucketName);
            throw new S3ServerException("Bucket credentials not found for bucket: " + bucketName);
        }
        return BucketCredentialsEntity.Builder.newInstance()
                .accessKey(bucketCredentials.getAccessKey())
                .secretKey(fieldEncryptionService.decrypt(bucketCredentials.getSecretKey()))
                .bucketName(bucketCredentials.getBucketName())
                .build();
    }

    /**
     * Persists the given credentials, encrypting the secret key before saving.
     *
     * @param bucketCredentials the credentials to save
     * @return the saved entity (with encrypted secret key)
     */
    public BucketCredentialsEntity saveBucketCredentials(BucketCredentialsEntity bucketCredentials) {
        log.info("Saving bucket credentials for bucket: {}", bucketCredentials.getBucketName());
        BucketCredentialsEntity savedBucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                .accessKey(bucketCredentials.getAccessKey())
                .secretKey(fieldEncryptionService.encrypt(bucketCredentials.getSecretKey()))
                .bucketName(bucketCredentials.getBucketName())
                .build();
        return bucketCredentialsRepository.save(savedBucketCredentials);
    }

    /**
     * Checks whether credentials exist for the given bucket.
     *
     * @param bucketName the bucket name
     * @return {@code true} if credentials exist
     */
    public boolean bucketCredentialsExist(String bucketName) {
        return bucketCredentialsRepository.findByBucketName(bucketName).isPresent();
    }
}
