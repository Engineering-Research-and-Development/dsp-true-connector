package it.eng.tools.s3.provision;

import it.eng.tools.s3.configuration.AwsClientProvider;
import it.eng.tools.s3.provision.model.S3CopyBucketDefinition;
import it.eng.tools.s3.provision.model.S3CopyBucketResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.internal.multipart.MultipartS3AsyncClient;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration;

import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

/**
 * This class is responsible for copying an S3 object from one bucket to another.
 * It uses the AWS SDK for Java to perform the copy operation asynchronously.
 */
@Service
@Slf4j
public class S3CopyBucket {

    private final AwsClientProvider awsClientProvider;
    private final MultipartConfiguration multipartConfiguration;

    public S3CopyBucket(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
        this.multipartConfiguration = MultipartConfiguration.builder()
                .thresholdInBytes(100 * 1024L * 1024L)
                .minimumPartSizeInBytes(10 * 1024L * 1024L)
                .build();
    }

    /**
     * Copies an S3 object from one bucket to another.
     *
     * @param copyBucketDefinition the definition of the copy operation
     * @return a CompletableFuture that will complete with the result of the copy operation
     */
    public CompletableFuture<S3CopyBucketResponse<Object>> copyBucket(S3CopyBucketDefinition copyBucketDefinition) {
        String sourceBucket = copyBucketDefinition.getSourceBucket();
        String sourceKey = copyBucketDefinition.getSourceKey();
        String destinationBucket = copyBucketDefinition.getDestinationBucket();
        String destinationKey = copyBucketDefinition.getDestinationKey();
        String endpointOverride = copyBucketDefinition.getEndpointOverride();

        var copyRequest = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket)
                .sourceKey(sourceKey)
                .destinationBucket(destinationBucket)
                .destinationKey(destinationKey)
                .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL)
                .build();

        var s3ClientRequest = S3ClientRequest.from(copyBucketDefinition.getDestinationRegion(),
                endpointOverride,
                copyBucketDefinition.getSecretToken());
        var s3Client = awsClientProvider.s3AsyncClient(s3ClientRequest);
        var multipartClient = MultipartS3AsyncClient.create(s3Client, multipartConfiguration, true);

        return multipartClient.copyObject(copyRequest)
                .thenApply(response -> {
                    var message = format("Successfully copied S3 object %s/%s to %s/%s.", sourceBucket, sourceKey, destinationBucket, destinationKey);
                    log.info(message);
                    return S3CopyBucketResponse.success(message);
                })
                .exceptionally(throwable -> {
                    var message = format("Exception during S3 copy operation: %s", throwable.getMessage());
                    log.error(message);
                    return S3CopyBucketResponse.error(message);
                });
    }
}
