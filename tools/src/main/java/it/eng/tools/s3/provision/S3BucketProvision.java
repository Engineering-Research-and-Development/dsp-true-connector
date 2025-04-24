package it.eng.tools.s3.provision;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import it.eng.tools.s3.configuration.AwsClientProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.iam.IamAsyncClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

@Service
@Slf4j
public class S3BucketProvision {

    private static final String ASSUME_ROLE_TRUST = "{" +
            "  \"Version\": \"2012-10-17\"," +
            "  \"Statement\": [" +
            "    {" +
            "      \"Effect\": \"Allow\"," +
            "      \"Principal\": {" +
            "        \"AWS\": \"%s\"" +
            "      }," +
            "      \"Action\": \"sts:AssumeRole\"" +
            "    }" +
            "  ]" +
            "}";

    private static final String BUCKET_POLICY = "{" +
            "    \"Version\": \"2012-10-17\"," +
            "    \"Statement\": [" +
            "        {" +
            "            \"Sid\": \"TemporaryAccess\", " +
            "            \"Effect\": \"Allow\"," +
            "            \"Action\": \"s3:PutObject\"," +
            "            \"Resource\": \"arn:aws:s3:::%s/*\"" +
            "        }" +
            "    ]" +
            "}";

    private final RetryPolicy retryPolicy;

    private final AwsClientProvider awsClientProvider;

    public S3BucketProvision(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
        this.retryPolicy = RetryPolicy.builder()
                .handle(Exception.class)
                .withMaxRetries(3)
                .build();
    }

    public CompletableFuture<S3ProvisionResponse> createS3ucketWithPermissions(S3BucketDefinition resourceDefinition) {

        var rq = S3ClientRequest.from(resourceDefinition.getRegionId(), resourceDefinition.getEndpointOverride());
        var s3AsyncClient = awsClientProvider.s3AsyncClient(rq);
        var iamClient = awsClientProvider.iamAsyncClient(rq);
        var stsClient = awsClientProvider.stsAsyncClient(rq);

        var request = CreateBucketRequest.builder()
                .bucket(resourceDefinition.getBucketName())
                .createBucketConfiguration(CreateBucketConfiguration.builder().build())
                .build();

        return s3AsyncClient.createBucket(request)
                .thenCompose(r -> getUser(iamClient))
                .thenCompose(response -> createRole(iamClient, resourceDefinition, response))
                .thenCompose(response -> createRolePolicy(iamClient, resourceDefinition, response))
                .thenCompose(role -> assumeRole(stsClient, role));
    }

    private CompletableFuture<GetUserResponse> getUser(IamAsyncClient iamAsyncClient) {
        return Failsafe.with(retryPolicy).getStageAsync(() -> {
            log.info("S3ProvisionPipeline: get user");
            return iamAsyncClient.getUser();
        });
    }

    private CompletableFuture<CreateRoleResponse> createRole(IamAsyncClient iamClient, S3BucketDefinition resourceDefinition, GetUserResponse response) {
        return Failsafe.with(retryPolicy).getStageAsync(() -> {
            String userArn = response.user().arn();
            Tag tag = Tag.builder().key("dataspaceconnector:process").value(resourceDefinition.getTransferProcessId()).build();

            log.info("S3ProvisionPipeline: create role for user " + userArn);
            CreateRoleRequest createRoleRequest = CreateRoleRequest.builder()
                    .roleName(resourceDefinition.getTransferProcessId()).description("EDC transfer process role")
                    .assumeRolePolicyDocument(format(ASSUME_ROLE_TRUST, userArn))
                    .maxSessionDuration(5000)
                    .tags(tag)
                    .build();

            return iamClient.createRole(createRoleRequest);
        });
    }

    private CompletableFuture<Role> createRolePolicy(IamAsyncClient iamAsyncClient, S3BucketDefinition resourceDefinition, CreateRoleResponse response) {
        return Failsafe.with(retryPolicy).getStageAsync(() -> {
            Role role = response.role();
            PutRolePolicyRequest policyRequest = PutRolePolicyRequest.builder()
                    .policyName(resourceDefinition.getTransferProcessId())
                    .roleName(role.roleName())
                    .policyDocument(format(BUCKET_POLICY, resourceDefinition.getBucketName()))
                    .build();

            log.info("S3ProvisionPipeline: attach bucket policy to role " + role.arn());
            return iamAsyncClient.putRolePolicy(policyRequest)
                    .thenApply(policyResponse -> role);
        });
    }

    private CompletableFuture<S3ProvisionResponse> assumeRole(StsAsyncClient stsClient, Role role) {
        return Failsafe.with(retryPolicy).getStageAsync(() -> {
            log.info("S3ProvisionPipeline: attempting to assume the role");
            AssumeRoleRequest roleRequest = AssumeRoleRequest.builder()
                    .roleArn(role.arn())
                    .roleSessionName("transfer")
                    .externalId("123")
                    .build();

            return stsClient.assumeRole(roleRequest)
                    .thenApply(response -> new S3ProvisionResponse(role, response.credentials()));
        });
    }
}