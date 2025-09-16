package it.eng.connector.integration.s3;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class S3BucketProvisionIntegrationTest extends BaseIntegrationTest {

    private final String bucketName = "test-bucket-test";

    @Autowired
    private S3BucketProvisionService s3BucketProvisionService;

    @Autowired
    private S3ClientProvider s3ClientProvider;

    @Autowired
    private S3ClientService s3ClientService;

    @Test
    public void testS3BucketProvision() {
        log.info("Running S3 bucket provision integration test");

        String fileContent = "Hello, World!";

        BucketCredentialsEntity bucketCredentials = s3BucketProvisionService.createSecureBucket(bucketName);

        // then
        assertDoesNotThrow(() -> {
            ContentDisposition contentDisposition = ContentDisposition.attachment()
                    .filename("test.txt")
                    .build();

            Map<String, String> destinationS3Properties = createS3EndpointProperties("datasetId");

            try (InputStream inputStream = new ByteArrayInputStream(fileContent.getBytes())) {
                s3ClientService.uploadFile(
                                inputStream,
                                destinationS3Properties,
                                MediaType.TEXT_PLAIN_VALUE,
                                contentDisposition.toString())
                        .get();
            } catch (Exception e) {
                log.error("Error uploading file to S3", e);
                fail("File upload failed: " + e.getMessage());
            }
        });
        assertNotNull(bucketCredentials);
        assertNotNull(bucketCredentials.getAccessKey());
        assertNotNull(bucketCredentials.getSecretKey());
        assertEquals(bucketName, bucketCredentials.getBucketName());

        // Create unauthorized user
        String unauthorizedAccessKey = "unauthorized-user";
        String unauthorizedSecretKey = generateSecretKey();
        BucketCredentialsEntity unauthorizedBucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                .accessKey(unauthorizedAccessKey)
                .secretKey(unauthorizedSecretKey)
                .bucketName(bucketName)
                .build();
        // Create client with unauthorized credentials
        S3ClientRequest s3ClientRequest = S3ClientRequest.from(s3Properties.getRegion(),
                null,
                unauthorizedBucketCredentials);
        S3Client unauthorizedClient = s3ClientProvider.s3Client(s3ClientRequest);
        // Test unauthorized access
        Exception exception = assertThrows(S3Exception.class, () -> {
            unauthorizedClient.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key("test.txt")
                    .build());
        });

        // Verify the exception message contains access denied
        assertTrue(exception.getMessage().contains("The Access Key Id you provided does not exist in our records."));
    }

    private String generateSecretKey() {
        return java.util.UUID.randomUUID().toString();
    }

}
