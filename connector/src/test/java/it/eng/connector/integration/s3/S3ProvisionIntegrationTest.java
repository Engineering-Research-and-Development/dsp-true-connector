package it.eng.connector.integration.s3;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.s3.provision.S3BucketProvision;
import it.eng.tools.s3.provision.S3CopyBucketProvision;
import it.eng.tools.s3.provision.model.S3BucketDefinition;
import it.eng.tools.s3.provision.model.S3CopyResourceDefinition;
import it.eng.tools.s3.provision.model.S3ProvisionResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@Slf4j
public class S3ProvisionIntegrationTest extends BaseIntegrationTest {

    private static final DockerImageName LOCALSTACK_DOCKER_IMAGE = DockerImageName.parse("localstack/localstack:4.2.0");
    private static final String region = "eu-central-1";
    public static final String DESTINATION_BUCKET_NAME = "destination-bucket-name";

    @Autowired
    private S3BucketProvision s3BucketProvision;

    @Autowired
    private S3CopyBucketProvision s3Provision;

    @Container
    private static final LocalStackContainer LOCALSTACK_CONTAINER = new LocalStackContainer(LOCALSTACK_DOCKER_IMAGE)
            .withServices(
                    S3,
                    LocalStackContainer.Service.IAM,
                    LocalStackContainer.Service.STS)
            .withEnv("DEFAULT_REGION", region)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(S3ProvisionIntegrationTest.class)));


    @DynamicPropertySource
    static void containersProperties(DynamicPropertyRegistry registry) {
//        registry.add("s3.endpoint", LOCALSTACK_CONTAINER::getEndpoint);
        registry.add("s3.endpoint", () -> LOCALSTACK_CONTAINER.getEndpointOverride(S3).toString());
    }


    @Test
    public void copyProvision() throws ExecutionException, InterruptedException {
        // create destination bucket
        S3BucketDefinition s3BucketDefinition = S3BucketDefinition.Builder.newInstance()
                .id("test-id")
                .transferProcessId("test-transfer-process-id")
                .regionId("us-east-1")
                .bucketName(DESTINATION_BUCKET_NAME)
                .endpointOverride(LOCALSTACK_CONTAINER.getEndpoint().toString())
                .build();

        CompletableFuture<S3ProvisionResponse> bucketProvisionResponse =
                s3BucketProvision.createS3BucketWithPermissions(s3BucketDefinition);

        S3ProvisionResponse response = bucketProvisionResponse.join();

        log.info(response.toString());
/*
        S3CopyResourceDefinition s3CopyResourceDefinition = S3CopyResourceDefinition.Builder.newInstance()
                .transferProcessId("tp_id_123")
                .endpointOverride(LOCALSTACK_CONTAINER.getEndpoint().toString())
                .destinationRegion(region)
                .destinationBucketName(DESTINATION_BUCKET_NAME)
                .destinationObjectName("destination_file.txt")
                .destinationKeyName("destination_file.txt")
                .bucketPolicyStatementSid("123")
                .build();
        CompletableFuture<GetBucketPolicyResponse> provisionResponse = s3Provision.copyProvision(s3CopyResourceDefinition);
        // log provisionResponse
        log.info("Provisioning completed successfully {}", provisionResponse.join());

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(DESTINATION_BUCKET_NAME)
                .key("file.txt")
                .build();

        StaticCredentialsProvider staticCredentialsProvider = StaticCredentialsProvider.create(
                AwsSessionCredentials.create(response.getCredentials().accessKeyId(), response.getCredentials().secretAccessKey(),
                        response.getCredentials().sessionToken()));

        try (S3Client s3 = S3Client.builder()
                .credentialsProvider(staticCredentialsProvider)
                .region(Region.of(region))
                .endpointOverride(LOCALSTACK_CONTAINER.getEndpoint())
                .forcePathStyle(true)
                .build()) {
            ResponseInputStream<GetObjectResponse> getObjectResponse = s3.getObject(getObjectRequest);
            log.info("Object response {}", getObjectResponse.response().metadata());
            String copiedContent = getObjectResponse.readAllBytes().toString();
            log.info("Copied content: {}", copiedContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("File copied successfully from source to destination bucket.");
*/
    }

    //    @Test
    public void testProvisionUser() {
        S3CopyResourceDefinition s3CopyResourceDefinition = S3CopyResourceDefinition.Builder.newInstance()
                .transferProcessId("tp_id_123")
                .endpointOverride(LOCALSTACK_CONTAINER.getEndpoint().toString())
                .destinationRegion(region)
                .destinationBucketName(DESTINATION_BUCKET_NAME)
                .destinationObjectName("destination_file.txt")
                .destinationKeyName("destination_file.txt")
                .bucketPolicyStatementSid("123")
                .build();

        S3ProvisionResponse provisionResponse = s3Provision.copyProvision(s3CopyResourceDefinition).join();
        // log provisionResponse
        log.info("Provisioning completed successfully {}", provisionResponse);
    }

    private boolean provisionSucceed(S3BucketDefinition resourceDefinition, Role role, Credentials credentials) {
        log.info("Role: {}", role);
        log.info("Credentials: {}", credentials);
        log.info("ResourceDefinition: {}", resourceDefinition);
//        var secretToken = new AwsTemporarySecretToken(credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken(), credentials.expiration().toEpochMilli());

//        var response = ProvisionResponse.Builder.newInstance().resource(resource).secretToken(secretToken).build();
        return true;
    }
}
