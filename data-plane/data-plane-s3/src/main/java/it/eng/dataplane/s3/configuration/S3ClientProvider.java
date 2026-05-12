package it.eng.dataplane.s3.configuration;

import it.eng.dataplane.s3.model.S3ClientRequest;
import it.eng.dataplane.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
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

/**
 * Provider for S3 client instances.
 * Maintains separate admin and per-bucket client caches.
 */
@Service
@Slf4j
public class S3ClientProvider {

    private final Executor executor;
    private final S3Properties s3Properties;
    private final AwsCredentialsProvider credentialsProvider;
    private final SdkHttpClient sdkHttpClient;
    private final SdkAsyncHttpClient sdkAsyncHttpClient;

    private final ConcurrentHashMap<String, S3Client> s3ClientCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, S3AsyncClient> asyncS3ClientCache = new ConcurrentHashMap<>();
    private final ThreadLocal<S3Client> adminS3ClientCache = ThreadLocal.withInitial(() -> null);

    /**
     * Constructs the provider.
     *
     * @param s3Properties        the S3 configuration properties
     * @param sdkHttpClient       the synchronous HTTP client
     * @param sdkAsyncHttpClient  the asynchronous HTTP client
     */
    public S3ClientProvider(
            S3Properties s3Properties,
            SdkHttpClient sdkHttpClient,
            SdkAsyncHttpClient sdkAsyncHttpClient) {
        this.s3Properties = s3Properties;
        this.sdkHttpClient = sdkHttpClient;
        this.sdkAsyncHttpClient = sdkAsyncHttpClient;
        this.executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                .threadNamePrefix("aws-client")
                .build());
        credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials
                .create(s3Properties.getAccessKey(), s3Properties.getSecretKey()));
    }

    private boolean isAwsEndpoint(String endpoint) {
        if (StringUtils.isBlank(endpoint)) {
            return true;
        }
        String lower = endpoint.toLowerCase();
        return lower.contains(".amazonaws.com") || lower.contains(".aws.");
    }

    /**
     * Creates or returns a cached S3Client for administrative operations.
     *
     * @return a configured administrative S3Client instance
     */
    public S3Client adminS3Client() {
        S3Client cached = adminS3ClientCache.get();
        if (cached == null) {
            String endpoint = s3Properties.getEndpoint();
            boolean isAws = isAwsEndpoint(endpoint);
            log.info("Creating admin S3Client - AWS mode: {}, endpoint: {}, region: {}",
                    isAws, endpoint, s3Properties.getRegion());
            var builder = S3Client.builder()
                    .httpClient(sdkHttpClient)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())))
                    .region(Region.of(s3Properties.getRegion()));
            if (isAws) {
                builder.serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(false)
                        .build());
            } else {
                builder.serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                        .endpointOverride(URI.create(endpoint));
            }
            cached = builder.build();
            adminS3ClientCache.set(cached);
        }
        return cached;
    }

    /**
     * Returns a cached or new S3Client scoped to the given request's bucket credentials.
     *
     * @param s3ClientRequest the request containing bucket credentials, region, and endpoint override
     * @return a configured S3Client instance
     */
    public S3Client s3Client(S3ClientRequest s3ClientRequest) {
        String cacheKey = buildCacheKey(s3ClientRequest);
        return s3ClientCache.computeIfAbsent(cacheKey, k -> createS3Client(s3ClientRequest));
    }

    /**
     * Returns a cached or new S3AsyncClient scoped to the given request's bucket credentials.
     *
     * @param s3ClientRequest the request containing bucket credentials, region, and endpoint override
     * @return a configured S3AsyncClient instance
     */
    public S3AsyncClient s3AsyncClient(S3ClientRequest s3ClientRequest) {
        String cacheKey = buildCacheKey(s3ClientRequest);
        return asyncS3ClientCache.computeIfAbsent(cacheKey, k -> createS3AsyncClient(s3ClientRequest));
    }

    /**
     * Clears cached S3 clients for a specific bucket.
     *
     * @param bucketName the name of the bucket to clear from cache
     */
    public void clearBucketCache(String bucketName) {
        log.info("Clearing S3 client cache for bucket: {}", bucketName);
        String prefix = bucketName + "|";
        s3ClientCache.keySet().removeIf(k -> k.startsWith(prefix));
        asyncS3ClientCache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildCacheKey(S3ClientRequest s3ClientRequest) {
        var creds = s3ClientRequest.bucketCredentials();
        if (creds == null) {
            return s3ClientRequest.region() + "|" + s3ClientRequest.endpointOverride();
        }
        return creds.getBucketName() + "|" + creds.getAccessKey();
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
        log.info("Creating S3Client with region: {}, endpointOverride: {}", region, endpointOverride);
        var builder = S3Client.builder()
                .httpClient(sdkHttpClient)
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
            return createS3AsyncClient(credentialsProvider, region, endpointOverride);
        }
    }

    private S3AsyncClient createS3AsyncClient(AwsCredentialsProvider credentialsProvider, String region, String endpointOverride) {
        var builder = S3AsyncClient.builder()
                .httpClient(sdkAsyncHttpClient)
                .asyncConfiguration(b -> b.advancedOption(FUTURE_COMPLETION_EXECUTOR, executor))
                .credentialsProvider(credentialsProvider)
                .region(Region.of(region))
                .crossRegionAccessEnabled(true);
        handleBaseEndpointOverride(builder, endpointOverride);
        return builder.build();
    }

    private void handleBaseEndpointOverride(S3BaseClientBuilder<?, ?> builder, String endpointOverride) {
        if (isAwsEndpoint(endpointOverride)) {
            log.debug("Configuring for AWS S3 (virtual-hosted style)");
            builder.serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                    .pathStyleAccessEnabled(false)
                    .build());
        } else {
            URI uri = URI.create(StringUtils.isNotBlank(endpointOverride)
                    ? endpointOverride
                    : s3Properties.getEndpoint());
            log.debug("Configuring for Minio (path-style), endpoint: {}", uri);
            builder.serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build())
                    .endpointOverride(uri);
        }
    }
}
