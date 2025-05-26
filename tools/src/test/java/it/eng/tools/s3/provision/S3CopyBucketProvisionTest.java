package it.eng.tools.s3.provision;

import it.eng.tools.s3.configuration.AwsClientProvider;
import it.eng.tools.s3.provision.model.S3CopyResourceDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.iam.IamAsyncClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class S3CopyBucketProvisionTest {

    String policyJson = """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Sid": "test-sid",
                  "Effect": "Allow",
                  "Principal": {"AWS": "*"},
                  "Action": "s3:GetObject",
                  "Resource": "arn:aws:s3:::test-bucket/*"
                },
                {
                  "Sid": "other-sid",
                  "Effect": "Allow",
                  "Principal": {"AWS": "*"},
                  "Action": "s3:ListBucket",
                  "Resource": "arn:aws:s3:::test-bucket"
                }
              ]
            }
            """;

    @Mock
    private AwsClientProvider awsClientProvider;
    @Mock
    private S3AsyncClient s3AsyncClient;
    @Mock
    private StsAsyncClient stsAsyncClient;
    @Mock
    private IamAsyncClient iamAsyncClient;

    @InjectMocks
    private S3CopyBucketProvision s3CopyBucketProvision;

    private S3CopyResourceDefinition resourceDef;

    @BeforeEach
    void setUp() {
        resourceDef = S3CopyResourceDefinition.Builder.newInstance()
                .transferProcessId("tp_id_123")
                .endpointOverride("http://endpoint-override")
                .destinationRegion("eu-central-1")
                .destinationBucketName("destination-bucket-name")
                .destinationObjectName("destination_file.txt")
                .destinationKeyName("destination_file.txt")
                .bucketPolicyStatementSid("123")
                .build();

        when(awsClientProvider.s3AsyncClient(any())).thenReturn(s3AsyncClient);
        when(awsClientProvider.stsAsyncClient(any())).thenReturn(stsAsyncClient);
        when(awsClientProvider.iamAsyncClient(any())).thenReturn(iamAsyncClient);
    }

    @Test
    void copyProvision_success() {
        when(iamAsyncClient.getUser())
                .thenReturn(CompletableFuture.completedFuture(
                        GetUserResponse.builder()
                                .user(User.builder().arn("user-arn").build())
                                .build()));


        // Mock getBucketPolicy
        when(s3AsyncClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetBucketPolicyResponse.builder()
                        .policy(policyJson).build()));

        // Mock updateBucketPolicy if needed
        when(s3AsyncClient.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Mock IAM client operations
        when(iamAsyncClient.createRole(any(CreateRoleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateRoleResponse.builder()
                        .role(Role.builder().arn("test-role-arn").build())
                        .build()));

        when(iamAsyncClient.putRolePolicy(any(PutRolePolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(PutRolePolicyResponse.builder().build()));

        // Mock assumeRole
        Credentials credentials = Credentials.builder()
                .accessKeyId("key")
                .secretAccessKey("secret")
                .sessionToken("token")
                .build();
        when(stsAsyncClient.assumeRole(any(AssumeRoleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(AssumeRoleResponse.builder()
                        .credentials(credentials)
                        .build()));

        var result = s3CopyBucketProvision.copyProvision(resourceDef).join();

        assertNotNull(result);
        assertNotNull(result.getCredentials());
        verify(iamAsyncClient).getUser();
        verify(s3AsyncClient).getBucketPolicy(any(GetBucketPolicyRequest.class));
        verify(iamAsyncClient).createRole(any(CreateRoleRequest.class));
        verify(iamAsyncClient).putRolePolicy(any(PutRolePolicyRequest.class));
        verify(stsAsyncClient).assumeRole(any(AssumeRoleRequest.class));

    }

    @Test
    void copyProvision_fail_createRole() {
        // Mock getUser - use no-args version
        when(iamAsyncClient.getUser())
                .thenReturn(CompletableFuture.completedFuture(
                        GetUserResponse.builder()
                                .user(User.builder().arn("user-arn").build())
                                .build()));
        when(iamAsyncClient.createRole(any(CreateRoleRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Create role failed")));

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                s3CopyBucketProvision.copyProvision(resourceDef).get());

        assertTrue(ex.getCause().getMessage().contains("Create role failed"));
        verify(iamAsyncClient).getUser();

    }

    @Test
    void copyProvision_fail_putRolePolicy() {
        // Mock getUser - use no-args version
        when(iamAsyncClient.getUser())
                .thenReturn(CompletableFuture.completedFuture(
                        GetUserResponse.builder()
                                .user(User.builder().arn("user-arn").build())
                                .build()));

        // Mock IAM client operations
        when(iamAsyncClient.createRole(any(CreateRoleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateRoleResponse.builder()
                        .role(Role.builder().arn("test-role-arn").build())
                        .build()));

        when(iamAsyncClient.putRolePolicy(any(PutRolePolicyRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("putRolePolicy failed")));

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                s3CopyBucketProvision.copyProvision(resourceDef).get());

        assertTrue(ex.getCause().getMessage().contains("putRolePolicy failed"));
        verify(iamAsyncClient).getUser();
        verify(iamAsyncClient).createRole(any(CreateRoleRequest.class));
    }
}
