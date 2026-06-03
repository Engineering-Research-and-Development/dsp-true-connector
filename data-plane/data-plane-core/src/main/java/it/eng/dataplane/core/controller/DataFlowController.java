package it.eng.dataplane.core.controller;

import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.service.DataFlowConflictException;
import it.eng.dataplane.core.service.DataFlowService;
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
        } catch (DataFlowConflictException e) {
            log.error("DataFlow lifecycle conflict for processId {}: {}", message.getProcessId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IllegalStateException e) {
            // DataFlow for this processId already exists in an in-flight state — treat as idempotent OK.
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
     * <p>Delegates to {@link DataFlowService#prepare(DataFlowPrepareMessage)} which resolves the
     * registered {@link it.eng.dataplane.api.spi.DataTransferProtocol}, allocates resources
     * (e.g. temporary IAM credentials for HTTP-PUSH, or a pre-signed URL for HTTP-PULL), and
     * persists a {@link it.eng.dataplane.api.model.DataFlowState#PREPARED} entity keyed by
     * processId so that a later {@code terminate} can clean up those resources.</p>
     *
     * @param message the DataFlowPrepareMessage from the Control Plane
     * @return 200 OK with {@link DataFlowPrepareResponse} containing protocol-specific addressing data,
     *         400 BAD REQUEST if the protocol rejects the request (e.g. unknown sourceType),
     *         or 409 CONFLICT if the process already exists in an incompatible state
     */
    @PostMapping("/prepare")
    public ResponseEntity<DataFlowPrepareResponse> prepareDataFlow(@RequestBody DataFlowPrepareMessage message) {
        log.info("Received prepare request for processId={}", message.getProcessId());
        try {
            DataFlowPrepareResponse response = dataFlowService.prepare(message);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid prepare request for processId={}: {}", message.getProcessId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException e) {
            log.info("Prepare conflict for processId {}: {}", message.getProcessId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Terminates an active data transfer.
     *
     * <p>Canonical path: {@code /dataflows/{processId}/terminate}.
     * The canonical route accepts both {@code POST} and {@code DELETE} during migration,
     * while the legacy alias {@code POST /dataflows/terminate/{processId}} is preserved for
     * backward compatibility.</p>
     *
     * @param processId the transfer process ID to terminate
     * @return 200 OK on success, 404 NOT FOUND if processId not found
     */
    @RequestMapping(path = {"/{processId}/terminate", "/terminate/{processId}"},
            method = {RequestMethod.POST, RequestMethod.DELETE})
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
     * <p>Canonical path: {@code POST /dataflows/{processId}/suspend}.
     * Legacy alias {@code POST /dataflows/suspend/{processId}} is preserved for backward compatibility.</p>
     *
     * @param processId the transfer process ID to suspend
     * @return 200 OK on success, 404 NOT FOUND if processId not found
     */
    @PostMapping({"/{processId}/suspend", "/suspend/{processId}"})
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
     * Resumes a suspended data transfer.
     *
     * @param processId the transfer process ID to resume
     * @return 200 OK on success, 404 NOT FOUND if processId not found
     */
    @PostMapping("/{processId}/resume")
    public ResponseEntity<Void> resumeDataFlow(@PathVariable String processId) {
        log.info("Received resume request for processId={}", processId);

        try {
            dataFlowService.resume(processId);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            log.error("DataFlow not found or invalid state: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Returns the current status of a data transfer.
     *
     * @param processId the transfer process ID to query
     * @return 200 OK with {@link DataFlowStatusMessage} on success, 404 NOT FOUND if processId not found
     */
    @GetMapping("/{processId}/status")
    public ResponseEntity<DataFlowStatusMessage> statusDataFlow(@PathVariable String processId) {
        log.info("Received status request for processId={}", processId);

        try {
            DataFlowEntity entity = dataFlowService.status(processId);
            DataFlowStatusMessage message = DataFlowStatusMessage.Builder.newInstance()
                    .dataFlowId(entity.getId())
                    .processId(entity.getProcessId())
                    .state(entity.getState())
                    .dataAddress(entity.getDataAddress())
                    .errorMessage(entity.getErrorMessage())
                    .build();
            return ResponseEntity.ok(message);
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
            .dataAddress(message.getDataAddress() == null ? null : message.getDataAddress().toPropertyMap())
            .participantId(message.getParticipantId())
            .counterPartyId(message.getCounterPartyId())
            .build();
    }
}
