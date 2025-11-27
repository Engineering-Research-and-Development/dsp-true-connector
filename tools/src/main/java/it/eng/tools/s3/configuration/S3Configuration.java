package it.eng.tools.s3.configuration;

import io.minio.admin.MinioAdminClient;
import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
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

    @Bean
    public MinioAdminClient minioAdminClient() {
        log.debug("Creating MinioAdminClient with OkHttpClient from OkHttpClientConfiguration");
        return MinioAdminClient.builder()
                .endpoint(s3Properties.getEndpoint())
                .credentials(s3Properties.getAccessKey(), s3Properties.getSecretKey())
                .httpClient(okHttpClient)
                .build();
    }
}
