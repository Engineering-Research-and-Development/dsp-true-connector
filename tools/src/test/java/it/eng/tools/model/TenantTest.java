package it.eng.tools.model;

import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantTest {

    private static final String BASE_URL = "http://example.com";

    @Test
    @DisplayName("Build tenant successfully with all required fields")
    void buildTenantSuccessfully() {
        Tenant tenant = Tenant.Builder.newInstance()
                .id("test-tenant")
                .name("Test Tenant")
                .description("A description")
                .participantId("urn:connector:test")
                .enabled(true)
                .automaticNegotiation(true)
                .automaticTransfer(false)
                .build();

        assertNotNull(tenant);
        assertEquals("test-tenant", tenant.getId());
        assertEquals("Test Tenant", tenant.getName());
        assertEquals("A description", tenant.getDescription());
        assertEquals("urn:connector:test", tenant.getParticipantId());
        assertEquals(BASE_URL + "/test-tenant", tenant.getCallbackAddress(BASE_URL));
        assertTrue(tenant.isEnabled());
        assertTrue(tenant.isAutomaticNegotiation());
        assertFalse(tenant.isAutomaticTransfer());
    }

    @Test
    @DisplayName("Build tenant fails when name is null")
    void buildTenantMissingName() {
        assertThrows(ValidationException.class, () ->
                Tenant.Builder.newInstance()
                        .id("test-tenant")
                        .participantId("urn:connector:test")
                        .build()
        );
    }

    @Test
    @DisplayName("Build tenant fails when participantId is null")
    void buildTenantMissingParticipantId() {
        assertThrows(ValidationException.class, () ->
                Tenant.Builder.newInstance()
                        .id("test-tenant")
                        .name("Test Tenant")
                        .build()
        );
    }

    @Test
    @DisplayName("Build tenant fails when id is null")
    void buildTenantMissingId() {
        assertThrows(ValidationException.class, () ->
                Tenant.Builder.newInstance()
                        .name("Test Tenant")
                        .participantId("urn:connector:test")
                        .build()
        );
    }

    @Test
    @DisplayName("getCallbackAddress strips trailing slash from base URL")
    void getCallbackAddress_stripsTrailingSlash() {
        Tenant tenant = Tenant.Builder.newInstance()
                .id("my-tenant")
                .name("Test")
                .participantId("urn:connector:test")
                .build();

        assertEquals("http://example.com/my-tenant", tenant.getCallbackAddress("http://example.com/"));
    }
}
