package it.eng.tools.s3.configuration;

import io.minio.admin.MinioAdminClient;
import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class S3Configuration {

    private final S3Properties s3Properties;
    private final OkHttpClient okHttpClient;

    public S3Configuration(S3Properties s3Properties, OkHttpClient okHttpClient) {
        this.s3Properties = s3Properties;
        this.okHttpClient = okHttpClient;
    }

    /**
     * MinioAdminClient is only created when s3.endpoint is set and is not an AWS endpoint.
     *
     * @return the configured MinioAdminClient or null when AWS S3 is detected
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
