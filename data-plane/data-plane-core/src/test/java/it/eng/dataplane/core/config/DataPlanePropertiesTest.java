package it.eng.dataplane.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DataPlaneProperties}.
 */
class DataPlanePropertiesTest {

    @Test
    @DisplayName("Default id is a non-blank UUID-like string")
    void defaultIdIsNonBlank() {
        DataPlaneProperties props = new DataPlaneProperties();
        assertNotNull(props.getId());
        assertFalse(props.getId().isBlank());
        // Should be UUID format: 8-4-4-4-12 hex chars
        assertTrue(props.getId().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "Expected UUID format, got: " + props.getId());
    }

    @Test
    @DisplayName("Two instances get different default ids")
    void twoInstancesGetDifferentDefaultIds() {
        DataPlaneProperties a = new DataPlaneProperties();
        DataPlaneProperties b = new DataPlaneProperties();
        assertNotEquals(a.getId(), b.getId());
    }

    @Test
    @DisplayName("Setters and getters work for all configurable fields")
    void settersAndGetters() {
        DataPlaneProperties props = new DataPlaneProperties();

        props.setId("my-dp-id");
        props.setEndpoint("http://dp:9090");
        props.setControlPlaneEndpoint("http://cp:8080");
        props.setControlPlaneAdminEndpoint("http://cp:8080");
        props.setAuthType("API_KEY");
        props.setApiKey("secret");
        props.setControlPlaneRegistrationKey("registration-key");

        assertEquals("my-dp-id", props.getId());
        assertEquals("http://dp:9090", props.getEndpoint());
        assertEquals("http://cp:8080", props.getControlPlaneEndpoint());
        assertEquals("http://cp:8080", props.getControlPlaneAdminEndpoint());
        assertEquals("API_KEY", props.getAuthType());
        assertEquals("secret", props.getApiKey());
        assertEquals("registration-key", props.getControlPlaneRegistrationKey());
    }

    @Test
    @DisplayName("Default authType is API_KEY")
    void defaultAuthTypeIsApiKey() {
        DataPlaneProperties props = new DataPlaneProperties();
        assertEquals("API_KEY", props.getAuthType());
    }

    @Test
    @DisplayName("controlPlaneEndpoint is null by default")
    void controlPlaneEndpointNullByDefault() {
        DataPlaneProperties props = new DataPlaneProperties();
        assertNull(props.getControlPlaneEndpoint());
    }
}
