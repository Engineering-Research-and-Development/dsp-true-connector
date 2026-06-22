package it.eng.tools.s3.configuration;

import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
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
 * Data-plane S3 client factory. Active when {@code s3.access-key} is not configured.
 *
 * <p>Creates S3 clients on demand from credentials carried in each {@link S3ClientRequest}.
 * No bootstrap credentials are required at startup — this factory is safe to instantiate
 * when no {@code s3.*} properties are set. Clients are cached by
 * {@code bucketName|accessKey|region|endpointOverride} so repeated calls with the same
 * target bucket and endpoint reuse the same instance.</p>
 *
 * <p>Every call to {@link #getClient(S3ClientRequest)} and {@link #getAsyncClient(S3ClientRequest)}
 * requires {@link S3ClientRequest#bucketCredentials()} to be non-null. A null value is a
 * programming error in the data-plane request path and throws {@link IllegalStateException}.</p>
 */
@Slf4j
public class DynamicS3ClientFactory implements S3ClientFactory {

    private final SdkHttpClient sdkHttpClient;
    private final SdkAsyncHttpClient sdkAsyncHttpClient;
    private final Executor executor;

    private final ConcurrentHashMap<String, S3Client> clientCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, S3AsyncClient> asyncClientCache = new ConcurrentHashMap<>();

    /**
     * Creates the dynamic factory. No S3 properties are required.
     *
     * @param sdkHttpClient      synchronous HTTP client (provided by {@link S3HttpClientFactory})
     * @param sdkAsyncHttpClient asynchronous HTTP client (provided by {@link S3HttpClientFactory})
     */
    public DynamicS3ClientFactory(SdkHttpClient sdkHttpClient,
                                   SdkAsyncHttpClient sdkAsyncHttpClient) {
        this.sdkHttpClient = sdkHttpClient;
        this.sdkAsyncHttpClient = sdkAsyncHttpClient;
        this.executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                .threadNamePrefix("dp-s3-client")
                .build());
        log.info("DynamicS3ClientFactory initialized — data-plane mode, no bootstrap S3 credentials");
    }

    /**
     * Returns a cached synchronous S3 client built from the explicit credentials in the request.
     *
     * @param request the S3 client request; {@code bucketCredentials()} must not be null
     * @return a configured {@link S3Client}
     * @throws IllegalStateException if {@code request.bucketCredentials()} is null
     */
    @Override
    public S3Client getClient(S3ClientRequest request) {
        log.info("Fetching S3 client from Dynamic S3 client for request={}", request);
        String cacheKey = cacheKey(request);
        return clientCache.computeIfAbsent(cacheKey, k -> createSyncClient(request));
    }

    /**
     * Returns a cached asynchronous S3 client built from the explicit credentials in the request.
     *
     * @param request the S3 client request; {@code bucketCredentials()} must not be null
     * @return a configured {@link S3AsyncClient}
     * @throws IllegalStateException if {@code request.bucketCredentials()} is null
     */
    @Override
    public S3AsyncClient getAsyncClient(S3ClientRequest request) {
        String cacheKey = cacheKey(request);
        return asyncClientCache.computeIfAbsent(cacheKey, k -> createAsyncClient(request));
    }

    /**
     * Clears cached clients for the given bucket so the next call creates a fresh instance.
     *
     * @param bucketName the bucket whose cached clients should be evicted
     */
    @Override
    public void clearBucketCache(String bucketName) {
        String prefix = bucketName + "|";
        clientCache.keySet().removeIf(k -> k.startsWith(prefix));
        asyncClientCache.keySet().removeIf(k -> k.startsWith(prefix));
        log.info("Cleared dynamic S3 client cache for bucket: {}", bucketName);
    }

    private S3Client createSyncClient(S3ClientRequest request) {
        BucketCredentialsEntity creds = requireCredentials(request);
        log.info("Creating dynamic S3Client — bucket: {}, region: {}, endpoint: {}",
                creds.getBucketName(), request.region(), request.endpointOverride());
        var credProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(creds.getAccessKey(), creds.getSecretKey()));
        var builder = S3Client.builder()
                .httpClient(sdkHttpClient)
                .credentialsProvider(credProvider)
                .region(Region.of(request.region()));
        applyEndpoint(builder, request.endpointOverride());
        return builder.build();
    }

    private S3AsyncClient createAsyncClient(S3ClientRequest request) {
        BucketCredentialsEntity creds = requireCredentials(request);
        log.info("Creating dynamic S3AsyncClient — bucket: {}, region: {}, endpoint: {}",
                creds.getBucketName(), request.region(), request.endpointOverride());
        var credProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(creds.getAccessKey(), creds.getSecretKey()));
        var builder = S3AsyncClient.builder()
                .httpClient(sdkAsyncHttpClient)
                .asyncConfiguration(b -> b.advancedOption(FUTURE_COMPLETION_EXECUTOR, executor))
                .credentialsProvider(credProvider)
                .region(Region.of(request.region()))
                .crossRegionAccessEnabled(true);
        applyEndpoint(builder, request.endpointOverride());
        return builder.build();
    }

    private void applyEndpoint(S3BaseClientBuilder<?, ?> builder, String endpointOverride) {
        if (StringUtils.isNotBlank(endpointOverride) && !isAwsEndpoint(endpointOverride)) {
            builder.serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .endpointOverride(URI.create(endpointOverride));
        } else {
            builder.serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                    .pathStyleAccessEnabled(false)
                    .build());
        }
    }

    private BucketCredentialsEntity requireCredentials(S3ClientRequest request) {
        BucketCredentialsEntity creds = request.bucketCredentials();
        if (creds == null) {
            throw new IllegalStateException(
                    "S3ClientRequest must carry explicit BucketCredentialsEntity in data-plane mode — "
                            + "DynamicS3ClientFactory has no bootstrap credentials");
        }
        return creds;
    }

    private String cacheKey(S3ClientRequest request) {
        BucketCredentialsEntity creds = request.bucketCredentials();
        if (creds == null) {
            return request.region() + "|" + request.endpointOverride();
        }
        return creds.getBucketName()
                + "|" + creds.getAccessKey()
                + "|" + request.region()
                + "|" + request.endpointOverride();
    }

    private boolean isAwsEndpoint(String endpoint) {
        if (StringUtils.isBlank(endpoint)) {
            return true;
        }
        String lower = endpoint.toLowerCase();
        return lower.contains(".amazonaws.com") || lower.contains(".aws.");
    }
}
