package it.eng.dataplane.core.service;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareMetadata;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
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
import java.util.Optional;
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
     * Prepares resources for a data transfer and persists a {@link DataFlowState#PREPARED} entity.
     *
     * <p>Resolves the protocol for the requested transfer type (with fallback to the single
     * registered protocol when the type is absent), delegates to
     * {@link DataTransferProtocol#prepare(DataFlowPrepareMessage)}, and persists a PREPARED
     * {@link DataFlowEntity} keyed by processId so that a later {@code terminate} can clean up
     * any allocated resources even before {@code start} is called.</p>
     *
     * @param message the prepare message from the Control Plane
     * @return the prepare response carrying protocol-specific addressing data
     * @throws IllegalArgumentException if the protocol rejects the request
     */
    public DataFlowPrepareResponse prepare(DataFlowPrepareMessage message) {
        Optional<DataFlowEntity> existingOpt = repository.findByProcessId(message.getProcessId());
        if (existingOpt.isPresent()) {
            DataFlowEntity existing = existingOpt.get();
            // VIEW-mode prepare must always generate a fresh presigned URL; do NOT return the
            // cached data address because presigned URLs expire and become stale after each call.
            boolean isViewMode = "VIEW".equals(
                    DataFlowPrepareMetadata.from(message).getSinkSection()
                            .getString(DataPlaneConstants.METADATA_FIELD_MODE));
            if (existing.getState() == DataFlowState.PREPARED && !isViewMode) {
                log.info("Reusing PREPARED DataFlow for processId={}", message.getProcessId());
                return DataFlowPrepareResponse.Builder.newInstance()
                        .processId(message.getProcessId())
                        .dataAddress(existing.getDataAddress())
                        .build();
            }
            // Allow a fresh prepare when the previous flow reached a terminal state, or when the
            // existing PREPARED entry is being refreshed for a VIEW request (presigned URL renewal).
            // This also covers retry-after-rollback (DP was TERMINATED by a failed
            // peer-notification rollback, and the CP now re-prepares for the same processId).
            if (existing.getState() != DataFlowState.PREPARED
                    && existing.getState() != DataFlowState.TERMINATED
                    && existing.getState() != DataFlowState.COMPLETED) {
                throw new IllegalStateException("DataFlow with processId " + message.getProcessId()
                        + " already exists in state " + existing.getState());
            }
            log.info("Allowing fresh prepare over {} DataFlow for processId={}{}",
                    existing.getState(), message.getProcessId(), isViewMode ? " (VIEW mode)" : "");
        }

        String transferType = DataFlowPrepareMetadata.from(message).getTransferType();
        if (transferType == null) {
            transferType = "";
        }

        DataTransferProtocol protocol = registry.getProtocol(transferType);
        if (protocol == null) {
            var supported = registry.getSupportedProtocols();
            if (!supported.isEmpty()) {
                String fallback = supported.iterator().next();
                protocol = registry.getProtocol(fallback);
                transferType = fallback;
                log.info("No transferType in prepare message; using single registered protocol '{}'", fallback);
            }
        }

        DataFlowPrepareResponse response;
        if (protocol != null) {
            response = protocol.prepare(message); // may throw IllegalArgumentException
        } else {
            log.warn("No protocol registered; returning empty prepare response for processId={}", message.getProcessId());
            response = DataFlowPrepareResponse.Builder.newInstance()
                    .processId(message.getProcessId())
                    .build();
        }

        // Reuse the existing entity's MongoDB _id when re-preparing over a terminal record.
        // Using the incoming processId as the new _id would trigger an INSERT that hits the
        // unique index on processId (the old row — created by start() with a UUID-based _id —
        // still owns that processId value), causing a DuplicateKey error.
        String entityId = existingOpt.map(DataFlowEntity::getId).orElse(message.getProcessId());
        Instant now = Instant.now();
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id(entityId)
                .processId(message.getProcessId())
                .agreementId(message.getAgreementId())
                .datasetId(message.getDatasetId())
                .transferType(transferType)
                .callbackAddress(message.getCallbackAddress())
                .participantId(message.getParticipantId())
                .counterPartyId(message.getCounterPartyId())
                .state(DataFlowState.PREPARED)
                .dataAddress(response.getDataAddress())
                .createdAt(now)
                .updatedAt(now)
                .build();
        repository.save(entity);

        auditEventService.saveEvent(DataPlaneAuditEventType.DATAFLOW_PREPARE_REQUESTED,
                message.getProcessId(), transferType, "Data flow prepare requested", null);

        return response;
    }

    /**
     * Starts a data transfer using the appropriate protocol implementation.
     * Persists the entity with {@link DataFlowState#STARTING} before delegating to the
     * protocol, then transitions to {@link DataFlowState#STARTED} on successful initiation.
     *
     * <p>If an entity for this processId already exists in {@link DataFlowState#PREPARED} state,
     * it is reused and transitioned to STARTING. Any other pre-existing state causes an
     * {@link IllegalStateException}.</p>
     *
     * @param dataFlow the data flow request
     * @throws IllegalArgumentException if no protocol supports the transfer type
     * @throws IllegalStateException if a non-PREPARED flow for this processId already exists
     */
    public void start(DataFlow dataFlow) {
        Optional<DataFlowEntity> existingOpt = repository.findByProcessId(dataFlow.getProcessId());
        DataFlowEntity entity;
        if (existingOpt.isPresent()) {
            DataFlowEntity existing = existingOpt.get();
            if (existing.getState() != DataFlowState.PREPARED) {
                if (existing.getState() == DataFlowState.STARTED
                        || existing.getState() == DataFlowState.COMPLETED
                        || existing.getState() == DataFlowState.TERMINATED) {
                    throw new DataFlowConflictException("DataFlow with processId " + dataFlow.getProcessId()
                            + " already exists in lifecycle state " + existing.getState());
                }
                throw new IllegalStateException("DataFlow with processId " + dataFlow.getProcessId() + " already exists");
            }
            log.info("Reusing PREPARED DataFlow for processId={}", dataFlow.getProcessId());
            entity = existing.withState(DataFlowState.STARTING);
        } else {
            entity = toEntity(dataFlow, DataFlowState.STARTING);
        }

        DataTransferProtocol protocol = requiredProtocol(dataFlow.getTransferType());
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
                .resumeTransfer(processId)
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
            protocol.terminateTransfer(processId)
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
            protocol.suspendTransfer(processId)
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
