package it.eng.dataplane.core.service;

import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.model.DataPlaneAuditEventType;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.repository.DataFlowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core service for managing data flow lifecycle on the Data Plane.
 * Orchestrates protocol implementations and Control Plane status callbacks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataFlowService {

    private final DataFlowRepository repository;
    private final DataTransferProtocolRegistry registry;
    private final DataPlaneAuditEventService auditEventService;
    private final DataFlowStateMachine stateMachine;

    /**
     * Starts a data transfer using the appropriate protocol implementation.
     * Persists the entity with {@link DataFlowState#STARTING} before delegating to the
     * protocol, then transitions to {@link DataFlowState#STARTED} on successful initiation.
     *
     * @param dataFlow the data flow request
     * @throws IllegalArgumentException if no protocol supports the transfer type
     * @throws IllegalStateException if a flow for this processId already exists
     */
    public void start(DataFlow dataFlow) {
        repository.findByProcessId(dataFlow.getProcessId()).ifPresent(existing -> {
            throw new IllegalStateException("DataFlow with processId " + dataFlow.getProcessId() + " already exists");
        });

        DataTransferProtocol protocol = requiredProtocol(dataFlow.getTransferType());
        DataFlowEntity entity = toEntity(dataFlow, DataFlowState.STARTING);
        repository.save(entity);

        auditEventService.saveEvent(DataPlaneAuditEventType.DATAFLOW_STARTED,
                dataFlow.getProcessId(), dataFlow.getTransferType(),
                "Data flow started", Map.of("dataFlowId", String.valueOf(dataFlow.getDataFlowId())));

        stateMachine.assertTransition(entity.getState(), DataFlowState.STARTED);
        DataFlowEntity startedEntity = entity.withState(DataFlowState.STARTED);
        repository.save(startedEntity);

        String processId = dataFlow.getProcessId();
        protocol.initiateTransfer(dataFlow)
            .thenAccept(result -> handleCompletion(processId, result))
            .exceptionally(ex -> { handleError(processId, ex); return null; });
    }

    /**
     * Resumes a suspended data transfer.
     *
     * @param processId the process ID to resume
     * @throws IllegalStateException if no flow exists for this processId or the state transition is invalid
     */
    public void resume(String processId) {
        DataFlowEntity entity = findRequired(processId);
        stateMachine.assertTransition(entity.getState(), DataFlowState.STARTED);
        requiredProtocol(entity.getTransferType())
                .resumeTransfer(entity.getId())
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        updateState(processId, DataFlowState.STARTED);
                        auditEventService.saveEvent(DataPlaneAuditEventType.DATAFLOW_RESUMED,
                                processId, entity.getTransferType(), "Data flow resumed", null);
                    } else {
                        handleError(processId, new RuntimeException(result.getErrorMessage()));
                    }
                })
                .exceptionally(ex -> {
                    handleError(processId, ex);
                    return null;
                });
    }

    /**
     * Returns the current entity for a data flow by process ID.
     *
     * @param processId the process ID to look up
     * @return the data flow entity
     * @throws IllegalStateException if no flow exists for this processId
     */
    public DataFlowEntity status(String processId) {
        return findRequired(processId);
    }

    /**
     * Terminates an active data transfer.
     *
     * @param processId the process ID to terminate
     * @throws IllegalStateException if no flow exists for this processId or the state transition is invalid
     */
    public void terminate(String processId) {
        DataFlowEntity entity = findRequired(processId);
        stateMachine.assertTransition(entity.getState(), DataFlowState.TERMINATED);
        String transferType = entity.getTransferType();

        DataTransferProtocol protocol = registry.getProtocol(transferType);
        if (protocol != null) {
            protocol.terminateTransfer(entity.getId())
                .thenAccept(result -> {
                    updateState(processId, DataFlowState.TERMINATED);
                    auditEventService.saveEvent(DataPlaneAuditEventType.DATAFLOW_TERMINATED,
                            processId, transferType, "Data flow terminated", null);
                })
                .exceptionally(ex -> { handleError(processId, ex); return null; });
        } else {
            updateState(processId, DataFlowState.TERMINATED);
            auditEventService.saveEvent(DataPlaneAuditEventType.DATAFLOW_TERMINATED,
                    processId, transferType, "Data flow terminated", null);
        }
    }

    /**
     * Suspends an active data transfer.
     *
     * @param processId the process ID to suspend
     * @throws IllegalStateException if no flow exists for this processId or the state transition is invalid
     */
    public void suspend(String processId) {
        DataFlowEntity entity = findRequired(processId);
        stateMachine.assertTransition(entity.getState(), DataFlowState.SUSPENDED);
        String transferType = entity.getTransferType();

        DataTransferProtocol protocol = registry.getProtocol(transferType);
        if (protocol != null) {
            protocol.suspendTransfer(entity.getId())
                .thenAccept(result -> {
                    updateState(processId, DataFlowState.SUSPENDED);
                    auditEventService.saveEvent(DataPlaneAuditEventType.DATAFLOW_SUSPENDED,
                            processId, transferType, "Data flow suspended", null);
                })
                .exceptionally(ex -> { handleError(processId, ex); return null; });
        } else {
            updateState(processId, DataFlowState.SUSPENDED);
            auditEventService.saveEvent(DataPlaneAuditEventType.DATAFLOW_SUSPENDED,
                    processId, transferType, "Data flow suspended", null);
        }
    }

    private void handleCompletion(String processId, DataFlowResult result) {
        if (result.isSuccess()) {
            DataFlowEntity fresh = findRequired(processId);
            stateMachine.assertTransition(fresh.getState(), DataFlowState.COMPLETED);
            DataFlowEntity completed = fresh.withState(DataFlowState.COMPLETED);
            repository.save(completed);
            auditEventService.saveEvent(DataPlaneAuditEventType.DATAFLOW_COMPLETED,
                    processId, completed.getTransferType(), "Data flow completed", null);
        } else {
            handleError(processId, new RuntimeException(result.getErrorMessage()));
        }
    }

    private void handleError(String processId, Throwable ex) {
        log.error("DataFlow processId={} failed: {}", processId, ex.getMessage(), ex);
        try {
            DataFlowEntity fresh = findRequired(processId);
            stateMachine.assertTransition(fresh.getState(), DataFlowState.TERMINATED);
            DataFlowEntity failed = fresh.withError(ex.getMessage(), DataFlowState.TERMINATED);
            repository.save(failed);
            auditEventService.saveEvent(DataPlaneAuditEventType.DATAFLOW_FAILED,
                    processId, failed.getTransferType(), "Data flow failed",
                    Map.of("error", ex.getMessage() != null ? ex.getMessage() : "unknown"));
        } catch (Exception saveEx) {
            log.error("Failed to persist TERMINATED state for DataFlow processId={}: {}", processId, saveEx.getMessage());
        }
    }

    private void updateState(String processId, DataFlowState state) {
        DataFlowEntity fresh = findRequired(processId);
        stateMachine.assertTransition(fresh.getState(), state);
        repository.save(fresh.withState(state));
    }

    private DataFlowEntity findRequired(String processId) {
        return repository.findByProcessId(processId)
                .orElseThrow(() -> new IllegalStateException("No DataFlow found for processId: " + processId));
    }

    private DataTransferProtocol requiredProtocol(String transferType) {
        DataTransferProtocol protocol = registry.getProtocol(transferType);
        if (protocol == null) {
            throw new IllegalArgumentException("No protocol registered for transferType: " + transferType);
        }
        return protocol;
    }

    private DataFlowEntity toEntity(DataFlow dataFlow, DataFlowState state) {
        Instant now = Instant.now();
        return DataFlowEntity.Builder.newInstance()
                .id(dataFlow.getDataFlowId() != null ? dataFlow.getDataFlowId() : UUID.randomUUID().toString())
                .processId(dataFlow.getProcessId())
                .agreementId(dataFlow.getAgreementId())
                .datasetId(dataFlow.getDatasetId())
                .transferType(dataFlow.getTransferType())
                .callbackAddress(dataFlow.getCallbackAddress())
                .state(state)
                .dataAddress(dataFlow.getDataAddress())
                .tenantId(dataFlow.getTenantId())
                .participantId(dataFlow.getParticipantId())
                .counterPartyId(dataFlow.getCounterPartyId())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
