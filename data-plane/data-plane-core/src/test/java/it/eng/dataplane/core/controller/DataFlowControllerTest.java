package it.eng.dataplane.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.service.DataFlowService;
import it.eng.dataplane.core.service.DataPlaneAuditEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataFlowController}.
 */
@ExtendWith(MockitoExtension.class)
class DataFlowControllerTest {

    @Mock
    private DataFlowService dataFlowService;

    @Mock
    private DataTransferProtocolRegistry protocolRegistry;

    @Mock
    private DataPlaneAuditEventService auditEventService;

    @Mock
    private DataTransferProtocol protocol;

    @InjectMocks
    private DataFlowController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DataFlowStartMessage buildStartMessage(String processId) {
        return DataFlowStartMessage.Builder.newInstance()
                .processId(processId)
                .transferType("HttpData-PULL")
                .agreementId("agree-1")
                .datasetId("dataset-1")
                .callbackAddress("http://cp:8080/callback")
                .build();
    }

    private DataFlowPrepareMessage buildPrepareMessage(String processId, Map<String, String> dataAddress) {
        return DataFlowPrepareMessage.Builder.newInstance()
                .processId(processId)
                .dataAddress(dataAddress)
                .build();
    }

    // ─── startDataFlow ───────────────────────────────────────────────────────

    @Test
    @DisplayName("startDataFlow returns 201 CREATED on success")
    void startDataFlow_returns201OnSuccess() {
        ResponseEntity<Void> response = controller.startDataFlow(buildStartMessage("proc-1"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(dataFlowService).start(any());
    }

    @Test
    @DisplayName("startDataFlow returns 200 OK when DataFlow already exists (idempotent)")
    void startDataFlow_returns200WhenAlreadyExists() {
        doThrow(new IllegalStateException("already exists")).when(dataFlowService).start(any());

        ResponseEntity<Void> response = controller.startDataFlow(buildStartMessage("proc-dup"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("startDataFlow returns 400 BAD REQUEST on invalid argument")
    void startDataFlow_returns400OnIllegalArgument() {
        doThrow(new IllegalArgumentException("invalid")).when(dataFlowService).start(any());

        ResponseEntity<Void> response = controller.startDataFlow(buildStartMessage("proc-bad"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ─── prepareDataFlow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("prepareDataFlow delegates to protocol matched by transferType in dataAddress")
    void prepareDataFlow_delegatesToProtocol() {
        DataFlowPrepareResponse expected = DataFlowPrepareResponse.Builder.newInstance()
                .processId("proc-1")
                .build();
        when(protocolRegistry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.prepare(any())).thenReturn(expected);

        ResponseEntity<DataFlowPrepareResponse> response = controller.prepareDataFlow(
                buildPrepareMessage("proc-1", Map.of("transferType", "HttpData-PULL")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    @DisplayName("prepareDataFlow falls back to single registered protocol when transferType absent")
    void prepareDataFlow_fallsBackToSingleProtocol() {
        DataFlowPrepareResponse expected = DataFlowPrepareResponse.Builder.newInstance()
                .processId("proc-2")
                .build();
        when(protocolRegistry.getProtocol("")).thenReturn(null);
        when(protocolRegistry.getSupportedProtocols()).thenReturn(Set.of("HttpData-PUSH"));
        when(protocolRegistry.getProtocol("HttpData-PUSH")).thenReturn(protocol);
        when(protocol.prepare(any())).thenReturn(expected);

        ResponseEntity<DataFlowPrepareResponse> response = controller.prepareDataFlow(
                buildPrepareMessage("proc-2", Map.of()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    @DisplayName("prepareDataFlow returns empty response when no protocol registered")
    void prepareDataFlow_returnsEmptyResponseWhenNoProtocol() {
        when(protocolRegistry.getProtocol("")).thenReturn(null);
        when(protocolRegistry.getSupportedProtocols()).thenReturn(Set.of());

        ResponseEntity<DataFlowPrepareResponse> response = controller.prepareDataFlow(
                buildPrepareMessage("proc-3", Map.of()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("proc-3", response.getBody().getProcessId());
    }

    @Test
    @DisplayName("prepareDataFlow handles null dataAddress without NPE")
    void prepareDataFlow_handlesNullDataAddress() {
        DataFlowPrepareResponse expected = DataFlowPrepareResponse.Builder.newInstance()
                .processId("proc-4")
                .build();
        when(protocolRegistry.getProtocol("")).thenReturn(null);
        when(protocolRegistry.getSupportedProtocols()).thenReturn(Set.of("HttpData-PULL"));
        when(protocolRegistry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.prepare(any())).thenReturn(expected);

        ResponseEntity<DataFlowPrepareResponse> response = controller.prepareDataFlow(
                buildPrepareMessage("proc-4", null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ─── terminateDataFlow ───────────────────────────────────────────────────

    @Test
    @DisplayName("terminateDataFlow returns 200 OK on success")
    void terminateDataFlow_returns200OnSuccess() {
        ResponseEntity<Void> response = controller.terminateDataFlow("proc-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(dataFlowService).terminate("proc-1");
    }

    @Test
    @DisplayName("terminateDataFlow returns 404 when processId not found")
    void terminateDataFlow_returns404WhenNotFound() {
        doThrow(new IllegalStateException("not found")).when(dataFlowService).terminate("missing");

        ResponseEntity<Void> response = controller.terminateDataFlow("missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ─── suspendDataFlow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("suspendDataFlow returns 200 OK on success")
    void suspendDataFlow_returns200OnSuccess() {
        ResponseEntity<Void> response = controller.suspendDataFlow("proc-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(dataFlowService).suspend("proc-1");
    }

    @Test
    @DisplayName("suspendDataFlow returns 404 when processId not found")
    void suspendDataFlow_returns404WhenNotFound() {
        doThrow(new IllegalStateException("not found")).when(dataFlowService).suspend("missing");

        ResponseEntity<Void> response = controller.suspendDataFlow("missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ─── resumeDataFlow ──────────────────────────────────────────────────────

    @Test
    @DisplayName("resumeDataFlow returns 200 OK on success")
    void resumeDataFlow_returns200OnSuccess() {
        ResponseEntity<Void> response = controller.resumeDataFlow("proc-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(dataFlowService).resume("proc-1");
    }

    @Test
    @DisplayName("resumeDataFlow returns 404 when processId not found")
    void resumeDataFlow_returns404WhenNotFound() {
        doThrow(new IllegalStateException("not found")).when(dataFlowService).resume("missing");

        ResponseEntity<Void> response = controller.resumeDataFlow("missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ─── statusDataFlow ──────────────────────────────────────────────────────

    @Test
    @DisplayName("statusDataFlow returns 200 OK with DataFlowStatusMessage on success")
    void statusDataFlow_returns200OnSuccess() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("entity-id-1")
                .processId("proc-1")
                .state(DataFlowState.STARTED)
                .build();
        when(dataFlowService.status("proc-1")).thenReturn(entity);

        ResponseEntity<DataFlowStatusMessage> response = controller.statusDataFlow("proc-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("proc-1", response.getBody().getProcessId());
        assertEquals("entity-id-1", response.getBody().getDataFlowId());
        assertEquals(DataFlowState.STARTED, response.getBody().getState());
    }

    @Test
    @DisplayName("statusDataFlow returns 404 when processId not found")
    void statusDataFlow_returns404WhenNotFound() {
        doThrow(new IllegalStateException("not found")).when(dataFlowService).status("missing");

        ResponseEntity<DataFlowStatusMessage> response = controller.statusDataFlow("missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
