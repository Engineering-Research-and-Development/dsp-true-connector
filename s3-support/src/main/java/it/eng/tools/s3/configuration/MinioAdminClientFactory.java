package it.eng.tools.s3.configuration;

import io.minio.admin.MinioAdminClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and caches {@link MinioAdminClient} instances on demand from explicit credentials.
 *
 * <p>Used by {@link it.eng.tools.s3.service.DynamicIamUserManagementService} on the data plane,
 * where admin credentials are unknown at startup and arrive per-request from CP metadata.
 * One client is reused per unique {@code endpoint|accessKey} combination.</p>
 */
@Component
@Slf4j
public class MinioAdminClientFactory {

    private final OkHttpClient okHttpClient;
    private final ConcurrentHashMap<String, MinioAdminClient> cache = new ConcurrentHashMap<>();

    /**
     * Constructs the factory with the shared HTTP client.
     *
     * @param okHttpClient the HTTP client used for all Minio admin connections
     */
    public MinioAdminClientFactory(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    /**
     * Returns a cached {@link MinioAdminClient} for the given admin credentials.
     * A new instance is created only on the first call for each unique
     * {@code endpoint|accessKey} pair.
     *
     * @param endpoint  the Minio endpoint URL (e.g. {@code http://minio:9000})
     * @param accessKey the admin access key
     * @param secretKey the admin secret key
     * @return a configured and cached {@link MinioAdminClient}
     */
    public MinioAdminClient get(String endpoint, String accessKey, String secretKey) {
        String cacheKey = endpoint + "|" + accessKey;
        return cache.computeIfAbsent(cacheKey, k -> {
            log.info("Creating MinioAdminClient for endpoint={}", endpoint);
            return MinioAdminClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .httpClient(okHttpClient)
                    .build();
        });
    }
}