package it.eng.datatransfer.rest.api;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPlaneRegistrationControllerTest {

    private static final String BOOTSTRAP_KEY = "bootstrap-key-value";

    private static final DataPlaneRegistration REGISTRATION =
            DataPlaneRegistration.Builder.newInstance()
                    .endpoint("http://dataplane:9000")
                    .supportedTransferTypes(Set.of("HttpData-PULL"))
                    .apiKey("raw-dp-key")
                    .build();

    @Mock
    private DataPlaneRegistrationService service;

    private DataPlaneRegistrationController controller;

    @BeforeEach
    void setUp() {
        controller = new DataPlaneRegistrationController(service, BOOTSTRAP_KEY);
    }

    @Test
    @DisplayName("register succeeds with a matching X-Registration-Key")
    void registerSucceedsWithMatchingKey() {
        when(service.register(REGISTRATION)).thenReturn(REGISTRATION);

        ResponseEntity<GenericApiResponse<DataPlaneRegistration>> response =
                controller.register(BOOTSTRAP_KEY, REGISTRATION);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(service).register(REGISTRATION);
    }

    @Test
    @DisplayName("register rejects a missing X-Registration-Key")
    void registerRejectsMissingKey() {
        ResponseEntity<GenericApiResponse<DataPlaneRegistration>> response =
                controller.register(null, REGISTRATION);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(service, never()).register(any());
    }

    @Test
    @DisplayName("register rejects a mismatched X-Registration-Key")
    void registerRejectsMismatchedKey() {
        ResponseEntity<GenericApiResponse<DataPlaneRegistration>> response =
                controller.register("wrong-key", REGISTRATION);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(service, never()).register(any());
    }

    @Test
    @DisplayName("deregister delegates the presented X-Api-Key to the service")
    void deregisterDelegatesApiKeyToService() {
        ResponseEntity<Void> response = controller.deregister("dp-id-1", "raw-dp-key");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(service).deregister("dp-id-1", "raw-dp-key");
    }
}
