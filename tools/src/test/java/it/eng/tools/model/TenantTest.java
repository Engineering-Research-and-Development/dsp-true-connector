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

    @Test
    @DisplayName("Build tenant successfully with all required fields")
    void buildTenantSuccessfully() {
        Tenant tenant = Tenant.Builder.newInstance()
                .id("test-tenant")
                .name("Test Tenant")
                .description("A description")
                .connectorId("urn:connector:test")
                .callbackAddress("http://example.com/test")
                .enabled(true)
                .automaticNegotiation(true)
                .automaticTransfer(false)
                .build();

        assertNotNull(tenant);
        assertEquals("test-tenant", tenant.getId());
        assertEquals("Test Tenant", tenant.getName());
        assertEquals("A description", tenant.getDescription());
        assertEquals("urn:connector:test", tenant.getConnectorId());
        assertEquals("http://example.com/test", tenant.getCallbackAddress());
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
                        .connectorId("urn:connector:test")
                        .callbackAddress("http://example.com/test")
                        .build()
        );
    }

    @Test
    @DisplayName("Build tenant fails when connectorId is null")
    void buildTenantMissingConnectorId() {
        assertThrows(ValidationException.class, () ->
                Tenant.Builder.newInstance()
                        .id("test-tenant")
                        .name("Test Tenant")
                        .callbackAddress("http://example.com/test")
                        .build()
        );
    }

    @Test
    @DisplayName("Build tenant fails when callbackAddress is null")
    void buildTenantMissingCallbackAddress() {
        assertThrows(ValidationException.class, () ->
                Tenant.Builder.newInstance()
                        .id("test-tenant")
                        .name("Test Tenant")
                        .connectorId("urn:connector:test")
                        .build()
        );
    }
}
