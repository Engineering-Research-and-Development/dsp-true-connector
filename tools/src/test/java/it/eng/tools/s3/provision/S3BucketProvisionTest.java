package it.eng.tools.s3.provision;

import it.eng.tools.s3.configuration.AwsClientProvider;
import it.eng.tools.s3.provision.model.S3BucketDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.iam.IamAsyncClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class S3BucketProvisionTest {

    @Mock
    private AwsClientProvider awsClientProvider;
    @Mock
    private S3AsyncClient s3AsyncClient;
    @Mock
    private IamAsyncClient iamAsyncClient;
    @Mock
    private StsAsyncClient stsAsyncClient;

    @InjectMocks
    private S3BucketProvision s3BucketProvision;

    private S3BucketDefinition bucketDef;

    @BeforeEach
    void setUp() {
//        s3BucketProvision = new S3BucketProvision(awsClientProvider);

        bucketDef = mock(S3BucketDefinition.class);
        when(bucketDef.getRegionId()).thenReturn("eu-west-1");
        when(bucketDef.getEndpointOverride()).thenReturn("http://localhost:4566");
        when(bucketDef.getBucketName()).thenReturn("test-bucket");
//        when(bucketDef.getTransferProcessId()).thenReturn("tp-123");

        when(awsClientProvider.s3AsyncClient(any())).thenReturn(s3AsyncClient);
        when(awsClientProvider.iamAsyncClient(any())).thenReturn(iamAsyncClient);
        when(awsClientProvider.stsAsyncClient(any())).thenReturn(stsAsyncClient);
    }

    @Test
    void createS3ucketWithPermissions_success() throws Exception {
        // Step 1: create bucket
        when(bucketDef.getTransferProcessId()).thenReturn("tp-123");
        when(s3AsyncClient.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateBucketResponse.builder().build()));
        // Step 2: get user
        when(iamAsyncClient.getUser())
                .thenReturn(CompletableFuture.completedFuture(GetUserResponse.builder()
                        .user(User.builder().arn("arn:aws:iam::123:user/test").build()).build()));
        // Step 3: create role
        when(iamAsyncClient.createRole(any(CreateRoleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateRoleResponse.builder()
                        .role(Role.builder().arn("arn:aws:iam::123:role/test").roleName("tp-123").build()).build()));
        // Step 4: put role policy
        when(iamAsyncClient.putRolePolicy(any(PutRolePolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(PutRolePolicyResponse.builder().build()));
        // Step 5: assume role
        when(stsAsyncClient.assumeRole(any(AssumeRoleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(AssumeRoleResponse.builder()
                        .credentials(Credentials.builder().accessKeyId("key").secretAccessKey("secret").build()).build()));

        var result = s3BucketProvision.createS3BucketWithPermissions(bucketDef).get();
        assertNotNull(result);
        assertNotNull(result.getRole());
        assertNotNull(result.getCredentials());
    }

    @Test
    void createS3ucketWithPermissions_fail_createBucket() {
        when(s3AsyncClient.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("createBucket failed")));

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                s3BucketProvision.createS3BucketWithPermissions(bucketDef).get());
        assertTrue(ex.getCause().getMessage().contains("createBucket failed"));
    }

    @Test
    void createS3BucketWithPermissions_fail_getUser() {
        when(s3AsyncClient.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateBucketResponse.builder().build()));
        when(iamAsyncClient.getUser())
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("getUser failed")));

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                s3BucketProvision.createS3BucketWithPermissions(bucketDef).get());
        assertTrue(ex.getCause().getMessage().contains("getUser failed"));
    }

    @Test
    void createS3ucketWithPermissions_fail_createRole() {
        when(bucketDef.getTransferProcessId()).thenReturn("tp-123");
        when(s3AsyncClient.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateBucketResponse.builder().build()));
        when(iamAsyncClient.getUser())
                .thenReturn(CompletableFuture.completedFuture(GetUserResponse.builder()
                        .user(User.builder().arn("arn:aws:iam::123:user/test").build()).build()));
        when(iamAsyncClient.createRole(any(CreateRoleRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("createRole failed")));

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                s3BucketProvision.createS3BucketWithPermissions(bucketDef).get());
        assertTrue(ex.getCause().getMessage().contains("createRole failed"));
    }

    @Test
    void createS3BucketWithPermissions_fail_putRolePolicy() {
        when(bucketDef.getTransferProcessId()).thenReturn("tp-123");
        when(s3AsyncClient.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateBucketResponse.builder().build()));
        when(iamAsyncClient.getUser())
                .thenReturn(CompletableFuture.completedFuture(GetUserResponse.builder()
                        .user(User.builder().arn("arn:aws:iam::123:user/test").build()).build()));
        when(iamAsyncClient.createRole(any(CreateRoleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateRoleResponse.builder()
                        .role(Role.builder().arn("arn:aws:iam::123:role/test").roleName("tp-123").build()).build()));
        when(iamAsyncClient.putRolePolicy(any(PutRolePolicyRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("putRolePolicy failed")));

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                s3BucketProvision.createS3BucketWithPermissions(bucketDef).get());
        assertTrue(ex.getCause().getMessage().contains("putRolePolicy failed"));
    }

    @Test
    void createS3BucketWithPermissions_fail_assumeRole() {
        when(bucketDef.getTransferProcessId()).thenReturn("tp-123");
        when(s3AsyncClient.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateBucketResponse.builder().build()));
        when(iamAsyncClient.getUser())
                .thenReturn(CompletableFuture.completedFuture(GetUserResponse.builder()
                        .user(User.builder().arn("arn:aws:iam::123:user/test").build()).build()));
        when(iamAsyncClient.createRole(any(CreateRoleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateRoleResponse.builder()
                        .role(Role.builder().arn("arn:aws:iam::123:role/test").roleName("tp-123").build()).build()));
        when(iamAsyncClient.putRolePolicy(any(PutRolePolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(PutRolePolicyResponse.builder().build()));
        when(stsAsyncClient.assumeRole(any(AssumeRoleRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("assumeRole failed")));

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                s3BucketProvision.createS3BucketWithPermissions(bucketDef).get());
        assertTrue(ex.getCause().getMessage().contains("assumeRole failed"));
    }
}
