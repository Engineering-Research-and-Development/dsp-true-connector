package it.eng.tools.s3.configuration;

import it.eng.tools.s3.provision.S3ClientRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3BaseClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.awssdk.utils.ThreadFactoryBuilder;

import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR;

@Component
@Slf4j
public class AwsClientProvider {

    private final Executor executor;
    private final AwsCredentialsProvider credentialsProvider;

    public AwsClientProvider() {
//        credentialsProvider = DefaultCredentialsProvider.create();
        credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials
                //minioadmin
                .create("accessKeyId", "accessKeySecret"));
        this.executor = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder()
                .threadNamePrefix("aws-client")
                .build());
    }

    public S3Client s3Client(S3ClientRequest s3ClientRequest) {
        return createS3Client(s3ClientRequest);
    }

    public S3AsyncClient s3AsyncClient(S3ClientRequest clientRequest) {
        return createS3AsyncClient(clientRequest);
    }

    public IamAsyncClient iamAsyncClient(S3ClientRequest clientRequest) {
//        var key = clientRequest.endpointOverride() != null ? clientRequest.endpointOverride() : NO_ENDPOINT_OVERRIDE;
        return createIamAsyncClient(clientRequest.endpointOverride());
    }

    public StsAsyncClient stsAsyncClient(S3ClientRequest clientRequest) {
        var key = clientRequest.region() + "/" + clientRequest.endpointOverride();
        return createStsClient(clientRequest.region(), clientRequest.endpointOverride());
    }

    private S3Client createS3Client(S3ClientRequest s3ClientRequest) {
        var token = s3ClientRequest.secretToken();
        var region = s3ClientRequest.region();
        var endpointOverride = s3ClientRequest.endpointOverride();

        if (token != null) {
            AwsCredentials credentials = AwsBasicCredentials.create(token.getAccessKey(), token.getAccessSecret());
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
                        .build())
                .endpointOverride(URI.create("http://localhost:4566"));

        return builder.build();
    }

    private S3AsyncClient createS3AsyncClient(S3ClientRequest s3ClientRequest) {
        var token = s3ClientRequest.secretToken();
        var region = s3ClientRequest.region();
        var endpointOverride = s3ClientRequest.endpointOverride();

        if (token != null) {
            var credentials = AwsBasicCredentials.create(token.getAccessKey(), token.getAccessSecret());
            return createS3AsyncClient(StaticCredentialsProvider.create(credentials), region, endpointOverride);
        } else {
            var key = s3ClientRequest.region() + "/" + s3ClientRequest.endpointOverride();
            return createS3AsyncClient(credentialsProvider, region, endpointOverride);
        }
    }

    private IamAsyncClient createIamAsyncClient(String endpointOverride) {
        var builder = IamAsyncClient.builder()
                .asyncConfiguration(b -> b.advancedOption(FUTURE_COMPLETION_EXECUTOR, executor))
                .credentialsProvider(credentialsProvider)
                .region(Region.AWS_GLOBAL);

        handleEndpointOverride(builder, endpointOverride);

        return builder.build();
    }

    private void handleEndpointOverride(SdkClientBuilder<?, ?> builder, String endpointOverride) {

        // either take override from parameter, or from config, or null
        var uri = URI.create(endpointOverride);
        builder.endpointOverride(uri);
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

    private StsAsyncClient createStsClient(String region, String endpointOverride) {
        var builder = StsAsyncClient.builder()
                .asyncConfiguration(b -> b.advancedOption(FUTURE_COMPLETION_EXECUTOR, executor))
                .credentialsProvider(credentialsProvider)
                .region(Region.of(region));

        handleEndpointOverride(builder, endpointOverride);

        return builder.build();
    }

    private void handleBaseEndpointOverride(S3BaseClientBuilder<?, ?> builder, String endpointOverride) {
        URI endpointOverrideUri;

        if (StringUtils.isNotBlank(endpointOverride)) {
            endpointOverrideUri = URI.create(endpointOverride);
        } else {
            endpointOverrideUri = URI.create("http://localhost:4566");
        }

        if (endpointOverrideUri != null) {
            builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .endpointOverride(endpointOverrideUri);
        }
    }
}
