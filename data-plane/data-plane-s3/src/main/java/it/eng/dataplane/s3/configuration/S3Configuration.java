package it.eng.dataplane.s3.configuration;

import io.minio.admin.MinioAdminClient;
import it.eng.dataplane.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for S3 client beans.
 * Creates the {@link MinioAdminClient} when a non-AWS S3 endpoint is configured.
 */
@Configuration
@Slf4j
public class S3Configuration {

    private final S3Properties s3Properties;
    private final OkHttpClient okHttpClient;

    /**
     * Constructs the configuration with required dependencies.
     *
     * @param s3Properties  the S3 configuration properties
     * @param okHttpClient  the HTTP client for MinIO admin calls
     */
    public S3Configuration(S3Properties s3Properties, OkHttpClient okHttpClient) {
        this.s3Properties = s3Properties;
        this.okHttpClient = okHttpClient;
    }

    /**
     * Creates a MinioAdminClient when a non-AWS endpoint is configured.
     *
     * @return the configured MinioAdminClient
     */
    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${s3.endpoint:}') and !('${s3.endpoint:}'.toLowerCase().contains('amazonaws.com'))")
    public MinioAdminClient minioAdminClient() {
        String endpoint = s3Properties.getEndpoint();
        log.info("Creating MinioAdminClient for Minio endpoint: {}", endpoint);
        return MinioAdminClient.builder()
                .endpoint(endpoint)
                .credentials(s3Properties.getAccessKey(), s3Properties.getSecretKey())
                .httpClient(okHttpClient)
                .build();
    }
}
