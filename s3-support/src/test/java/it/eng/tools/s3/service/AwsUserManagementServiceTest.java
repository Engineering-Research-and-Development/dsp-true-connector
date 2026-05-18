package it.eng.tools.s3.service;

import it.eng.tools.s3.model.BucketCredentialsEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
    @DisplayName("attachTemporaryPolicy does not throw (no-op in AWS mode)")
    void attachTemporaryPolicy_noOp_doesNotThrow() {
        assertDoesNotThrow(() -> service.attachTemporaryPolicy("ak", "policy-name", "{}"));
    }

    @Test
    @DisplayName("deleteUser does not throw (no-op in AWS mode)")
    void deleteUser_noOp_doesNotThrow() {
        assertDoesNotThrow(() -> service.deleteUser("ak"));
    }

    @Test
    @DisplayName("deletePolicy does not throw (no-op in AWS mode)")
    void deletePolicy_noOp_doesNotThrow() {
        assertDoesNotThrow(() -> service.deletePolicy("my-policy"));
    }
}
