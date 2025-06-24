package it.eng.tools.s3.configuration;

import io.minio.admin.MinioAdminClient;
import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.utils.ThreadFactoryBuilder;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@Slf4j
public class S3Configuration {

    private final S3Properties s3Properties;

    public S3Configuration(S3Properties s3Properties) {
        this.s3Properties = s3Properties;
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(s3Properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())))
                .region(Region.of(s3Properties.getRegion()))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Bean
    public MinioAdminClient minioAdminClient() {
        return MinioAdminClient.builder()
                .endpoint(s3Properties.getEndpoint())
                .credentials(s3Properties.getAccessKey(), s3Properties.getSecretKey())
                .build();
    }

    @Bean
    public S3AsyncClient s3AsyncClient() {
        ExecutorService executor = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder()
                .threadNamePrefix("aws-client")
                .build());

        return S3AsyncClient.builder()
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .endpointOverride(URI.create(s3Properties.getEndpoint()))
                .asyncConfiguration(b -> b.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, executor))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())))
                .region(Region.of(s3Properties.getRegion()))
                .crossRegionAccessEnabled(true)
                .build();
    }
}
