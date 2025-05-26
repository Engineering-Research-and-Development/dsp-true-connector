package it.eng.tools.s3.provision;

import it.eng.tools.s3.configuration.AwsClientProvider;
import it.eng.tools.s3.provision.model.S3BucketDeprovisionDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3CopyBucketDeprovisionTest {

    @Mock
    private AwsClientProvider awsClientProvider;

    @Mock
    private S3AsyncClient s3AsyncClient;

    @Mock
    private IamAsyncClient iamAsyncClient;

    @InjectMocks
    private S3CopyBucketDeprovision s3BucketDeprovision;

    @Test
    void deprovisionBucket_removesBucketPolicyAndDeletesRoleSuccessfully() {
        var deprovisionDefinition = mock(S3BucketDeprovisionDefinition.class);
        var getBucketPolicyResponse = mock(GetBucketPolicyResponse.class);
        var putBucketPolicyResponse = mock(PutBucketPolicyResponse.class);
        var deleteRolePolicyResponse = mock(DeleteRolePolicyResponse.class);
        var deleteRoleResponse = mock(DeleteRoleResponse.class);

        when(deprovisionDefinition.getDestinationRegion()).thenReturn("us-east-1");
        when(deprovisionDefinition.getEndpointOverride()).thenReturn("http://endpoint.override");
        SecretToken secretToken = mock(SecretToken.class);
        when(deprovisionDefinition.getSecretToken()).thenReturn(secretToken);
        when(deprovisionDefinition.getDestinationBucketName()).thenReturn("test-bucket");
        when(deprovisionDefinition.getSourceAccountRoleName()).thenReturn("test-role");
        when(deprovisionDefinition.getBucketPolicyStatementSid()).thenReturn("test-sid");

        when(awsClientProvider.s3AsyncClient(any())).thenReturn(s3AsyncClient);
        when(awsClientProvider.iamAsyncClient(any())).thenReturn(iamAsyncClient);

        // Provide a valid policy JSON string with a statement containing the Sid to be removed
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
        when(getBucketPolicyResponse.policy()).thenReturn(policyJson);

        when(s3AsyncClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(getBucketPolicyResponse));
        when(s3AsyncClient.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putBucketPolicyResponse));
        when(iamAsyncClient.deleteRolePolicy(any(DeleteRolePolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(deleteRolePolicyResponse));
        when(iamAsyncClient.deleteRole(any(DeleteRoleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(deleteRoleResponse));

        var result = s3BucketDeprovision.deprovisionBucket(deprovisionDefinition).join();

        assertNotNull(result);
        verify(s3AsyncClient).getBucketPolicy(any(GetBucketPolicyRequest.class));
        verify(s3AsyncClient).putBucketPolicy(any(PutBucketPolicyRequest.class));
        verify(iamAsyncClient).deleteRolePolicy(any(DeleteRolePolicyRequest.class));
        verify(iamAsyncClient).deleteRole(any(DeleteRoleRequest.class));
    }

    @Test
    void deprovisionBucket_throwsExceptionWhenBucketPolicyParsingFails() {
        var deprovisionDefinition = mock(S3BucketDeprovisionDefinition.class);
        var getBucketPolicyResponse = mock(GetBucketPolicyResponse.class);

        when(deprovisionDefinition.getDestinationRegion()).thenReturn("us-east-1");
        when(deprovisionDefinition.getEndpointOverride()).thenReturn("http://endpoint.override");
        SecretToken secretToken = mock(SecretToken.class);
        when(deprovisionDefinition.getSecretToken()).thenReturn(secretToken);
        when(deprovisionDefinition.getDestinationBucketName()).thenReturn("test-bucket");

        when(awsClientProvider.s3AsyncClient(any())).thenReturn(s3AsyncClient);
        when(s3AsyncClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(getBucketPolicyResponse));
        when(getBucketPolicyResponse.policy()).thenReturn("{\n" +
                "  \"foo\": \"bar\"\n" +
                "}");

        var exception = assertThrows(RuntimeException.class, () ->
                s3BucketDeprovision.deprovisionBucket(deprovisionDefinition).join());

        assertEquals("Bucket policy JSON missing or invalid 'Statement' array", exception.getCause().getMessage());
    }

    @Test
    void deprovisionBucket_handlesMissingBucketPolicyGracefully() {
        var deprovisionDefinition = mock(S3BucketDeprovisionDefinition.class);
        var getBucketPolicyResponse = mock(GetBucketPolicyResponse.class);
        var deleteRolePolicyResponse = mock(DeleteRolePolicyResponse.class);
        var deleteRoleResponse = mock(DeleteRoleResponse.class);

        when(deprovisionDefinition.getDestinationRegion()).thenReturn("us-east-1");
        when(deprovisionDefinition.getEndpointOverride()).thenReturn("http://endpoint.override");
        SecretToken secretToken = mock(SecretToken.class);
        when(deprovisionDefinition.getSecretToken()).thenReturn(secretToken);
        when(deprovisionDefinition.getDestinationBucketName()).thenReturn("test-bucket");
        when(deprovisionDefinition.getSourceAccountRoleName()).thenReturn("test-role");

        when(awsClientProvider.s3AsyncClient(any())).thenReturn(s3AsyncClient);
        when(awsClientProvider.iamAsyncClient(any())).thenReturn(iamAsyncClient);

        when(s3AsyncClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(getBucketPolicyResponse));
        when(getBucketPolicyResponse.policy()).thenReturn(null);
        when(iamAsyncClient.deleteRolePolicy(any(DeleteRolePolicyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(deleteRolePolicyResponse));
        when(iamAsyncClient.deleteRole(any(DeleteRoleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(deleteRoleResponse));

        var result = s3BucketDeprovision.deprovisionBucket(deprovisionDefinition).join();

        assertNotNull(result);
        verify(s3AsyncClient).getBucketPolicy(any(GetBucketPolicyRequest.class));
        verify(iamAsyncClient).deleteRolePolicy(any(DeleteRolePolicyRequest.class));
        verify(iamAsyncClient).deleteRole(any(DeleteRoleRequest.class));
    }
}
