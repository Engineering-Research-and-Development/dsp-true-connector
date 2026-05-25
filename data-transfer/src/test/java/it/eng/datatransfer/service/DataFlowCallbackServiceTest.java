package it.eng.datatransfer.service;

import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataFlowCallbackServiceTest {

    private static final TransferProcess TRANSFER_PROCESS =
            DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;

    private static final String PROCESS_ID = TRANSFER_PROCESS.getId();

    @Mock
    private TransferProcessRepository repository;

    @Mock
    private DataTransferAPIService apiService;

    @InjectMocks
    private DataFlowCallbackService service;

    // ── handlePrepared ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("handlePrepared persists dataFlowState=PREPARED")
    void handlePreparedPersistsDataFlowState() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(TRANSFER_PROCESS));

        service.handlePrepared(PROCESS_ID, null);

        verify(repository).save(argThat(saved -> "PREPARED".equals(saved.getDataFlowState())));
        verifyNoInteractions(apiService);
    }

    @Test
    @DisplayName("handlePrepared maps dataAddress from map")
    void handlePreparedMapsDataAddress() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(TRANSFER_PROCESS));

        service.handlePrepared(PROCESS_ID, Map.of("endpointType", "HttpData", "endpoint", "https://example.com"));

        ArgumentCaptor<TransferProcess> captor = ArgumentCaptor.forClass(TransferProcess.class);
        verify(repository).save(captor.capture());
        TransferProcess saved = captor.getValue();
        assertEquals("PREPARED", saved.getDataFlowState());
        assertNotNull(saved.getDataAddress());
        assertEquals("HttpData", saved.getDataAddress().getEndpointType());
        assertEquals("https://example.com", saved.getDataAddress().getEndpoint());
    }

    // ── handleStarted ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleStarted persists dataFlowState=STARTED")
    void handleStartedPersistsDataFlowState() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(TRANSFER_PROCESS));

        service.handleStarted(PROCESS_ID, Map.of("endpointType", "HttpData"));

        verify(repository).save(argThat(saved -> "STARTED".equals(saved.getDataFlowState())));
        verifyNoInteractions(apiService);
    }

    @Test
    @DisplayName("handleStarted maps extra properties into EndpointProperties")
    void handleStartedMapsExtraPropertiesToEndpointProperties() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(TRANSFER_PROCESS));

        service.handleStarted(PROCESS_ID, Map.of("endpointType", "HttpData", "token", "abc123"));

        ArgumentCaptor<TransferProcess> captor = ArgumentCaptor.forClass(TransferProcess.class);
        verify(repository).save(captor.capture());
        DataAddress addr = captor.getValue().getDataAddress();
        assertNotNull(addr);
        assertNotNull(addr.getEndpointProperties());
        assertTrue(addr.getEndpointProperties().stream()
                .anyMatch(p -> "token".equals(p.getName()) && "abc123".equals(p.getValue())));
    }

    // ── handleCompleted ────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleCompleted persists dataFlowState=COMPLETED before delegating")
    void handleCompletedPersistsStateBeforeDelegating() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(TRANSFER_PROCESS));

        service.handleCompleted(PROCESS_ID, null);

        var inOrder = inOrder(repository, apiService);
        inOrder.verify(repository).save(argThat(saved -> "COMPLETED".equals(saved.getDataFlowState())));
        inOrder.verify(apiService).completeTransfer(PROCESS_ID);
    }

    @Test
    @DisplayName("handleCompleted with dataAddress maps address and delegates")
    void handleCompletedWithDataAddressMapsAndDelegates() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(TRANSFER_PROCESS));

        service.handleCompleted(PROCESS_ID, Map.of("endpoint", "https://presigned-url.example.com"));

        ArgumentCaptor<TransferProcess> captor = ArgumentCaptor.forClass(TransferProcess.class);
        verify(repository).save(captor.capture());
        TransferProcess saved = captor.getValue();
        assertEquals("COMPLETED", saved.getDataFlowState());
        assertEquals("https://presigned-url.example.com", saved.getDataAddress().getEndpoint());
        verify(apiService).completeTransfer(PROCESS_ID);
    }

    @Test
    @DisplayName("handleCompleted restores previous process when completion delegation fails")
    void handleCompletedRestoresPreviousProcessWhenDelegationFails() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(TRANSFER_PROCESS));
        doThrow(new RuntimeException("completion failed")).when(apiService).completeTransfer(PROCESS_ID);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.handleCompleted(PROCESS_ID, null));

        assertEquals("completion failed", exception.getMessage());
        var inOrder = inOrder(repository, apiService);
        inOrder.verify(repository).save(argThat(saved -> "COMPLETED".equals(saved.getDataFlowState())));
        inOrder.verify(apiService).completeTransfer(PROCESS_ID);
        inOrder.verify(repository).save(argThat(saved -> saved.getDataFlowState() == null
                && saved.getDataFlowErrorMessage() == null
                && saved.getState() == TRANSFER_PROCESS.getState()));
    }

    // ── handleErrored ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleErrored persists error message before delegating termination")
    void handleErroredPersistsErrorBeforeTerminating() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(TRANSFER_PROCESS));

        service.handleErrored(PROCESS_ID, "provider dp failed");

        var inOrder = inOrder(repository, apiService);
        inOrder.verify(repository).save(argThat(
                saved -> "provider dp failed".equals(saved.getDataFlowErrorMessage())
                        && "TERMINATED".equals(saved.getDataFlowState())));
        inOrder.verify(apiService).terminateTransfer(PROCESS_ID);
    }

    @Test
    @DisplayName("handleErrored with null error message still transitions to TERMINATED")
    void handleErroredWithNullMessageStillTerminates() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(TRANSFER_PROCESS));

        service.handleErrored(PROCESS_ID, null);

        verify(repository).save(argThat(saved -> "TERMINATED".equals(saved.getDataFlowState())));
        verify(apiService).terminateTransfer(PROCESS_ID);
    }

    @Test
    @DisplayName("handleErrored restores previous process when termination delegation fails")
    void handleErroredRestoresPreviousProcessWhenDelegationFails() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(TRANSFER_PROCESS));
        doThrow(new RuntimeException("termination failed")).when(apiService).terminateTransfer(PROCESS_ID);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.handleErrored(PROCESS_ID, "provider dp failed"));

        assertEquals("termination failed", exception.getMessage());
        var inOrder = inOrder(repository, apiService);
        inOrder.verify(repository).save(argThat(saved -> "TERMINATED".equals(saved.getDataFlowState())
                && "provider dp failed".equals(saved.getDataFlowErrorMessage())));
        inOrder.verify(apiService).terminateTransfer(PROCESS_ID);
        inOrder.verify(repository).save(argThat(saved -> saved.getDataFlowState() == null
                && saved.getDataFlowErrorMessage() == null
                && saved.getState() == TRANSFER_PROCESS.getState()));
    }

    // ── findRequired error handling ────────────────────────────────────────────

    @Test
    @DisplayName("handleCompleted throws IllegalStateException when TransferProcess not found")
    void handleCompletedThrowsWhenNotFound() {
        when(repository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.handleCompleted("unknown-id", null));
        verify(apiService, never()).completeTransfer(anyString());
    }

    @Test
    @DisplayName("handleErrored throws IllegalStateException when TransferProcess not found")
    void handleErroredThrowsWhenNotFound() {
        when(repository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.handleErrored("unknown-id", "some error"));
        verify(apiService, never()).terminateTransfer(anyString());
    }

    // ── dataAddressFromMap ─────────────────────────────────────────────────────

    @Test
    @DisplayName("dataAddressFromMap returns null for null input")
    void dataAddressFromMapNullInput() {
        assertNull(service.dataAddressFromMap(null));
    }

    @Test
    @DisplayName("dataAddressFromMap returns null for empty map")
    void dataAddressFromMapEmptyInput() {
        assertNull(service.dataAddressFromMap(Map.of()));
    }

    @Test
    @DisplayName("dataAddressFromMap maps endpointType and endpoint to dedicated fields")
    void dataAddressFromMapMapsKnownKeys() {
        var result = service.dataAddressFromMap(
                Map.of("endpointType", "HttpData", "endpoint", "https://host/path"));

        assertEquals("HttpData", result.getEndpointType());
        assertEquals("https://host/path", result.getEndpoint());
        assertNull(result.getEndpointProperties());
    }

    @Test
    @DisplayName("dataAddressFromMap puts extra keys into EndpointProperties")
    void dataAddressFromMapExtraKeysGoToProperties() {
        var result = service.dataAddressFromMap(
                Map.of("endpointType", "HttpData", "authCode", "secret"));

        assertEquals("HttpData", result.getEndpointType());
        assertNotNull(result.getEndpointProperties());
        assertTrue(result.getEndpointProperties().stream()
                .anyMatch(p -> "authCode".equals(p.getName()) && "secret".equals(p.getValue())));
    }
}
