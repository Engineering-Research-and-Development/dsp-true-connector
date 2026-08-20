package it.eng.tools.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantCreateRequestTest {

    @Test
    @DisplayName("TenantCreateRequest toTenant maps tenant fields and excludes bucket fields")
    void toTenant_mapsTenantFieldsOnly() {
        TenantCreateRequest request = TenantCreateRequest.Builder.newInstance()
                .id("tenant-a")
                .name("Tenant A")
                .description("Description")
                .participantId("urn:connector:tenant-a")
                .automaticNegotiation(true)
                .automaticTransfer(false)
                .enabled(true)
                .bucketName("external-bucket")
                .accessKey("external-access")
                .secretKey("external-secret")
                .verifyConnection(true)
                .build();

        Tenant tenant = request.toTenant();

        assertEquals("tenant-a", tenant.getId());
        assertEquals("Tenant A", tenant.getName());
        assertEquals("Description", tenant.getDescription());
        assertEquals("urn:connector:tenant-a", tenant.getParticipantId());
        assertTrue(tenant.isAutomaticNegotiation());
        assertFalse(tenant.isAutomaticTransfer());
        assertTrue(tenant.isEnabled());
        assertNull(tenant.getBucketName());
    }

    @Test
    @DisplayName("TenantCreateRequest toCredentialsRequest maps optional bucket credential fields")
    void toCredentialsRequest_mapsCredentialsFields() {
        TenantCreateRequest request = TenantCreateRequest.Builder.newInstance()
                .id("tenant-b")
                .name("Tenant B")
                .participantId("urn:connector:tenant-b")
                .bucketName("external-bucket")
                .accessKey("external-access")
                .secretKey("external-secret")
                .verifyConnection(true)
                .build();

        TenantBucketCredentialsRequest credentialsRequest = request.toCredentialsRequest();

        assertEquals("external-bucket", credentialsRequest.getBucketName());
        assertEquals("external-access", credentialsRequest.getAccessKey());
        assertEquals("external-secret", credentialsRequest.getSecretKey());
        assertTrue(credentialsRequest.isVerifyConnection());
    }

    @Test
    @DisplayName("TenantCreateRequest verifyConnection defaults to false")
    void verifyConnection_defaultIsFalse() {
        TenantCreateRequest request = TenantCreateRequest.Builder.newInstance()
                .id("tenant-c")
                .name("Tenant C")
                .participantId("urn:connector:tenant-c")
                .build();

        TenantBucketCredentialsRequest credentialsRequest = request.toCredentialsRequest();

        assertFalse(credentialsRequest.isVerifyConnection());
    }
}
