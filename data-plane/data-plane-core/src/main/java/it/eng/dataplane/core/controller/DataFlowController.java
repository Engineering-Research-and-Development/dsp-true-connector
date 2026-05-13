package it.eng.dataplane.core.controller;

import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.core.model.DataPlaneAuditEventType;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.service.DataFlowService;
import it.eng.dataplane.core.service.DataPlaneAuditEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller receiving DPS messages from Control Plane.
 * Handles data flow lifecycle requests and delegates to the DataFlowService.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/dataflows")
public class DataFlowController {

    private final DataFlowService dataFlowService;
    private final DataTransferProtocolRegistry protocolRegistry;
    private final DataPlaneAuditEventService auditEventService;

    /**
     * Initiates a new data transfer based on a Control Plane request.
     *
     * @param message the DataFlowStartMessage from the Control Plane
     * @return 201 CREATED on success, 400 BAD REQUEST on duplicate processId
     */
    @PostMapping("/start")
    public ResponseEntity<Void> startDataFlow(@RequestBody DataFlowStartMessage message) {
        log.info("Received start request for processId={}, transferType={}", 
            message.getProcessId(), message.getTransferType());
        
        try {
            DataFlow dataFlow = toDataFlow(message);
            dataFlowService.start(dataFlow);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IllegalStateException e) {
            // DataFlow for this processId already exists — treat as idempotent OK.
            // This can happen when the Control Plane retries the start call (e.g. [P] Push data
            // manual trigger after DPS auto-start). Returning 200 prevents false error propagation.
            log.info("DataFlow already exists for processId {}, returning OK (idempotent)", message.getProcessId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Handles a prepare request from the Control Plane.
     *
     * <p>Delegates to the registered {@link it.eng.dataplane.api.spi.DataTransferProtocol}
     * implementation so the Data Plane can allocate resources (e.g. temporary IAM credentials
     * for HTTP-PUSH, or generate a pre-signed URL for HTTP-PULL) before the DSP transfer
     * messages are exchanged between connectors.</p>
     *
     * @param message the DataFlowPrepareMessage from the Control Plane
     * @return 200 OK with {@link DataFlowPrepareResponse} containing protocol-specific addressing data
     */
    @PostMapping("/prepare")
    public ResponseEntity<DataFlowPrepareResponse> prepareDataFlow(@RequestBody DataFlowPrepareMessage message) {
        log.info("Received prepare request for processId={}", message.getProcessId());
        String transferType = message.getDataAddress() != null
                ? message.getDataAddress().getOrDefault("transferType", "")
                : "";
        var protocol = protocolRegistry.getProtocol(transferType);
        if (protocol == null) {
            // Each DP container hosts exactly one protocol — fall back to it when the
            // Control Plane does not embed transferType in the prepare message dataAddress.
            var supported = protocolRegistry.getSupportedProtocols();
            if (!supported.isEmpty()) {
                String fallback = supported.iterator().next();
                protocol = protocolRegistry.getProtocol(fallback);
                log.info("No transferType in prepare message; using single registered protocol '{}'", fallback);
            }
        }
        DataFlowPrepareResponse response;
        if (protocol != null) {
            response = protocol.prepare(message);
        } else {
            log.warn("No protocol registered; returning empty prepare response for processId={}", message.getProcessId());
            response = DataFlowPrepareResponse.Builder.newInstance()
                    .processId(message.getProcessId())
                    .build();
        }
        auditEventService.saveEvent(DataPlaneAuditEventType.DATAFLOW_PREPARE_REQUESTED,
                message.getProcessId(), transferType,
                "Data flow prepare requested", null);
        return ResponseEntity.ok(response);
    }

    /**
     * Terminates an active data transfer.
     *
     * @param processId the transfer process ID to terminate
     * @return 200 OK on success, 404 NOT FOUND if processId not found
     */
    @PostMapping("/terminate/{processId}")
    public ResponseEntity<Void> terminateDataFlow(@PathVariable String processId) {
        log.info("Received terminate request for processId={}", processId);
        
        try {
            dataFlowService.terminate(processId);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            log.error("DataFlow not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Suspends an active data transfer.
     *
     * @param processId the transfer process ID to suspend
     * @return 200 OK on success, 404 NOT FOUND if processId not found
     */
    @PostMapping("/suspend/{processId}")
    public ResponseEntity<Void> suspendDataFlow(@PathVariable String processId) {
        log.info("Received suspend request for processId={}", processId);
        
        try {
            dataFlowService.suspend(processId);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            log.error("DataFlow not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Converts a DataFlowStartMessage to a DataFlow domain object.
     *
     * @param message the incoming message from Control Plane
     * @return populated DataFlow instance
     */
    private DataFlow toDataFlow(DataFlowStartMessage message) {
        return DataFlow.Builder.newInstance()
            .processId(message.getProcessId())
            .transferType(message.getTransferType())
            .agreementId(message.getAgreementId())
            .datasetId(message.getDatasetId())
            .callbackAddress(message.getCallbackAddress())
            .dataAddress(message.getDataAddress())
            .participantId(message.getParticipantId())
            .counterPartyId(message.getCounterPartyId())
            .build();
    }
}
