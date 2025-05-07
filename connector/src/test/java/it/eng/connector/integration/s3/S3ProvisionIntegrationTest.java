package it.eng.connector.integration.s3;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.s3.provision.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.iam.model.GetUserResponse;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class S3ProvisionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private S3BucketProvision s3BucketProvision;

    @Autowired
    private S3Provision s3Provision;

    @Test
    public void copyProvision() throws ExecutionException, InterruptedException {

        S3BucketDefinition s3BucketDefinition = S3BucketDefinition.Builder.newInstance()
                .id("test-id")
                .transferProcessId("test-transfer-process-id")
                .regionId("us-east-1")
                .bucketName("test-bucket-name")
                .endpointOverride("http://localhost:4566")
                .build();

        CompletableFuture<S3ProvisionResponse> bucketProvisionResponse =
                s3BucketProvision.createS3ucketWithPermissions(s3BucketDefinition);

        bucketProvisionResponse.join();
        log.info(String.valueOf(bucketProvisionResponse.get().getCredentials()));

        S3CopyResourceDefinition s3CopyResourceDefinition = S3CopyResourceDefinition.Builder.newInstance()
                .transferProcessId("tp_id_123")
                .endpointOverride("http://localhost:4566")
                .destinationRegion("us-east-1")
                .destinationBucketName(s3BucketDefinition.getBucketName())
                .destinationObjectName("file.txt")
                .destinationKeyName("file.txt")
                .bucketPolicyStatementSid("123")
                .build();
        CompletableFuture<GetBucketPolicyResponse> provisionResponse = s3Provision.copyProvision(s3CopyResourceDefinition);
        // log provisionResponse
        log.info("Provisioning completed successfully {}", provisionResponse.join());
    }

    //    @Test
    public void testProvisionUser() {
        S3BucketDefinition bucketDefinition = S3BucketDefinition.Builder.newInstance()
                .id("test-id")
                .transferProcessId("test-transfer-process-id")
                .regionId("us-east-1")
                .bucketName("test-bucket-name")
                .build();

        GetUserResponse provisionResponse = s3Provision.provision(bucketDefinition).join();
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
