package it.eng.dataplane.core.controller;

import it.eng.dataplane.core.config.DataPlaneProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ControlPlaneRegistrationController}.
 */
@ExtendWith(MockitoExtension.class)
class ControlPlaneRegistrationControllerTest {

    @Mock
    private DataPlaneProperties properties;

    @InjectMocks
    private ControlPlaneRegistrationController controller;

    @Test
    @DisplayName("registerControlPlane sets endpoint from payload and returns 200 OK")
    void registerControlPlane_setsEndpointAndReturns200() {
        Map<String, String> payload = Map.of("endpoint", "http://cp:8080");

        ResponseEntity<Void> response = controller.registerControlPlane(payload);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(properties).setControlPlaneEndpoint("http://cp:8080");
    }

    @Test
    @DisplayName("registerControlPlane handles missing endpoint key gracefully")
    void registerControlPlane_handlesAbsentEndpointKey() {
        Map<String, String> payload = Map.of("otherKey", "value");

        ResponseEntity<Void> response = controller.registerControlPlane(payload);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(properties).setControlPlaneEndpoint(null);
    }
}
