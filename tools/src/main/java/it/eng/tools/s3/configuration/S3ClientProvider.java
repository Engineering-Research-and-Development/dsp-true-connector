package it.eng.tools.s3.configuration;

import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3BaseClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.utils.ThreadFactoryBuilder;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR;

@Service
@Slf4j
public class S3ClientProvider {

    private final Executor executor;
    private final S3Properties s3Properties;
    private final AwsCredentialsProvider credentialsProvider;

    private final ConcurrentHashMap<String, S3Client> s3ClientCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, S3AsyncClient> asyncS3ClientCache = new ConcurrentHashMap<>();
    private final ThreadLocal<S3Client> adminS3ClientCache = ThreadLocal.withInitial(() -> null);


    public S3ClientProvider(S3Properties s3Properties) {
        this.s3Properties = s3Properties;
        this.executor = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder()
                .threadNamePrefix("aws-client")
                .build());
        credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials
                .create(s3Properties.getAccessKey(), s3Properties.getSecretKey()));
    }

    /**
     * Creates a S3Client for administrative operations on S3 buckets.
     * This client is used for operations that require admin privileges.
     * AccessKey and SecretKey are read from the S3Properties configuration.
     *
     * @return a configured administrative privileges S3Client instance
     */
    public S3Client adminS3Client() {
        S3Client cached = adminS3ClientCache.get();
        if (cached == null) {
            cached = S3Client.builder()
                    .endpointOverride(URI.create(s3Properties.getEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())))
                    .region(Region.of(s3Properties.getRegion()))
                    .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
            adminS3ClientCache.set(cached);
        }
        return cached;
    }

    /**
     * Creates a S3Client for general operations on S3 buckets.
     * This client is used for operations that do not require admin privileges.
     * AccessKey and SecretKey are read from the S3ClientRequest.
     *
     * @param s3ClientRequest the request containing bucket credentials, region, and endpoint override
     * @return a configured general privileges S3Client instance
     */
    public S3Client s3Client(S3ClientRequest s3ClientRequest) {
        String bucketName = s3ClientRequest.bucketCredentials().getBucketName();
        return s3ClientCache.computeIfAbsent(bucketName, bn -> createS3Client(s3ClientRequest));
    }

    /**
     * Creates a S3AsyncClient for asynchronous operations on S3 buckets.
     * This client is used for operations that do not require admin privileges.
     * AccessKey and SecretKey are read from the S3ClientRequest.
     *
     * @param s3ClientRequest the request containing bucket credentials, region, and endpoint override
     * @return a configured general privileges S3AsyncClient instance
     */
    public S3AsyncClient s3AsyncClient(S3ClientRequest s3ClientRequest) {
        String bucketName = s3ClientRequest.bucketCredentials().getBucketName();
        return asyncS3ClientCache.computeIfAbsent(bucketName, bn -> createS3AsyncClient(s3ClientRequest));
    }

    private S3Client createS3Client(S3ClientRequest s3ClientRequest) {
        var bucketCredentials = s3ClientRequest.bucketCredentials();
        var region = s3ClientRequest.region();
        var endpointOverride = s3ClientRequest.endpointOverride();

        if (bucketCredentials != null) {
            AwsCredentials credentials = AwsBasicCredentials.create(bucketCredentials.getAccessKey(), bucketCredentials.getSecretKey());
            return createS3Client(StaticCredentialsProvider.create(credentials), region, endpointOverride);
        } else {
            return createS3Client(credentialsProvider, region, endpointOverride);
        }
    }

    private S3Client createS3Client(AwsCredentialsProvider credentialsProvider, String region, String endpointOverride) {
        var builder = S3Client.builder()
//                .credentialsProvider(credentialsProvider)
//                .region(Region.of(region));
                .credentialsProvider(credentialsProvider)
                .region(Region.of(region))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build());

        handleBaseEndpointOverride(builder, endpointOverride);

        return builder.build();
    }

    private S3AsyncClient createS3AsyncClient(S3ClientRequest s3ClientRequest) {
        var bucketCredentials = s3ClientRequest.bucketCredentials();
        var region = s3ClientRequest.region();
        var endpointOverride = s3ClientRequest.endpointOverride();

        if (bucketCredentials != null) {
            var credentials = AwsBasicCredentials.create(bucketCredentials.getAccessKey(), bucketCredentials.getSecretKey());
            return createS3AsyncClient(StaticCredentialsProvider.create(credentials), region, endpointOverride);
        } else {
            var key = s3ClientRequest.region() + "/" + s3ClientRequest.endpointOverride();
            return createS3AsyncClient(credentialsProvider, region, endpointOverride);
        }
    }

    private S3AsyncClient createS3AsyncClient(AwsCredentialsProvider credentialsProvider, String region, String endpointOverride) {
        var builder = S3AsyncClient.builder()
                .asyncConfiguration(b -> b.advancedOption(FUTURE_COMPLETION_EXECUTOR, executor))
                .credentialsProvider(credentialsProvider)
                .region(Region.of(region))
                .crossRegionAccessEnabled(true);

        handleBaseEndpointOverride(builder, endpointOverride);

        return builder.build();
    }

    private void handleBaseEndpointOverride(S3BaseClientBuilder<?, ?> builder, String endpointOverride) {
        URI endpointOverrideUri;

        if (StringUtils.isNotBlank(endpointOverride)) {
            endpointOverrideUri = URI.create(endpointOverride);
        } else {
            endpointOverrideUri = URI.create(s3Properties.getEndpoint());
        }

        builder.serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .endpointOverride(endpointOverrideUri);
    }

}
