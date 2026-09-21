package it.eng.tools.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantUpdateRequestTest {

    @Test
    @DisplayName("TenantUpdateRequest toTenantUpdates maps mutable tenant fields")
    void toTenantUpdates_mapsMutableFields() {
        TenantUpdateRequest request = TenantUpdateRequest.Builder.newInstance()
                .name("Updated tenant")
                .description("Updated description")
                .automaticNegotiation(true)
                .automaticTransfer(false)
                .build();
        Tenant existing = Tenant.Builder.newInstance()
                .id("tenant-id")
                .name("Existing name")
                .participantId("urn:connector:tenant")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .enabled(true)
                .build();

        Tenant updates = request.toTenantUpdates(existing);

        assertEquals("Updated tenant", updates.getName());
        assertEquals("Updated description", updates.getDescription());
        assertTrue(updates.isAutomaticNegotiation());
        assertFalse(updates.isAutomaticTransfer());
        assertEquals("tenant-id", updates.getId());
        assertEquals("urn:connector:tenant", updates.getParticipantId());
    }

    @Test
    @DisplayName("TenantUpdateRequest toCredentialsRequest maps optional credentials")
    void toCredentialsRequest_mapsCredentialsFields() {
        TenantUpdateRequest request = TenantUpdateRequest.Builder.newInstance()
                .bucketName("tenant-bucket")
                .accessKey("provided-access")
                .secretKey("provided-secret")
                .verifyConnection(true)
                .build();

        TenantBucketCredentialsRequest credentialsRequest = request.toCredentialsRequest();

        assertEquals("tenant-bucket", credentialsRequest.getBucketName());
        assertEquals("provided-access", credentialsRequest.getAccessKey());
        assertEquals("provided-secret", credentialsRequest.getSecretKey());
        assertTrue(credentialsRequest.isVerifyConnection());
    }

    @Test
    @DisplayName("TenantUpdateRequest verifyConnection defaults to false")
    void verifyConnection_defaultIsFalse() {
        TenantUpdateRequest request = TenantUpdateRequest.Builder.newInstance()
                .name("Updated tenant")
                .build();

        TenantBucketCredentialsRequest credentialsRequest = request.toCredentialsRequest();

        assertFalse(credentialsRequest.isVerifyConnection());
    }

    @Test
    @DisplayName("TenantUpdateRequest toTenantUpdates keeps existing name when omitted")
    void toTenantUpdates_withoutName_preservesExistingName() {
        Tenant existing = Tenant.Builder.newInstance()
                .id("tenant-id")
                .name("Existing name")
                .participantId("urn:connector:tenant")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .enabled(true)
                .build();
        TenantUpdateRequest request = TenantUpdateRequest.Builder.newInstance()
                .description("Only description changes")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .build();

        Tenant updates = request.toTenantUpdates(existing);

        assertEquals("Existing name", updates.getName());
        assertEquals("Only description changes", updates.getDescription());
    }
}
