package it.eng.datatransfer.rest.api;

import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataFlowCallbackControllerTest {

    private static final String VALID_API_KEY = "valid-api-key-123";
    private static final String UNKNOWN_API_KEY = "unknown-key";

    private static final DataPlaneRegistration DATA_PLANE_REGISTRATION =
            DataPlaneRegistration.Builder.newInstance()
                    .endpoint("http://dataplane:9000")
                    .supportedTransferTypes(Set.of("HttpData-PULL"))
                    .apiKey(VALID_API_KEY)
                    .build();

    private static final DataFlowStatusMessage COMPLETION_MESSAGE =
            DataFlowStatusMessage.Builder.newInstance()
                    .processId(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId())
                    .state(DataFlowState.COMPLETED)
                    .build();

    private static final DataFlowStatusMessage ERROR_MESSAGE =
            DataFlowStatusMessage.Builder.newInstance()
                    .processId(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId())
                    .state(DataFlowState.TERMINATED)
                    .errorMessage("Transfer failed")
                    .build();

    @Mock
    private DataTransferAPIService apiService;

    @Mock
    private DataPlaneRegistrationService registrationService;

    @InjectMocks
    private DataFlowCallbackController controller;

    @Test
    @DisplayName("Complete callback updates transfer process to COMPLETED")
    void completeCallbackUpdatesTransferProcessToCompleted() {
        when(registrationService.findByApiKey(VALID_API_KEY))
                .thenReturn(Optional.of(DATA_PLANE_REGISTRATION));

        ResponseEntity<GenericApiResponse<?>> response =
                controller.completeCallback(VALID_API_KEY, COMPLETION_MESSAGE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        verify(apiService).completeTransfer(COMPLETION_MESSAGE.getProcessId());
    }

    @Test
    @DisplayName("Error callback updates transfer process to TERMINATED")
    void errorCallbackUpdatesTransferProcessToTerminated() {
        when(registrationService.findByApiKey(VALID_API_KEY))
                .thenReturn(Optional.of(DATA_PLANE_REGISTRATION));

        ResponseEntity<GenericApiResponse<?>> response =
                controller.errorCallback(VALID_API_KEY, ERROR_MESSAGE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        verify(apiService).terminateTransfer(ERROR_MESSAGE.getProcessId());
    }

    @Test
    @DisplayName("Callback with unknown API key returns 401")
    void callbackWithUnknownApiKeyReturns401() {
        when(registrationService.findByApiKey(UNKNOWN_API_KEY))
                .thenReturn(Optional.empty());

        ResponseEntity<GenericApiResponse<?>> completeResponse =
                controller.completeCallback(UNKNOWN_API_KEY, COMPLETION_MESSAGE);
        ResponseEntity<GenericApiResponse<?>> errorResponse =
                controller.errorCallback(UNKNOWN_API_KEY, ERROR_MESSAGE);

        assertEquals(HttpStatus.UNAUTHORIZED, completeResponse.getStatusCode());
        assertNotNull(completeResponse.getBody());
        assertFalse(completeResponse.getBody().isSuccess());

        assertEquals(HttpStatus.UNAUTHORIZED, errorResponse.getStatusCode());
        assertNotNull(errorResponse.getBody());
        assertFalse(errorResponse.getBody().isSuccess());

        verify(apiService, never()).completeTransfer(anyString());
        verify(apiService, never()).terminateTransfer(anyString());
    }

    @Test
    @DisplayName("Complete callback with missing API key returns 401")
    void completeCallbackWithMissingApiKeyReturns401() {
        ResponseEntity<GenericApiResponse<?>> response =
                controller.completeCallback(null, COMPLETION_MESSAGE);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        verify(registrationService, never()).findByApiKey(any());
        verify(apiService, never()).completeTransfer(anyString());
    }

    @Test
    @DisplayName("Error callback with missing API key returns 401")
    void errorCallbackWithMissingApiKeyReturns401() {
        ResponseEntity<GenericApiResponse<?>> response =
                controller.errorCallback(null, ERROR_MESSAGE);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        verify(registrationService, never()).findByApiKey(any());
        verify(apiService, never()).terminateTransfer(anyString());
    }
}
