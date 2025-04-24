package it.eng.tools.s3.provision;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import it.eng.tools.s3.configuration.AwsClientProvider;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.iam.IamAsyncClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;


@Service
@Slf4j
public class S3Provision {

    private RetryPolicy retryPolicy = RetryPolicy.ofDefaults();

    // Do not modify this trust policy
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

    private final AwsClientProvider awsClientProvider;

    public S3Provision(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
    }

    public CompletableFuture<GetBucketPolicyResponse> copyProvision(S3CopyResourceDefinition resourceDefinition) {
        var sourceClientRequest = S3ClientRequest.from("us-east-1", resourceDefinition.getEndpointOverride(), null);

        var iamClient = awsClientProvider.iamAsyncClient(sourceClientRequest);
        var stsClient = awsClientProvider.stsAsyncClient(sourceClientRequest);
        // here should go destinationRequest
        //        var destinationClientRequest = S3ClientRequest.from(resourceDefinition.getDestinationRegion(), resourceDefinition.getEndpointOverride(), secretTokenResult.getContent());
        var s3Client = awsClientProvider.s3AsyncClient(sourceClientRequest);

        return iamClient.getUser()
                .thenCompose(response -> createRole(iamClient, resourceDefinition, response))
                .thenCompose(response -> putRolePolicy(iamClient, resourceDefinition, response))
                .thenCompose(provisionSteps -> getBucketPolicy(s3Client, resourceDefinition, provisionSteps));
//                .thenCompose(provisionSteps -> updateBucketPolicy(s3Client, resourceDefinition, provisionSteps));
//				.thenCompose(role -> assumeRole(stsClient, resourceDefinition, role));

    }

    private CompletableFuture<CreateRoleResponse> createRole(IamAsyncClient iamClient,
                                                             S3CopyResourceDefinition resourceDefinition,
                                                             GetUserResponse getUserResponse) {
        var roleName = "TestRole_provision";
        var trustPolicy = roleTrustPolicy(getUserResponse.user().arn());

        var createRoleRequest = CreateRoleRequest.builder()
                .roleName(roleName)
                .description(format("Role for EDC transfer: %s", roleName))
                .assumeRolePolicyDocument(trustPolicy.toString())
                .maxSessionDuration(50000)
                .tags(roleTags(resourceDefinition))
                .build();

        return Failsafe.with(RetryPolicy.ofDefaults()).getStageAsync(() -> {
            log.info(format("S3CopyProvisionPipeline: creating IAM role '%s'", roleName));
            return iamClient.createRole(createRoleRequest);
        });
    }

    private List<Tag> roleTags(S3CopyResourceDefinition resourceDefinition) {
        var edcTag = Tag.builder()
                .key("created-by")
                .value("EDC")
                .build();
        var componentIdTag = Tag.builder()
                .key("edc:component-id")
                .value("componentId")
                .build();
        var tpTag = Tag.builder()
                .key("edc:transfer-process-id")
                .value(UUID.randomUUID().toString())
                .build();
        return List.of(edcTag, componentIdTag, tpTag);
    }

    private static final String VERSION_ATTRIBUTE = "Version";
    private static final String VERSION = "2012-10-17";
    public static final String STATEMENT_ATTRIBUTE = "Statement";
    private static final String EFFECT_ATTRIBUTE = "Effect";
    private static final String EFFECT_ALLOW = "Allow";
    private static final String PRINCIPAL_ATTRIBUTE = "Principal";
    private static final String AWS_ATTRIBUTE = "AWS";
    private static final String ACTION_ATTRIBUTE = "Action";
    private static final String PERMISSION_STS_ASSUME_ROLE = "sts:AssumeRole";
    private static final String CONDITION_ATTRIBUTE = "Condition";

    public static JsonObject roleTrustPolicy(String userArn) {
        return Json.createObjectBuilder()
                .add(VERSION_ATTRIBUTE, VERSION)
                .add(STATEMENT_ATTRIBUTE, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(EFFECT_ATTRIBUTE, EFFECT_ALLOW)
                                .add(PRINCIPAL_ATTRIBUTE, Json.createObjectBuilder()
                                        .add(AWS_ATTRIBUTE, userArn)
                                        .build())
                                .add(ACTION_ATTRIBUTE, PERMISSION_STS_ASSUME_ROLE)
                                .add(CONDITION_ATTRIBUTE, Json.createObjectBuilder().build())
                                .build())
                        .build())
                .build();
    }

    private CompletableFuture<PutRolePolicyResponse> putRolePolicy(IamAsyncClient iamClient,
                                                                   S3CopyResourceDefinition resourceDefinition,
                                                                   CreateRoleResponse createRoleResponse) {
        var sourceBucket = "source-bucket-name";
        var sourceObject = "source-object-name";
        var destinationBucket = resourceDefinition.getDestinationBucketName();
        var destinationObject = resourceDefinition.getDestinationObjectName();

        var rolePolicy = crossAccountRolePolicy(sourceBucket, sourceObject, destinationBucket, destinationObject);

        var role = createRoleResponse.role();
        log.info("Created role policy: {}", role.toString());
        log.info("Role arn {} and id {}", role.arn(), role.roleId());
        var putRolePolicyRequest = PutRolePolicyRequest.builder()
                .roleName(role.roleName())
                .policyName(resourceIdentifier(resourceDefinition))
                .policyDocument(rolePolicy.toString())
                .build();

        return Failsafe.with(RetryPolicy.ofDefaults()).getStageAsync(() -> {
            log.info(format("S3CopyProvisionPipeline: creating IAM role policy '%s'", putRolePolicyRequest.policyName()));
            return iamClient.putRolePolicy(putRolePolicyRequest);
        });
    }

    public static String resourceIdentifier(S3CopyResourceDefinition resourceDefinition) {
        return resourceIdentifier(resourceDefinition.getTransferProcessId());
    }

    public static String resourceIdentifier(String transferProcessId) {
        return format("edc-transfer_%s", transferProcessId);
    }

    private static final String PERMISSION_S3_LIST_BUCKET = "s3:ListBucket";
    private static final String PERMISSION_S3_GET_OBJECT = "s3:GetObject";
    private static final String PERMISSION_S3_GET_OBJECT_TAGGING = "s3:GetObjectTagging";
    private static final String PERMISSION_S3_GET_OBJECT_VERSION = "s3:GetObjectVersion";
    private static final String PERMISSION_S3_GET_OBJECT_VERSION_TAGGING = "s3:GetObjectVersionTagging";
    private static final String PERMISSION_S3_PUT_OBJECT = "s3:PutObject";
    private static final String PERMISSION_S3_PUT_OBJECT_ACL = "s3:PutObjectAcl";
    private static final String PERMISSION_S3_PUT_OBJECT_TAGGING = "s3:PutObjectTagging";

    private static final String RESOURCE_ATTRIBUTE = "Resource";
    private static final String S3_BUCKET_ARN_TEMPLATE = "arn:aws:s3:::%s";
    private static final String S3_OBJECT_ARN_TEMPLATE = "arn:aws:s3:::%s/%s";

    public static JsonObject crossAccountRolePolicy(String sourceBucket, String sourceObject, String destinationBucket, String destinationObject) {
        return Json.createObjectBuilder()
                .add(VERSION_ATTRIBUTE, VERSION)
                .add(STATEMENT_ATTRIBUTE, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(EFFECT_ATTRIBUTE, EFFECT_ALLOW)
                                .add(ACTION_ATTRIBUTE, Json.createArrayBuilder()
                                        .add(PERMISSION_S3_LIST_BUCKET)
                                        .add(PERMISSION_S3_GET_OBJECT)
                                        .add(PERMISSION_S3_GET_OBJECT_TAGGING)
                                        .add(PERMISSION_S3_GET_OBJECT_VERSION)
                                        .add(PERMISSION_S3_GET_OBJECT_VERSION_TAGGING)
                                        .build())
                                .add(RESOURCE_ATTRIBUTE, Json.createArrayBuilder()
                                        .add(format(S3_BUCKET_ARN_TEMPLATE, sourceBucket))
                                        .add(format(S3_OBJECT_ARN_TEMPLATE, sourceBucket, sourceObject))
                                        .build())
                                .build())
                        .add(Json.createObjectBuilder()
                                .add(EFFECT_ATTRIBUTE, EFFECT_ALLOW)
                                .add(ACTION_ATTRIBUTE, Json.createArrayBuilder()
                                        .add(PERMISSION_S3_LIST_BUCKET)
                                        .add(PERMISSION_S3_PUT_OBJECT)
                                        .add(PERMISSION_S3_PUT_OBJECT_ACL)
                                        .add(PERMISSION_S3_PUT_OBJECT_TAGGING)
                                        .add(PERMISSION_S3_GET_OBJECT_TAGGING)
                                        .add(PERMISSION_S3_GET_OBJECT_VERSION)
                                        .add(PERMISSION_S3_GET_OBJECT_VERSION_TAGGING)
                                        .build())
                                .add(RESOURCE_ATTRIBUTE, Json.createArrayBuilder()
                                        .add(format(S3_BUCKET_ARN_TEMPLATE, destinationBucket))
                                        .add(format(S3_OBJECT_ARN_TEMPLATE, destinationBucket, destinationObject))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    //CompletableFuture<S3ProvisionResponse>
    public CompletableFuture<GetUserResponse> provision(S3BucketDefinition bucketDefinition) {
        S3ClientRequest rq = S3ClientRequest.from(bucketDefinition.getRegionId(), bucketDefinition.getEndpointOverride());
        var s3AsyncClient = awsClientProvider.s3AsyncClient(rq);
        var s3Client = awsClientProvider.s3Client(rq);
        var iamClient = awsClientProvider.iamAsyncClient(rq);
        var stsClient = awsClientProvider.stsAsyncClient(rq);

        var request = CreateBucketRequest.builder()
                .bucket(bucketDefinition.getBucketName())
                .createBucketConfiguration(CreateBucketConfiguration.builder().build())
                .build();

//		return s3Client.createBucket(request);
        //completableFuture not complete
        return s3AsyncClient.createBucket(request)
                .thenCompose(r -> getUser(iamClient));
//				.thenCompose(response -> createRole(iamClient, bucketDefinition, response))
//				.thenCompose(response -> createRolePolicy(iamClient, bucketDefinition, response))
//				.thenCompose(role -> assumeRole(stsClient, role))
//				.whenComplete((result, ex) -> {
//					if (ex != null) {
//						log.error("Error in provisioning pipeline", ex);
//					} else {
//						log.info("Provisioning pipeline completed successfully");
//					}
//				});
    }

    private CompletableFuture<GetUserResponse> getUser(IamAsyncClient iamAsyncClient) {
        return Failsafe.with(RetryPolicy.ofDefaults()).getStageAsync(() -> {
            log.info("S3ProvisionPipeline: get user");
            return iamAsyncClient.getUser();
        });
    }

    private CompletableFuture<Role> createRolePolicy(IamAsyncClient iamAsyncClient, S3BucketDefinition resourceDefinition, CreateRoleResponse response) {
        Role role = response.role();
        log.info("Creating role policy: {}", role);
        PutRolePolicyRequest policyRequest = PutRolePolicyRequest.builder()
                .policyName(resourceDefinition.getTransferProcessId())
                .roleName(role.roleName())
                .policyDocument(format(BUCKET_POLICY, resourceDefinition.getBucketName()))
                .build();

        return iamAsyncClient.putRolePolicy(policyRequest)
                .thenApply(policyResponse -> role);
    }

    private CompletableFuture<CreateRoleResponse> createRole(IamAsyncClient iamClient, S3BucketDefinition resourceDefinition, GetUserResponse response) {
        log.info("Create role for user");
        String userArn = response.user().arn();
        software.amazon.awssdk.services.iam.model.Tag tag = Tag.builder()
                .key("dspTRUEConnector:process")
                .value(resourceDefinition.getTransferProcessId())
                .build();

        int roleMaxSessionDuration = 10000;
        CreateRoleRequest createRoleRequest = CreateRoleRequest.builder()
                .roleName(resourceDefinition.getTransferProcessId()).description("EDC transfer process role")
                .assumeRolePolicyDocument(format(ASSUME_ROLE_TRUST, userArn))
                .maxSessionDuration(roleMaxSessionDuration)
                .tags(tag)
                .build();

        return iamClient.createRole(createRoleRequest);
    }

    private CompletableFuture<S3ProvisionResponse> assumeRole(StsAsyncClient stsClient, Role role) {
        log.info("Assume role");
        AssumeRoleRequest roleRequest = AssumeRoleRequest.builder()
                .roleArn(role.arn())
                .roleSessionName("transfer")
                .externalId("123")
                .build();

        return stsClient.assumeRole(roleRequest)
                .thenApply(response -> new S3ProvisionResponse(role, response.credentials()));
    }


    private CompletableFuture<GetBucketPolicyResponse> getBucketPolicy(S3AsyncClient s3Client,
                                                                       S3CopyResourceDefinition resourceDefinition,
                                                                       PutRolePolicyResponse provisionSteps) {
        log.info("PutRolePolicyResponse {}", provisionSteps.toString());

        var getBucketPolicyRequest = GetBucketPolicyRequest.builder()
                .bucket(resourceDefinition.getDestinationBucketName())
                .build();

        return Failsafe.with(retryPolicy).getStageAsync(() -> {
            log.info("S3ProvisionPipeline: get bucket policy");
            return s3Client.getBucketPolicy(getBucketPolicyRequest);
        });
			/*
					.handle((result, ex) -> {
						if (ex == null) {
							provisionSteps.setBucketPolicy(result.policy());
							return provisionSteps;
						} else {
							if (ex instanceof CompletionException &&
									ex.getCause() instanceof S3Exception s3Exception &&
									s3Exception.awsErrorDetails().errorCode().equals(S3_ERROR_CODE_NO_SUCH_BUCKET_POLICY)) {
								// accessing the bucket policy works, but no bucket policy is set
								provisionSteps.setBucketPolicy(emptyBucketPolicy().toString());
								return provisionSteps;
							}

							throw new CompletionException("Failed to get destination bucket policy", ex);
						}
					});

			 */
    }

    public static final String BUCKET_POLICY_STATEMENT_SID_ATTRIBUTE = "Sid";
    private static final String S3_ALL_OBJECTS_ARN_TEMPLATE = "arn:aws:s3:::%s/*";

    public static JsonObject bucketPolicyStatement(String sid, String roleArn, String destinationBucket) {
        return Json.createObjectBuilder()
                .add(BUCKET_POLICY_STATEMENT_SID_ATTRIBUTE, sid)
                .add(EFFECT_ATTRIBUTE, EFFECT_ALLOW)
                .add(PRINCIPAL_ATTRIBUTE, Json.createObjectBuilder()
                        .add(AWS_ATTRIBUTE, roleArn)
                        .build())
                .add(ACTION_ATTRIBUTE, Json.createArrayBuilder()
                        .add(PERMISSION_S3_LIST_BUCKET)
                        .add(PERMISSION_S3_PUT_OBJECT)
                        .add(PERMISSION_S3_PUT_OBJECT_ACL)
                        .add(PERMISSION_S3_PUT_OBJECT_TAGGING)
                        .add(PERMISSION_S3_GET_OBJECT_TAGGING)
                        .add(PERMISSION_S3_GET_OBJECT_VERSION)
                        .add(PERMISSION_S3_GET_OBJECT_VERSION_TAGGING)
                        .build())
                .add(RESOURCE_ATTRIBUTE, Json.createArrayBuilder()
                        .add(format(S3_BUCKET_ARN_TEMPLATE, destinationBucket))
                        .add(format(S3_ALL_OBJECTS_ARN_TEMPLATE, destinationBucket))
                        .build())
                .build();
    }
/*
    private CompletableFuture<PutBucketPolicyResponse> updateBucketPolicy(S3AsyncClient s3Client,
                                                                          S3CopyResourceDefinition resourceDefinition,
                                                                          GetBucketPolicyResponse provisionSteps) {
        var statementSid = resourceDefinition.getBucketPolicyStatementSid();
        var roleArn = "arn:aws:iam::000000000000:role/TestRole_provision";//provisionSteps.getRole().arn();
        var destinationBucket = resourceDefinition.getDestinationBucketName();

        var statement = bucketPolicyStatement(statementSid, roleArn, destinationBucket);

        var typeReference = new TypeReference<HashMap<String, Object>>() {
        };

        ObjectMapper objectMapper = new ObjectMapper();
        var policyJson = Json.createObjectBuilder(objectMapper.readValue(provisionSteps.getBucketPolicy(),
                typeReference)).build();

        var statements = Json.createArrayBuilder(policyJson.getJsonArray(STATEMENT_ATTRIBUTE))
                .add(statement)
                .build();
        var updatedBucketPolicy = Json.createObjectBuilder(policyJson)
                .add(STATEMENT_ATTRIBUTE, statements)
                .build().toString();

        var putBucketPolicyRequest = PutBucketPolicyRequest.builder()
                .bucket(resourceDefinition.getDestinationBucketName())
                .policy(updatedBucketPolicy)
                .build();

        return Failsafe.with(retryPolicy).getStageAsync(() -> {
            log.info("S3CopyProvisionPipeline: updating destination bucket policy");
            return s3Client.putBucketPolicy(putBucketPolicyRequest);
        });
    }
 */
}
