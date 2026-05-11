package it.eng.dataplane.core.service;

import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.repository.DataFlowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Core service for managing data flow lifecycle on the Data Plane.
 * Orchestrates protocol implementations and Control Plane status callbacks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataFlowService {

    private static final Executor VIRTUAL_THREAD_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    private final DataFlowRepository repository;
    private final DataTransferProtocolRegistry registry;
    private final ControlPlaneClient controlPlaneClient;

    /**
     * Starts a data transfer using the appropriate protocol implementation.
     *
     * @param dataFlow the data flow request
     * @throws IllegalArgumentException if no protocol supports the transfer type
     * @throws IllegalStateException if a flow for this processId already exists
     */
    public void start(DataFlow dataFlow) {
        repository.findByProcessId(dataFlow.getProcessId()).ifPresent(existing -> {
            throw new IllegalStateException("DataFlow with processId " + dataFlow.getProcessId() + " already exists");
        });

        DataTransferProtocol protocol = registry.getProtocol(dataFlow.getTransferType());
        if (protocol == null) {
            throw new IllegalArgumentException("No protocol registered for transferType: " + dataFlow.getTransferType());
        }

        DataFlowEntity entity = toEntity(dataFlow, DataFlowState.STARTED);
        repository.save(entity);

        protocol.initiateTransfer(dataFlow)
            .thenAccept(result -> handleCompletion(entity, result))
            .exceptionally(ex -> { handleError(entity, ex); return null; });
    }

    /**
     * Terminates an active data transfer.
     *
     * @param processId the process ID to terminate
     * @throws IllegalStateException if no flow exists for this processId
     */
    public void terminate(String processId) {
        DataFlowEntity entity = repository.findByProcessId(processId)
            .orElseThrow(() -> new IllegalStateException("No DataFlow found for processId: " + processId));

        DataTransferProtocol protocol = registry.getProtocol(entity.getTransferType());
        if (protocol != null) {
            protocol.terminateTransfer(entity.getProcessId())
                .thenAccept(result -> updateState(entity, DataFlowState.TERMINATED))
                .exceptionally(ex -> { handleError(entity, ex); return null; });
        } else {
            updateState(entity, DataFlowState.TERMINATED);
        }
    }

    /**
     * Suspends an active data transfer.
     *
     * @param processId the process ID to suspend
     * @throws IllegalStateException if no flow exists for this processId
     */
    public void suspend(String processId) {
        DataFlowEntity entity = repository.findByProcessId(processId)
            .orElseThrow(() -> new IllegalStateException("No DataFlow found for processId: " + processId));

        DataTransferProtocol protocol = registry.getProtocol(entity.getTransferType());
        if (protocol != null) {
            protocol.suspendTransfer(entity.getId())
                .thenAccept(result -> updateState(entity, DataFlowState.SUSPENDED))
                .exceptionally(ex -> { handleError(entity, ex); return null; });
        } else {
            updateState(entity, DataFlowState.SUSPENDED);
        }
    }

    private void handleCompletion(DataFlowEntity entity, DataFlowResult result) {
        if (result.isSuccess()) {
            updateState(entity, DataFlowState.COMPLETED);
            CompletableFuture.runAsync(() ->
                controlPlaneClient.sendStatus(entity.getCallbackAddress(), entity.getProcessId(),
                    DataFlowState.COMPLETED, null, null),
                VIRTUAL_THREAD_EXECUTOR);
        } else {
            handleError(entity, new RuntimeException(result.getErrorMessage()));
        }
    }

    private void handleError(DataFlowEntity entity, Throwable ex) {
        log.error("DataFlow {} failed: {}", entity.getId(), ex.getMessage(), ex);
        try {
            entity.setErrorMessage(ex.getMessage());
            updateState(entity, DataFlowState.TERMINATED);
        } catch (Exception saveEx) {
            log.error("Failed to persist TERMINATED state for DataFlow {}: {}", entity.getId(), saveEx.getMessage());
        }
        CompletableFuture.runAsync(() ->
            controlPlaneClient.sendStatus(entity.getCallbackAddress(), entity.getProcessId(),
                DataFlowState.TERMINATED, null, ex.getMessage()),
            VIRTUAL_THREAD_EXECUTOR);
    }

    private void updateState(DataFlowEntity entity, DataFlowState state) {
        entity.setState(state);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    private DataFlowEntity toEntity(DataFlow dataFlow, DataFlowState state) {
        DataFlowEntity entity = new DataFlowEntity();
        entity.setId(dataFlow.getDataFlowId() != null ? dataFlow.getDataFlowId() : UUID.randomUUID().toString());
        entity.setProcessId(dataFlow.getProcessId());
        entity.setAgreementId(dataFlow.getAgreementId());
        entity.setDatasetId(dataFlow.getDatasetId());
        entity.setTransferType(dataFlow.getTransferType());
        entity.setCallbackAddress(dataFlow.getCallbackAddress());
        entity.setState(state);
        entity.setDataAddress(dataFlow.getDataAddress());
        entity.setTenantId(dataFlow.getTenantId());
        entity.setParticipantId(dataFlow.getParticipantId());
        entity.setCounterPartyId(dataFlow.getCounterPartyId());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
