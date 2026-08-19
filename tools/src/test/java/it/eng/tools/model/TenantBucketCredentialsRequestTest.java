package it.eng.tools.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantBucketCredentialsRequestTest {

    @Test
    @DisplayName("Build request with all fields set")
    void buildWithAllFieldsSet() {
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName("my-bucket")
                .accessKey("my-access-key")
                .secretKey("my-secret-key")
                .verifyConnection(true)
                .build();

        assertEquals("my-bucket", request.getBucketName());
        assertEquals("my-access-key", request.getAccessKey());
        assertEquals("my-secret-key", request.getSecretKey());
        assertTrue(request.isVerifyConnection());
    }

    @Test
    @DisplayName("verifyConnection defaults to false when not explicitly set")
    void verifyConnectionDefaultsToFalse() {
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName("my-bucket")
                .build();

        assertFalse(request.isVerifyConnection());
    }

    @Test
    @DisplayName("Build request with no fields set")
    void buildWithNoFieldsSet() {
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance().build();

        assertNull(request.getBucketName());
        assertNull(request.getAccessKey());
        assertNull(request.getSecretKey());
        assertFalse(request.isVerifyConnection());
    }
}
