package it.eng.connector.integration.s3;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3BaseClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.awssdk.utils.ThreadFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR;

public class UploadToBucketIntegrationTest {

    String presignedUrl = "http://localhost:9000/source-bucket/spring-tool-suite-4-4.29.1.RELEASE-e4.35.0-win32.win32.x86_64.zip?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20250523T090130Z&X-Amz-SignedHeaders=host&X-Amz-Credential=new-minioadmin%2F20250523%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Expires=3600&X-Amz-Signature=01a7e631b206775030a97cc8b7fa5a7c63e827e85f3d2b3d325134c036ca0d54";

    @Test
    public void getMinioPresignedUrl() {
        // Test data
        String bucketName = "source-bucket";
        String key = "spring-tool-suite-4-4.29.1.RELEASE-e4.35.0-win32.win32.x86_64.zip";

        // MinIO credentials
        String sourceAccessKeyId = "new-minioadmin";
        String sourceSecretAccessKey = "minioadmin";

        // Create credentials provider
        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(sourceAccessKeyId, sourceSecretAccessKey));

        // Create S3Presigner
        S3Presigner presigner = S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of("us-east-1"))
                .endpointOverride(URI.create("http://localhost:9000"))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        // Create GetObjectRequest
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        // Create GetObjectPresignRequest
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(60)) // URL expires in 60 minutes
                .getObjectRequest(getObjectRequest)
                .build();

        // Generate the presigned URL
        String presignedUrl = presigner.presignGetObject(presignRequest)
                .url()
                .toString();

        System.out.println("Presigned URL: " + presignedUrl);
        assertNotNull(presignedUrl);
        assertTrue(presignedUrl.contains(bucketName));
        assertTrue(presignedUrl.contains(key));

        // Don't forget to close the presigner
        presigner.close();
    }

    @Test
    void testDirectUpload() {
        // Test data
        String bucketName = "destination-bucket";
        String key = "test-file.zip";

        String sourceAccessKeyId = "new-minioadmin";
        String sourceSecretAccessKey = "minioadmin";

        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials
                .create(sourceAccessKeyId, sourceSecretAccessKey));
        var s3AsyncClient = createS3AsyncClient(credentialsProvider, "us-east-1", "http://localhost:9000");

        try (InputStream inputStream = S3PresignedUrlReader.getInputStreamFromPresignedUrl(presignedUrl)) {
            S3DirectUploader uploader = new S3DirectUploader(s3AsyncClient);
//            uploader.uploadStream(inputStream, bucketName, key)
//                    .join();

            // For large files using multipart upload
//            InputStream largeInputStream = new ByteArrayInputStream(data.getBytes());
            String eTag = uploader.uploadLargeStream(inputStream, bucketName, key).join();
            assertNotNull(eTag);
        } catch (IOException e) {
            throw new RuntimeException("Failed to process stream", e);
        }
    }

    private S3AsyncClient createS3AsyncClient(AwsCredentialsProvider credentialsProvider, String region, String endpointOverride) {
        var executor = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder()
                .threadNamePrefix("aws-client")
                .build());

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
            endpointOverrideUri = URI.create("http://localhost:4566");
        }

        if (endpointOverrideUri != null) {
            builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .endpointOverride(endpointOverrideUri);
        }
    }
}
