package it.eng.tools.s3.configuration;

import java.net.URI;

import it.eng.tools.s3.properties.S3Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Configuration {
    
    private final S3Properties s3Properties;
    
    public S3Configuration(S3Properties s3Properties) {
        this.s3Properties = s3Properties;
    }
    
    @Bean
    public S3Client s3Client() {
        String endpoint = s3Properties.getEndpoint();
        String accessKey = s3Properties.getAccessKey();
        String secretKey = s3Properties.getSecretKey();
        String region = s3Properties.getRegion();
        
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .region(Region.of(region))
            .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .build();
    }
}
