package it.eng.tools.s3.service;

import it.eng.tools.exception.S3ServerException;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.repository.BucketCredentialsRepository;
import it.eng.tools.service.FieldEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BucketCredentialsService {

    private final FieldEncryptionService fieldEncryptionService;
    private final BucketCredentialsRepository bucketCredentialsRepository;

    public BucketCredentialsService(FieldEncryptionService fieldEncryptionService, BucketCredentialsRepository bucketCredentialsRepository) {
        this.fieldEncryptionService = fieldEncryptionService;
        this.bucketCredentialsRepository = bucketCredentialsRepository;
    }

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

    public BucketCredentialsEntity saveBucketCredentials(BucketCredentialsEntity bucketCredentials) {
        log.info("Saving bucket credentials for bucket: {}", bucketCredentials.getBucketName());
        BucketCredentialsEntity savedBucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                .accessKey(bucketCredentials.getAccessKey())
                .secretKey(fieldEncryptionService.encrypt(bucketCredentials.getSecretKey()))
                .bucketName(bucketCredentials.getBucketName())
                .build();
        return bucketCredentialsRepository.save(savedBucketCredentials);
    }
}
