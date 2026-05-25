package it.eng.dataplane.core.service;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.repository.DataFlowRepository;
import it.eng.dataplane.core.service.DataPlaneAuditEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataFlowService}.
 */
@ExtendWith(MockitoExtension.class)
class DataFlowServiceTest {

    @Mock
    private DataFlowRepository repository;

    @Mock
    private DataTransferProtocolRegistry registry;

    @Mock
    private ControlPlaneClient controlPlaneClient;

    @Mock
    private DataPlaneAuditEventService auditEventService;

    @Mock
    private DataTransferProtocol protocol;

    @Mock
    private DataFlowStateMachine stateMachine;

    private DataFlowService service;

    @BeforeEach
    void setUp() {
        service = new DataFlowService(repository, registry, controlPlaneClient, auditEventService, stateMachine);
    }

    /**
     * Verifies that starting a data flow delegates to the correct protocol implementation
     * and saves STARTING then STARTED to the repository before the async transfer completes.
     */
    @Test
    void startDelegatesToProtocol() {
        // Given
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("test-process-123")
            .transferType("HttpData-PULL")
            .build();

        when(repository.findByProcessId("test-process-123")).thenReturn(Optional.empty());
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        
        // Return an incomplete future to prevent completion callback from running
        CompletableFuture<DataFlowResult> incompleteFuture = new CompletableFuture<>();
        when(protocol.initiateTransfer(any(DataFlow.class))).thenReturn(incompleteFuture);

        // When
        service.start(dataFlow);

        // Then: protocol must be called
        verify(protocol, times(1)).initiateTransfer(dataFlow);

        // Then: exactly two synchronous saves — STARTING first, STARTED second
        ArgumentCaptor<DataFlowEntity> entityCaptor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository, times(2)).save(entityCaptor.capture());

        DataFlowEntity firstSave = entityCaptor.getAllValues().get(0);
        assertEquals("test-process-123", firstSave.getProcessId());
        assertEquals("HttpData-PULL", firstSave.getTransferType());
        assertEquals(DataFlowState.STARTING, firstSave.getState());

        DataFlowEntity secondSave = entityCaptor.getAllValues().get(1);
        assertEquals("test-process-123", secondSave.getProcessId());
        assertEquals(DataFlowState.STARTED, secondSave.getState());
    }

    /**
     * Verifies that attempting to start a data flow with an unsupported transfer type
     * throws an IllegalArgumentException with the transfer type in the message.
     */
    @Test
    void startThrowsWhenProtocolNotFound() {
        // Given
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("test-process-456")
            .transferType("Unknown-Protocol")
            .build();

        when(repository.findByProcessId("test-process-456")).thenReturn(Optional.empty());
        when(registry.getProtocol("Unknown-Protocol")).thenReturn(null);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> service.start(dataFlow));
        
        assertTrue(exception.getMessage().contains("Unknown-Protocol"));
        assertTrue(exception.getMessage().contains("No protocol registered for transferType"));
        
        verify(repository, never()).save(any(DataFlowEntity.class));
        verify(protocol, never()).initiateTransfer(any(DataFlow.class));
    }

    /**
     * Verifies that attempting to start a data flow with a duplicate processId
     * throws an IllegalStateException.
     */
    @Test
    void startThrowsOnDuplicateProcessId() {
        // Given
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("duplicate-process")
            .transferType("HttpData-PULL")
            .build();

        DataFlowEntity existingEntity = DataFlowEntity.Builder.newInstance()
                .processId("duplicate-process")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("duplicate-process")).thenReturn(Optional.of(existingEntity));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> service.start(dataFlow));
        
        assertTrue(exception.getMessage().contains("duplicate-process"));
        assertTrue(exception.getMessage().contains("already exists"));
        
        verify(registry, never()).getProtocol(anyString());
        verify(repository, never()).save(any(DataFlowEntity.class));
    }

    /**
     * Verifies that start() persists the entity with STARTING state before STARTED,
     * ensuring the transition INITIALIZED → STARTING is recorded as the very first save.
     */
    @Test
    void startPersistsStartingBeforeAsyncCompletion() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-1")
                .transferType("HttpData-PULL")
                .build();

        when(repository.findByProcessId("tp-1")).thenReturn(Optional.empty());
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class))).thenReturn(new CompletableFuture<>());

        service.start(dataFlow);

        ArgumentCaptor<DataFlowEntity> entityCaptor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository, times(2)).save(entityCaptor.capture());
        // The first (index 0) save must be STARTING
        assertEquals(DataFlowState.STARTING, entityCaptor.getAllValues().get(0).getState());
    }

    /**
     * Verifies that start() persists STARTED as the second synchronous save, before the
     * long-running transfer future has completed, so status polling can observe STARTED
     * during transfer execution.
     */
    @Test
    void startPersistsStartedBeforeTransferCompletion() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-started")
                .transferType("HttpData-PULL")
                .build();

        when(repository.findByProcessId("tp-started")).thenReturn(Optional.empty());
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        // Never complete the future — transfer is "in flight"
        when(protocol.initiateTransfer(any(DataFlow.class))).thenReturn(new CompletableFuture<>());

        service.start(dataFlow);

        ArgumentCaptor<DataFlowEntity> entityCaptor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository, times(2)).save(entityCaptor.capture());
        // The second (index 1) save must be STARTED, before any completion arrives
        assertEquals(DataFlowState.STARTED, entityCaptor.getAllValues().get(1).getState());
    }

    /**
     * Verifies that a successful transfer result transitions the entity from STARTED to COMPLETED.
     */
    @Test
    void startCompletedAfterSuccessfulTransfer() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-complete")
                .transferType("HttpData-PULL")
                .build();

        when(repository.findByProcessId("tp-complete")).thenReturn(Optional.empty());
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class)))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));

        service.start(dataFlow);

        // STARTING + STARTED + COMPLETED = 3 saves
        ArgumentCaptor<DataFlowEntity> entityCaptor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository, times(3)).save(entityCaptor.capture());
        assertEquals(DataFlowState.STARTING,  entityCaptor.getAllValues().get(0).getState());
        assertEquals(DataFlowState.STARTED,   entityCaptor.getAllValues().get(1).getState());
        assertEquals(DataFlowState.COMPLETED, entityCaptor.getAllValues().get(2).getState());
    }

    /**
     * Verifies that a failed transfer future transitions the entity to TERMINATED.
     */
    @Test
    void startTerminatedAfterTransferError() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-error")
                .transferType("HttpData-PULL")
                .build();

        when(repository.findByProcessId("tp-error")).thenReturn(Optional.empty());
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("transfer-boom")));

        service.start(dataFlow);

        // STARTING + STARTED + TERMINATED = 3 saves
        ArgumentCaptor<DataFlowEntity> entityCaptor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository, times(3)).save(entityCaptor.capture());
        assertEquals(DataFlowState.STARTING,    entityCaptor.getAllValues().get(0).getState());
        assertEquals(DataFlowState.STARTED,     entityCaptor.getAllValues().get(1).getState());
        assertEquals(DataFlowState.TERMINATED,  entityCaptor.getAllValues().get(2).getState());
    }

    /**
     * Verifies that resume() moves a SUSPENDED flow back to STARTED after successful protocol resumption,
     * and that the state machine transition is validated before invoking the protocol.
     */
    @Test
    void resumeMovesSuspendedFlowBackToStarted() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-1")
                .processId("tp-1")
                .transferType("HttpData-PULL")
                .state(DataFlowState.SUSPENDED)
                .build();

        when(repository.findByProcessId("tp-1")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.resumeTransfer("df-1")).thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));

        service.resume("tp-1");

        verify(stateMachine).assertTransition(DataFlowState.SUSPENDED, DataFlowState.STARTED);
        verify(repository, atLeastOnce()).save(argThat(saved -> saved.getState() == DataFlowState.STARTED));
    }

    /**
     * Verifies that a failed result from resumeTransfer does not produce a false STARTED state.
     * The flow must transition to TERMINATED via the error path instead.
     */
    @Test
    void resumeFailedResultDoesNotMoveToStarted() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-2")
                .processId("tp-2")
                .transferType("HttpData-PULL")
                .state(DataFlowState.SUSPENDED)
                .build();

        when(repository.findByProcessId("tp-2")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.resumeTransfer("df-2"))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.failure("resume-error")));

        service.resume("tp-2");

        verify(repository, never()).save(argThat(saved -> saved.getState() == DataFlowState.STARTED));
        verify(repository, atLeastOnce()).save(argThat(saved -> saved.getState() == DataFlowState.TERMINATED));
    }

    /**
     * Verifies that terminate() invokes the protocol with the entity's internal ID, not the process ID.
     */
    @Test
    void terminateUsesEntityIdForProtocolCall() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-3")
                .processId("tp-3")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-3")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.terminateTransfer("df-3"))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));

        service.terminate("tp-3");

        verify(protocol).terminateTransfer("df-3");
        verify(protocol, never()).terminateTransfer("tp-3");
    }

    /**
     * Verifies that terminate() validates the state machine transition before acting.
     */
    @Test
    void terminateValidatesStateMachineTransition() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-4")
                .processId("tp-4")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-4")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.terminateTransfer(any())).thenReturn(new CompletableFuture<>());

        service.terminate("tp-4");

        verify(stateMachine).assertTransition(DataFlowState.STARTED, DataFlowState.TERMINATED);
    }

    /**
     * Verifies that suspend() validates the state machine transition before acting.
     */
    @Test
    void suspendValidatesStateMachineTransition() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-5")
                .processId("tp-5")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-5")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.suspendTransfer(any())).thenReturn(new CompletableFuture<>());

        service.suspend("tp-5");

        verify(stateMachine).assertTransition(DataFlowState.STARTED, DataFlowState.SUSPENDED);
    }

    /**
     * Verifies that the DataFlowState enum contains exactly the canonical DPS state set,
     * with no extra or missing values.
     */
    @Test
    void dataFlowStateEnumMatchesCanonicalDpsSet() {
        assertEquals(
                Set.of(
                        DataFlowState.INITIALIZED,
                        DataFlowState.PREPARING,
                        DataFlowState.PREPARED,
                        DataFlowState.STARTING,
                        DataFlowState.STARTED,
                        DataFlowState.SUSPENDED,
                        DataFlowState.COMPLETED,
                        DataFlowState.TERMINATED),
                EnumSet.allOf(DataFlowState.class));
    }
}
