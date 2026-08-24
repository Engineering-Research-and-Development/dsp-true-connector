package it.eng.tools.s3.service;

import it.eng.tools.s3.model.BucketCredentialsEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link AwsUserManagementService}.
 * All methods are no-ops in AWS mode (IAM must be pre-configured externally).
 */
class AwsUserManagementServiceTest {

    private AwsUserManagementService service;

    @BeforeEach
    void setUp() {
        service = new AwsUserManagementService();
    }

    @Test
    @DisplayName("createUser does not throw (no-op in AWS mode)")
    void createUser_noOp_doesNotThrow() {
        BucketCredentialsEntity creds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("my-bucket").accessKey("ak").secretKey("sk").build();
        assertDoesNotThrow(() -> service.createUser(creds));
    }

    @Test
    @DisplayName("attachPolicyToUser does not throw (no-op in AWS mode)")
    void attachPolicyToUser_noOp_doesNotThrow() {
        BucketCredentialsEntity creds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("my-bucket").accessKey("ak").secretKey("sk").build();
        assertDoesNotThrow(() -> service.attachPolicyToUser(creds));
    }

    @Test
    @DisplayName("attachTemporaryPolicy throws until AWS temp-user redesign is implemented")
    void attachTemporaryPolicy_throwsUnsupportedOperationException() {
        BucketCredentialsEntity creds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("my-bucket").accessKey("ak").secretKey("sk").build();
        assertThrows(UnsupportedOperationException.class,
                () -> service.attachTemporaryPolicy(creds, "ak", "policy-name", "{}"));
    }

    @Test
    @DisplayName("deleteUser throws until AWS temp-user redesign is implemented")
    void deleteUser_throwsUnsupportedOperationException() {
        BucketCredentialsEntity creds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("my-bucket").accessKey("ak").secretKey("sk").build();
        assertThrows(UnsupportedOperationException.class, () -> service.deleteUser(creds, "ak"));
    }

    @Test
    @DisplayName("deletePolicy throws until AWS temp-user redesign is implemented")
    void deletePolicy_throwsUnsupportedOperationException() {
        BucketCredentialsEntity creds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("my-bucket").accessKey("ak").secretKey("sk").build();
        assertThrows(UnsupportedOperationException.class, () -> service.deletePolicy(creds, "my-policy"));
    }
}
