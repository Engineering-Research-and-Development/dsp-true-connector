package it.eng.tools.s3.provision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import it.eng.tools.s3.configuration.AwsClientProvider;
import it.eng.tools.s3.provision.model.S3BucketDeprovisionDefinition;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamAsyncClient;
import software.amazon.awssdk.services.iam.model.DeleteRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.DeleteRolePolicyResponse;
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest;
import software.amazon.awssdk.services.iam.model.DeleteRoleResponse;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyResponse;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class S3CopyBucketDeprovision {

    public static final String STATEMENT_ATTRIBUTE = "Statement";
    public static final String BUCKET_POLICY_STATEMENT_SID_ATTRIBUTE = "Sid";

    private final AwsClientProvider awsClientProvider;
    private final RetryPolicy<Object> retryPolicy;
    private final JsonMapper jsonMapper;
    ;

    public S3CopyBucketDeprovision(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
        this.retryPolicy = RetryPolicy.builder()
                .handle(Exception.class)
                .withMaxRetries(3)
                .build();
        this.jsonMapper = new JsonMapper();
    }

    public CompletableFuture<DeleteRoleResponse> deprovisionBucket(S3BucketDeprovisionDefinition deprovisionDefinition) {
        // bet bucket policy
        // remove policy we added for copying
        // update bucket policy
        // delete role

        var s3ClientRequest = S3ClientRequest.from(deprovisionDefinition.getDestinationRegion(),
                deprovisionDefinition.getEndpointOverride(),
                deprovisionDefinition.getSecretToken());
        var s3Client = awsClientProvider.s3AsyncClient(s3ClientRequest);

        // create IAM client for source account -> delete IAM role
        var iamClient = awsClientProvider.iamAsyncClient(S3ClientRequest.from(Region.AWS_GLOBAL.id(),
                deprovisionDefinition.getEndpointOverride()));

        var getBucketPolicyRequest = GetBucketPolicyRequest.builder()
                .bucket(deprovisionDefinition.getDestinationBucketName())
                .build();

        String roleName = deprovisionDefinition.getSourceAccountRoleName();

        return s3Client.getBucketPolicy(getBucketPolicyRequest)
                .thenCompose(response -> removeCopyBucketPolicy(s3Client, deprovisionDefinition, response))
                .thenCompose(response -> deleteRolePolicy(iamClient, roleName))
                .thenCompose(response -> deleteRole(iamClient, roleName));
    }

    private CompletableFuture<PutBucketPolicyResponse> removeCopyBucketPolicy(S3AsyncClient s3Client,
                                                                              S3BucketDeprovisionDefinition deprovisionDefinition,
                                                                              GetBucketPolicyResponse bucketPolicyResponse) {

        //get bucket policy
        var policy = bucketPolicyResponse.policy();
        if (policy == null || policy.isBlank()) {
            // No policy to update, return a completed future with null or a dummy response
            return CompletableFuture.completedFuture(PutBucketPolicyResponse.builder().build());
        }

        var bucketPolicyStatementSid = deprovisionDefinition.getBucketPolicyStatementSid();
        // remove policy we added for copying
        var typeReference = new TypeReference<HashMap<String, Object>>() {
        };

        JsonObject policyJson = null;
        try {
            policyJson = Json.createObjectBuilder(jsonMapper.readValue(policy, typeReference)).build();
        } catch (JsonProcessingException e) {
            log.error("Error parsing bucket policy JSON", e);
            throw new RuntimeException(e);
        }

        if (!policyJson.containsKey(STATEMENT_ATTRIBUTE) || policyJson.get(STATEMENT_ATTRIBUTE) == null
                || !policyJson.get(STATEMENT_ATTRIBUTE).getValueType().equals(jakarta.json.JsonValue.ValueType.ARRAY)) {
            log.error("Bucket policy JSON missing or invalid 'Statement' array");
            throw new RuntimeException("Bucket policy JSON missing or invalid 'Statement' array");
        }

        var statementsBuilder = Json.createArrayBuilder();

        policyJson.getJsonArray(STATEMENT_ATTRIBUTE).forEach(entry -> {
            var statement = (JsonObject) entry;
            var sid = statement.getJsonString(BUCKET_POLICY_STATEMENT_SID_ATTRIBUTE);

            // add all previously existing statements to bucket policy, omit only statement with Sid specified in provisioned resource
            if (sid == null || !bucketPolicyStatementSid.equals(sid.getString())) {
                statementsBuilder.add(statement);
            }
        });

        var updatedBucketPolicy = Json.createObjectBuilder(policyJson)
                .add(STATEMENT_ATTRIBUTE, statementsBuilder)
                .build();

        // update bucket policy

        var putBucketPolicyRequest = PutBucketPolicyRequest.builder()
                .bucket(deprovisionDefinition.getDestinationBucketName())
                .policy(updatedBucketPolicy.toString())
                .build();

        return Failsafe.with(retryPolicy).getStageAsync(() -> {
            log.info("Updating destination bucket policy");
            return s3Client.putBucketPolicy(putBucketPolicyRequest);
        });
    }

    private CompletableFuture<DeleteRolePolicyResponse> deleteRolePolicy(IamAsyncClient iamClient, String roleName) {
        var deleteRolePolicyRequest = DeleteRolePolicyRequest.builder()
                .roleName(roleName)
                .policyName(roleName)
                .build();

        return Failsafe.with(retryPolicy).getStageAsync(() -> {
            log.info("Deleting IAM role policy");
            return iamClient.deleteRolePolicy(deleteRolePolicyRequest);
        });
    }

    private CompletableFuture<DeleteRoleResponse> deleteRole(IamAsyncClient iamClient, String roleName) {
        var deleteRoleRequest = DeleteRoleRequest.builder()
                .roleName(roleName)
                .build();

        return Failsafe.with(retryPolicy).getStageAsync(() -> {
            log.info("Deleting IAM role");
            return iamClient.deleteRole(deleteRoleRequest);
        });
    }

}
