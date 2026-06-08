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
                .supportedTransferTypes(Set.of(DataTransferFormat.HTTP_PULL.format()))
                .build();

        assertNotNull(reg);
        assertNotNull(reg.getId());
        assertNotNull(reg.getRegisteredAt());
        assertEquals("http://dataplane.example.com", reg.getEndpoint());
        assertTrue(reg.getSupportedTransferTypes().contains(DataTransferFormat.HTTP_PULL.format()));
    }

    @Test
    @DisplayName("Missing endpoint throws ValidationException")
    public void missingEndpointThrowsValidation() {
        assertThrows(ValidationException.class, () ->
                DataPlaneRegistration.Builder.newInstance()
                        .supportedTransferTypes(Set.of(DataTransferFormat.HTTP_PULL.format()))
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
}
