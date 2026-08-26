package it.eng.datatransfer.model;

import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DataPlaneRegistrationTest {

    @Test
    @DisplayName("Build with required fields succeeds and auto-sets id and registeredAt")
    public void buildWithRequiredFieldsSucceeds() {
        DataPlaneRegistration reg = DataPlaneRegistration.Builder.newInstance()
                .endpoint("http://dataplane.example.com")
                .supportedTransferTypes(Set.of("HttpData-PULL"))
                .build();

        assertNotNull(reg);
        assertNotNull(reg.getId());
        assertNotNull(reg.getRegisteredAt());
        assertEquals("http://dataplane.example.com", reg.getEndpoint());
        assertTrue(reg.getSupportedTransferTypes().contains("HttpData-PULL"));
    }

    @Test
    @DisplayName("Missing endpoint throws ValidationException")
    public void missingEndpointThrowsValidation() {
        assertThrows(ValidationException.class, () ->
                DataPlaneRegistration.Builder.newInstance()
                        .supportedTransferTypes(Set.of("HttpData-PULL"))
                        .build());
    }

    @Test
    @DisplayName("Missing supportedTransferTypes throws ValidationException")
    public void missingSupportedTransferTypesThrowsValidation() {
        assertThrows(ValidationException.class, () ->
                DataPlaneRegistration.Builder.newInstance()
                        .endpoint("http://dataplane.example.com")
                        .build());
    }

    @Test
    @DisplayName("Empty supportedTransferTypes throws ValidationException")
    public void emptySupportedTransferTypesThrowsValidation() {
        assertThrows(ValidationException.class, () ->
                DataPlaneRegistration.Builder.newInstance()
                        .endpoint("http://dataplane.example.com")
                        .supportedTransferTypes(Set.of())
                        .build());
    }

    @Test
    @DisplayName("Empty builder throws ValidationException")
    public void emptyBuilderThrowsValidation() {
        assertThrows(ValidationException.class, () ->
                DataPlaneRegistration.Builder.newInstance().build());
    }

    @Test
    @DisplayName("Build with transport profiles - profiles are stored and accessible")
    public void buildWithTransportProfilesSucceeds() {
        DataPlaneRegistration reg = DataPlaneRegistration.Builder.newInstance()
                .endpoint("http://dp.example.com")
                .supportedTransferTypes(Set.of("stream:grpc"))
                .transportProfiles(Set.of("stream:grpc"))
                .build();

        assertNotNull(reg);
        assertNotNull(reg.getTransportProfiles());
        assertTrue(reg.getTransportProfiles().contains("stream:grpc"));
    }

    @Test
    @DisplayName("Build without transport profiles - field is null and registration is still valid")
    public void buildWithoutTransportProfilesSucceeds() {
        DataPlaneRegistration reg = DataPlaneRegistration.Builder.newInstance()
                .endpoint("http://dp.example.com")
                .supportedTransferTypes(Set.of("HttpData-PULL"))
                .build();

        assertNotNull(reg);
        assertNull(reg.getTransportProfiles());
    }

    @Test
    @DisplayName("Build with apiKey and apiKeyHint retains both")
    public void buildWithApiKeyHintRetainsIt() {
        DataPlaneRegistration reg = DataPlaneRegistration.Builder.newInstance()
                .endpoint("http://dataplane.example.com")
                .supportedTransferTypes(Set.of("HttpData-PULL"))
                .apiKey("hashed-value-not-checked-here")
                .apiKeyHint("a1b2c3d4")
                .build();

        assertEquals("a1b2c3d4", reg.getApiKeyHint());
    }
}
