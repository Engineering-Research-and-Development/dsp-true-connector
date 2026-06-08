package it.eng.datatransfer.rest.api;

import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.service.DataFlowCallbackService;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
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
    private static final String PROCESS_ID = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId();

    private static final DataPlaneRegistration DATA_PLANE_REGISTRATION =
            DataPlaneRegistration.Builder.newInstance()
                    .endpoint("http://dataplane:9000")
                    .supportedTransferTypes(Set.of(DataTransferFormat.HTTP_PULL.format()))
                    .apiKey(VALID_API_KEY)
                    .build();

    private static final DataFlowStatusMessage COMPLETION_MESSAGE =
            DataFlowStatusMessage.Builder.newInstance()
                    .processId(PROCESS_ID)
                    .state(DataFlowState.COMPLETED)
                    .build();

    private static final DataFlowStatusMessage ERROR_MESSAGE =
            DataFlowStatusMessage.Builder.newInstance()
                    .processId(PROCESS_ID)
                    .state(DataFlowState.TERMINATED)
                    .errorMessage("Transfer failed")
                    .build();

    @Mock
    private DataFlowCallbackService callbackService;

    @Mock
    private DataPlaneRegistrationService registrationService;

    @InjectMocks
    private DataFlowCallbackController controller;

    // ── Legacy endpoint: complete ──────────────────────────────────────────────

    @Test
    @DisplayName("Legacy complete callback delegates to callbackService.handleCompleted")
    void completeCallbackDelegatesToCallbackService() {
        when(registrationService.findByApiKey(VALID_API_KEY))
                .thenReturn(Optional.of(DATA_PLANE_REGISTRATION));

        ResponseEntity<GenericApiResponse<?>> response =
                controller.completeCallback(VALID_API_KEY, COMPLETION_MESSAGE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        verify(callbackService).handleCompleted(COMPLETION_MESSAGE.getProcessId(),
                COMPLETION_MESSAGE.getDataAddress());
    }

    @Test
    @DisplayName("Legacy error callback delegates to callbackService.handleErrored")
    void errorCallbackDelegatesToCallbackService() {
        when(registrationService.findByApiKey(VALID_API_KEY))
                .thenReturn(Optional.of(DATA_PLANE_REGISTRATION));

        ResponseEntity<GenericApiResponse<?>> response =
                controller.errorCallback(VALID_API_KEY, ERROR_MESSAGE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        verify(callbackService).handleErrored(ERROR_MESSAGE.getProcessId(),
                ERROR_MESSAGE.getErrorMessage());
    }

    // ── Canonical endpoints ────────────────────────────────────────────────────

    @Test
    @DisplayName("Canonical prepared callback delegates to callbackService.handlePrepared")
    void preparedCallbackDelegatesToCallbackService() {
        when(registrationService.findByApiKey(VALID_API_KEY))
                .thenReturn(Optional.of(DATA_PLANE_REGISTRATION));
        var msg = DataFlowStatusMessage.Builder.newInstance()
                .processId(PROCESS_ID)
                .state(DataFlowState.PREPARED)
                .build();

        ResponseEntity<GenericApiResponse<?>> response =
                controller.preparedCallback(PROCESS_ID, VALID_API_KEY, msg);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(callbackService).handlePrepared(PROCESS_ID, msg.getDataAddress());
    }

    @Test
    @DisplayName("Canonical started callback delegates to callbackService.handleStarted")
    void startedCallbackDelegatesToCallbackService() {
        when(registrationService.findByApiKey(VALID_API_KEY))
                .thenReturn(Optional.of(DATA_PLANE_REGISTRATION));
        var msg = DataFlowStatusMessage.Builder.newInstance()
                .processId(PROCESS_ID)
                .state(DataFlowState.STARTED)
                .build();

        ResponseEntity<GenericApiResponse<?>> response =
                controller.startedCallback(PROCESS_ID, VALID_API_KEY, msg);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(callbackService).handleStarted(PROCESS_ID, msg.getDataAddress());
    }

    @Test
    @DisplayName("Canonical completed callback delegates to callbackService.handleCompleted")
    void completedCallbackDelegatesToCallbackService() {
        when(registrationService.findByApiKey(VALID_API_KEY))
                .thenReturn(Optional.of(DATA_PLANE_REGISTRATION));
        var msg = DataFlowStatusMessage.Builder.newInstance()
                .processId(PROCESS_ID)
                .state(DataFlowState.COMPLETED)
                .build();

        ResponseEntity<GenericApiResponse<?>> response =
                controller.completedCallback(PROCESS_ID, VALID_API_KEY, msg);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(callbackService).handleCompleted(PROCESS_ID, msg.getDataAddress());
    }

    @Test
    @DisplayName("Canonical errored callback delegates to callbackService.handleErrored")
    void erroredCallbackDelegatesToCallbackService() {
        when(registrationService.findByApiKey(VALID_API_KEY))
                .thenReturn(Optional.of(DATA_PLANE_REGISTRATION));
        var msg = DataFlowStatusMessage.Builder.newInstance()
                .processId(PROCESS_ID)
                .state(DataFlowState.TERMINATED)
                .errorMessage("dp crashed")
                .build();

        ResponseEntity<GenericApiResponse<?>> response =
                controller.erroredCallback(PROCESS_ID, VALID_API_KEY, msg);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(callbackService).handleErrored(PROCESS_ID, "dp crashed");
    }

    // ── API-key authentication ─────────────────────────────────────────────────

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
        assertFalse(completeResponse.getBody().isSuccess());
        assertEquals(HttpStatus.UNAUTHORIZED, errorResponse.getStatusCode());
        assertFalse(errorResponse.getBody().isSuccess());
        verifyNoInteractions(callbackService);
    }

    @Test
    @DisplayName("Complete callback with missing API key returns 401")
    void completeCallbackWithMissingApiKeyReturns401() {
        ResponseEntity<GenericApiResponse<?>> response =
                controller.completeCallback(null, COMPLETION_MESSAGE);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        verify(registrationService, never()).findByApiKey(any());
        verifyNoInteractions(callbackService);
    }

    @Test
    @DisplayName("Error callback with missing API key returns 401")
    void errorCallbackWithMissingApiKeyReturns401() {
        ResponseEntity<GenericApiResponse<?>> response =
                controller.errorCallback(null, ERROR_MESSAGE);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        verify(registrationService, never()).findByApiKey(any());
        verifyNoInteractions(callbackService);
    }

    @Test
    @DisplayName("Canonical completed callback with missing API key returns 401")
    void canonicalCompletedCallbackMissingApiKeyReturns401() {
        var msg = DataFlowStatusMessage.Builder.newInstance()
                .processId(PROCESS_ID).state(DataFlowState.COMPLETED).build();

        ResponseEntity<GenericApiResponse<?>> response =
                controller.completedCallback(PROCESS_ID, null, msg);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        verifyNoInteractions(callbackService);
    }

    @Test
    @DisplayName("Canonical errored callback with unknown API key returns 401")
    void canonicalErroredCallbackUnknownApiKeyReturns401() {
        when(registrationService.findByApiKey(UNKNOWN_API_KEY)).thenReturn(Optional.empty());
        var msg = DataFlowStatusMessage.Builder.newInstance()
                .processId(PROCESS_ID).state(DataFlowState.TERMINATED).build();

        ResponseEntity<GenericApiResponse<?>> response =
                controller.erroredCallback(PROCESS_ID, UNKNOWN_API_KEY, msg);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        verifyNoInteractions(callbackService);
    }
}

