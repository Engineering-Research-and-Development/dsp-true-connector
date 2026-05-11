package it.eng.dataplane.core.controller;

import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.dataplane.api.model.DataFlow;
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
        } catch (IllegalStateException e) {
            log.error("DataFlow already exists: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
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
