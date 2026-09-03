package it.eng.tools.s3.service;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Verifies that a candidate set of external S3 credentials actually grant access to a given
 * bucket, without persisting the candidate credentials. This is used to back the opt-in
 * {@code verifyConnection} pre-flight check when an admin supplies "bring your own bucket"
 * credentials for a tenant.
 */
@Service
@Slf4j
public class BucketConnectionVerificationService {

    private final S3ClientProvider s3ClientProvider;
    private final S3Properties s3Properties;

    /**
     * Creates a new {@link BucketConnectionVerificationService}.
     *
     * @param s3ClientProvider the provider used to build a bucket-scoped S3 client for the candidate credentials
     * @param s3Properties the S3 configuration properties used to resolve region and endpoint
     */
    public BucketConnectionVerificationService(S3ClientProvider s3ClientProvider, S3Properties s3Properties) {
        this.s3ClientProvider = s3ClientProvider;
        this.s3Properties = s3Properties;
    }

    /**
     * Verifies that the given candidate credentials grant access to the given bucket via a
     * {@code HeadBucket} probe. The candidate credentials are never persisted; the ad-hoc
     * client built for the probe is evicted from the {@link S3ClientProvider} cache after use,
     * regardless of outcome.
     *
     * @param bucketName the bucket name to probe
     * @param accessKey the candidate S3 access key
     * @param secretKey the candidate S3 secret key
     * @return {@code true} if the candidate credentials successfully access the bucket, {@code false} otherwise
     */
    public boolean verify(String bucketName, String accessKey, String secretKey) {
        try {
            BucketCredentialsEntity candidateCredentials = BucketCredentialsEntity.Builder.newInstance()
                    .bucketName(bucketName)
                    .accessKey(accessKey)
                    .secretKey(secretKey)
                    .build();

            String endpointOverride = StringUtils.isNotBlank(s3Properties.getExternalPresignedEndpoint())
                    ? s3Properties.getExternalPresignedEndpoint()
                    : s3Properties.getEndpoint();

            S3ClientRequest s3ClientRequest =
                    S3ClientRequest.from(s3Properties.getRegion(), endpointOverride, candidateCredentials);
            S3Client s3Client = s3ClientProvider.s3Client(s3ClientRequest);

            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
            return true;
        } catch (S3Exception e) {
            log.warn("Bucket connectivity verification failed for bucket '{}': {}", bucketName, e.getMessage());
            return false;
        } finally {
            s3ClientProvider.clearBucketCache(bucketName);
        }
    }
}
