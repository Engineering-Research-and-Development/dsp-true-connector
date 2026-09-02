package it.eng.dataplane.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataAddress;
import it.eng.dataplane.api.message.EndpointProperty;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.service.DataFlowConflictException;
import it.eng.dataplane.core.service.DataFlowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

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

    private DataFlowPrepareMessage buildPrepareMessage(String processId, Map<String, Object> metadata) {
        return DataFlowPrepareMessage.Builder.newInstance()
                .processId(processId)
                .transferType(metadata == null ? null : (String) metadata.get(DataPlaneConstants.METADATA_FIELD_TRANSFER_TYPE))
                .metadata(metadata)
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
    @DisplayName("startDataFlow returns 409 CONFLICT when DataFlow is in STARTED lifecycle state")
    void startDataFlow_returns409WhenDataFlowExistsInStartedState() {
        doThrow(new DataFlowConflictException("DataFlow proc-started already in STARTED state"))
                .when(dataFlowService).start(any());

        ResponseEntity<Void> response = controller.startDataFlow(buildStartMessage("proc-started"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @DisplayName("startDataFlow returns 409 CONFLICT when DataFlow is in COMPLETED lifecycle state")
    void startDataFlow_returns409WhenDataFlowExistsInCompletedState() {
        doThrow(new DataFlowConflictException("DataFlow proc-completed already in COMPLETED state"))
                .when(dataFlowService).start(any());

        ResponseEntity<Void> response = controller.startDataFlow(buildStartMessage("proc-completed"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @DisplayName("startDataFlow returns 409 CONFLICT when DataFlow is in TERMINATED lifecycle state")
    void startDataFlow_returns409WhenDataFlowExistsInTerminatedState() {
        doThrow(new DataFlowConflictException("DataFlow proc-terminated already in TERMINATED state"))
                .when(dataFlowService).start(any());

        ResponseEntity<Void> response = controller.startDataFlow(buildStartMessage("proc-terminated"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @DisplayName("startDataFlow returns 400 BAD REQUEST on invalid argument")
    void startDataFlow_returns400OnIllegalArgument() {
        doThrow(new IllegalArgumentException("invalid")).when(dataFlowService).start(any());

        ResponseEntity<Void> response = controller.startDataFlow(buildStartMessage("proc-bad"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("startDataFlow converts schema-aligned dataAddress into internal flat map")
    void startDataFlow_convertsDataAddressToInternalMap() {
        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpointType("grpc")
                .endpoint("grpc://dp-grpc:5050")
                .endpointProperties(List.of(
                        EndpointProperty.Builder.newInstance().name("sessionId").value("sess-123").build(),
                        EndpointProperty.Builder.newInstance().name("mode").value("non-finite").build()))
                .build();
        DataFlowStartMessage message = DataFlowStartMessage.Builder.newInstance()
                .processId("proc-grpc")
                .transferType("stream:grpc")
                .callbackAddress("http://cp:8080/callback")
                .dataAddress(dataAddress)
                .build();

        ResponseEntity<Void> response = controller.startDataFlow(message);

        ArgumentCaptor<DataFlow> dataFlowCaptor = ArgumentCaptor.forClass(DataFlow.class);
        verify(dataFlowService).start(dataFlowCaptor.capture());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(Map.of(
                "endpoint", "grpc://dp-grpc:5050",
                "endpointType", "grpc",
                "sessionId", "sess-123",
                "mode", "non-finite"), dataFlowCaptor.getValue().getDataAddress());
    }

    @Test
    @DisplayName("startDataFlow forwards structured metadata to the runtime DataFlow")
    void startDataFlow_forwardsStructuredMetadata() {
        Map<String, Object> metadata = Map.of(
                DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                        DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket")));
        DataFlowStartMessage message = DataFlowStartMessage.Builder.newInstance()
                .processId("proc-meta")
                .transferType("HttpData-PUSH")
                .callbackAddress("http://cp:8080/callback")
                .metadata(metadata)
                .build();

        ResponseEntity<Void> response = controller.startDataFlow(message);

        ArgumentCaptor<DataFlow> dataFlowCaptor = ArgumentCaptor.forClass(DataFlow.class);
        verify(dataFlowService).start(dataFlowCaptor.capture());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(metadata, dataFlowCaptor.getValue().getMetadata());
    }

    // ─── prepareDataFlow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("prepareDataFlow returns 200 OK with service response")
    void prepareDataFlow_returns200WithServiceResponse() {
        DataFlowPrepareResponse expected = DataFlowPrepareResponse.Builder.newInstance()
                .processId("proc-1")
                .build();
        when(dataFlowService.prepare(any())).thenReturn(expected);

        ResponseEntity<DataFlowPrepareResponse> response = controller.prepareDataFlow(
                buildPrepareMessage("proc-1", Map.of("transferType", "HttpData-PULL")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(dataFlowService).prepare(any(DataFlowPrepareMessage.class));
    }

    @Test
    @DisplayName("prepareDataFlow returns 200 OK with null metadata")
    void prepareDataFlow_returns200WithNullMetadata() {
        DataFlowPrepareResponse expected = DataFlowPrepareResponse.Builder.newInstance()
                .processId("proc-4")
                .build();
        when(dataFlowService.prepare(any())).thenReturn(expected);

        ResponseEntity<DataFlowPrepareResponse> response = controller.prepareDataFlow(
                buildPrepareMessage("proc-4", null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("prepareDataFlow returns 400 BAD_REQUEST when service throws IllegalArgumentException")
    void prepareDataFlow_returns400OnIllegalArgumentFromService() {
        doThrow(new IllegalArgumentException("No SourceReader available for sourceType: unknown"))
                .when(dataFlowService).prepare(any());

        ResponseEntity<DataFlowPrepareResponse> response = controller.prepareDataFlow(
                buildPrepareMessage("proc-err", Map.of("transferType", "stream:grpc")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ─── terminateDataFlow ───────────────────────────────────────────────────

    @Test
    @DisplayName("prepareDataFlow routes through DataFlowService instead of invoking protocol directly")
    void prepareDataFlow_routesThroughDataFlowService() {
        DataFlowPrepareMessage message = buildPrepareMessage("proc-svc", Map.of("transferType", "HttpData-PUSH"));
        DataFlowPrepareResponse expected = DataFlowPrepareResponse.Builder.newInstance()
                .processId("proc-svc")
                .build();
        when(dataFlowService.prepare(any())).thenReturn(expected);

        controller.prepareDataFlow(message);

        // The controller must delegate to the service — not bypass it
        verify(dataFlowService).prepare(message);
    }

    @Test
    @DisplayName("prepareDataFlow returns 409 CONFLICT when DataFlowService.prepare throws IllegalStateException")
    void prepareDataFlow_returns409WhenServiceThrowsIllegalState() {
        DataFlowPrepareMessage message = buildPrepareMessage("proc-svc-bad",
                Map.of("transferType", "stream:grpc"));
        doThrow(new IllegalStateException("already exists in state STARTED"))
                .when(dataFlowService).prepare(any());

        ResponseEntity<DataFlowPrepareResponse> response = controller.prepareDataFlow(message);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
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
