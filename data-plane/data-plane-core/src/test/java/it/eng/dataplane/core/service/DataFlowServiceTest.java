package it.eng.dataplane.core.service;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.model.DataPlaneAuditEventType;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.repository.DataFlowRepository;
import it.eng.dataplane.core.service.DataPlaneAuditEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private DataPlaneAuditEventService auditEventService;

    @Mock
    private DataTransferProtocol protocol;

    @Mock
    private DataFlowStateMachine stateMachine;

    @Mock
    private DataFlowCheckpointService checkpointService;

    @Mock
    private DataFlowExecutionRegistry executionRegistry;

    private DataFlowService service;

    @BeforeEach
    void setUp() {
        service = new DataFlowService(repository, registry, auditEventService, stateMachine, checkpointService, executionRegistry);
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

        DataFlowEntity startedEntity = DataFlowEntity.Builder.newInstance()
                .processId("tp-complete")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        // First call: duplicate check; second call: reload inside handleCompletion
        when(repository.findByProcessId("tp-complete"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(startedEntity));
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

        DataFlowEntity startedEntity = DataFlowEntity.Builder.newInstance()
                .processId("tp-error")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        // First call: duplicate check; second call: reload inside handleError
        when(repository.findByProcessId("tp-error"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(startedEntity));
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
        DataFlowEntity suspendedEntity = DataFlowEntity.Builder.newInstance()
                .id("df-1")
                .processId("tp-1")
                .transferType("HttpData-PULL")
                .state(DataFlowState.SUSPENDED)
                .build();

        DataFlowEntity startedEntity = suspendedEntity.withState(DataFlowState.STARTED);

        when(repository.findByProcessId("tp-1"))
                .thenReturn(Optional.of(suspendedEntity))
                .thenReturn(Optional.of(startedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.resumeTransfer("df-1")).thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));

        service.resume("tp-1");

        verify(stateMachine, atLeastOnce()).assertTransition(DataFlowState.SUSPENDED, DataFlowState.STARTED);
        verify(repository, atLeastOnce()).save(argThat(saved -> saved.getState() == DataFlowState.STARTED));
        verify(auditEventService).saveEvent(DataPlaneAuditEventType.DATAFLOW_RESUMED,
                "tp-1", "HttpData-PULL", "Data flow resumed", null);
    }

    /**
     * Verifies that a failed result from resumeTransfer does not produce a false STARTED state.
     * The flow must transition to TERMINATED via the error path instead.
     */
    @Test
    void resumeFailedResultDoesNotMoveToStarted() {
        DataFlowEntity suspendedEntity = DataFlowEntity.Builder.newInstance()
                .id("df-2")
                .processId("tp-2")
                .transferType("HttpData-PULL")
                .state(DataFlowState.SUSPENDED)
                .build();

        DataFlowEntity startedEntity = suspendedEntity.withState(DataFlowState.STARTED);

        when(repository.findByProcessId("tp-2"))
                .thenReturn(Optional.of(suspendedEntity))
                .thenReturn(Optional.of(startedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.resumeTransfer("df-2"))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.failure("resume-error")));

        service.resume("tp-2");

        verify(repository).save(argThat(saved -> saved.getState() == DataFlowState.STARTED));
        verify(repository).save(argThat(saved -> saved.getState() == DataFlowState.TERMINATED));
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

    /**
     * Verifies that the async completion callback reloads a fresh entity from the repository
     * rather than operating on the stale captured instance, so a concurrent state change
     * (e.g. the entity is already TERMINATED) prevents a lost-update overwrite.
     */
    @Test
    void asyncCompletionUsesReloadedEntityAndValidatesTransition() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-stale")
                .transferType("HttpData-PULL")
                .build();

        // Simulate: by the time the completion fires the entity was already TERMINATED
        DataFlowEntity terminatedEntity = DataFlowEntity.Builder.newInstance()
                .processId("tp-stale")
                .transferType("HttpData-PULL")
                .state(DataFlowState.TERMINATED)
                .build();

        // First call: duplicate check; second+ call: reload inside handleCompletion / handleError
        when(repository.findByProcessId("tp-stale"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(terminatedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class)))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));
        lenient().doThrow(new IllegalStateException("TERMINATED -> COMPLETED not allowed"))
                .when(stateMachine).assertTransition(DataFlowState.TERMINATED, DataFlowState.COMPLETED);
        // handleError fallback also hits assertTransition(TERMINATED, TERMINATED)
        lenient().doThrow(new IllegalStateException("TERMINATED -> TERMINATED not allowed"))
                .when(stateMachine).assertTransition(DataFlowState.TERMINATED, DataFlowState.TERMINATED);

        // Must not propagate any exception to the caller
        assertDoesNotThrow(() -> service.start(dataFlow));

        // COMPLETED must never be persisted — stale overwrite is prevented
        verify(repository, never()).save(argThat(saved -> saved.getState() == DataFlowState.COMPLETED));
        verify(checkpointService, never()).deleteByProcessId("tp-stale");
    }

    /**
     * Verifies that terminate() does not emit the audit event until after the async protocol
     * callback has completed and the state has been persisted.
     * When the future is never completed, no audit event must be recorded.
     */
    @Test
    void terminateAuditLoggedOnlyAfterAsyncStatePersistedWhenProtocolPresent() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-audit")
                .processId("tp-audit")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-audit")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        // Future that never completes — simulates an in-flight protocol call
        when(protocol.terminateTransfer(any())).thenReturn(new CompletableFuture<>());

        service.terminate("tp-audit");

        // No audit event must have been emitted before the callback fires
        verify(auditEventService, never()).saveEvent(
                eq(DataPlaneAuditEventType.DATAFLOW_TERMINATED), any(), any(), any(), any());
    }

    /**
     * Verifies that suspend() does not emit the audit event until after the async protocol
     * callback has completed and the state has been persisted.
     */
    @Test
    void suspendAuditLoggedOnlyAfterAsyncStatePersistedWhenProtocolPresent() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-suspend-audit")
                .processId("tp-suspend-audit")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-suspend-audit")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.suspendTransfer(any())).thenReturn(new CompletableFuture<>());

        service.suspend("tp-suspend-audit");

        verify(auditEventService, never()).saveEvent(
                eq(DataPlaneAuditEventType.DATAFLOW_SUSPENDED), any(), any(), any(), any());
    }

    /**
     * Verifies that start() registers an execution handle in the registry for the running transfer.
     */
    @Test
    void startRegistersExecutionHandleInRegistry() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-reg-start")
                .transferType("HttpData-PULL")
                .build();

        when(repository.findByProcessId("tp-reg-start")).thenReturn(Optional.empty());
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class))).thenReturn(new CompletableFuture<>());

        service.start(dataFlow);

        verify(executionRegistry).register(eq("tp-reg-start"), any(DataFlowExecutionHandle.class));
    }

    /**
     * Verifies that a successful transfer completion removes the execution handle from the registry
     * and deletes the checkpoint.
     */
    @Test
    void completionRemovesHandleAndDeletesCheckpoint() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-reg-complete")
                .transferType("HttpData-PULL")
                .build();

        DataFlowEntity startedEntity = DataFlowEntity.Builder.newInstance()
                .processId("tp-reg-complete")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-reg-complete"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(startedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class)))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));

        service.start(dataFlow);

        verify(executionRegistry).remove("tp-reg-complete");
        verify(checkpointService).deleteByProcessId("tp-reg-complete");
    }

    /**
     * Verifies that a failed transfer removes the execution handle from the registry
     * and deletes the checkpoint.
     */
    @Test
    void errorRemovesHandleAndDeletesCheckpoint() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-reg-error")
                .transferType("HttpData-PULL")
                .build();

        DataFlowEntity startedEntity = DataFlowEntity.Builder.newInstance()
                .processId("tp-reg-error")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-reg-error"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(startedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("transfer-failed")));

        service.start(dataFlow);

        verify(executionRegistry).remove("tp-reg-error");
        verify(checkpointService).deleteByProcessId("tp-reg-error");
    }

    /**
     * Verifies that resume() registers an execution handle in the registry for the resumed transfer.
     */
    @Test
    void resumeRegistersExecutionHandleInRegistry() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-reg-resume")
                .processId("tp-reg-resume")
                .transferType("HttpData-PULL")
                .state(DataFlowState.SUSPENDED)
                .build();

        when(repository.findByProcessId("tp-reg-resume")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.resumeTransfer("df-reg-resume")).thenReturn(new CompletableFuture<>());

        service.resume("tp-reg-resume");

        verify(executionRegistry).register(eq("tp-reg-resume"), any(DataFlowExecutionHandle.class));
    }

    /**
     * Verifies that resume() persists STARTED before launching the protocol and therefore
     * blocks a second concurrent resume attempt from launching another transfer future.
     */
    @Test
    void resumePreSaveBlocksDuplicateResume() {
        DataFlowEntity suspendedEntity = DataFlowEntity.Builder.newInstance()
                .id("df-resume-duplicate")
                .processId("tp-resume-duplicate")
                .transferType("HttpData-PULL")
                .state(DataFlowState.SUSPENDED)
                .build();

        DataFlowEntity startedEntity = suspendedEntity.withState(DataFlowState.STARTED);

        when(repository.findByProcessId("tp-resume-duplicate"))
                .thenReturn(Optional.of(suspendedEntity))
                .thenReturn(Optional.of(startedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.resumeTransfer("df-resume-duplicate")).thenReturn(new CompletableFuture<>());
        lenient().doThrow(new IllegalStateException("STARTED -> STARTED not allowed"))
                .when(stateMachine).assertTransition(DataFlowState.STARTED, DataFlowState.STARTED);

        service.resume("tp-resume-duplicate");

        assertThrows(IllegalStateException.class, () -> service.resume("tp-resume-duplicate"));
        verify(repository).save(argThat(saved -> saved.getState() == DataFlowState.STARTED));
        verify(protocol, times(1)).resumeTransfer("df-resume-duplicate");
    }

    /**
     * Verifies that terminate() removes the execution handle and deletes the checkpoint
     * after the protocol callback fires.
     */
    @Test
    void terminateRemovesHandleAndDeletesCheckpoint() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-reg-terminate")
                .processId("tp-reg-terminate")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        DataFlowEntity freshEntity = DataFlowEntity.Builder.newInstance()
                .id("df-reg-terminate")
                .processId("tp-reg-terminate")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-reg-terminate"))
                .thenReturn(Optional.of(entity))
                .thenReturn(Optional.of(freshEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.terminateTransfer("df-reg-terminate"))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));

        service.terminate("tp-reg-terminate");

        verify(executionRegistry).remove("tp-reg-terminate");
        verify(checkpointService).deleteByProcessId("tp-reg-terminate");
    }

    /**
     * Verifies that terminate() cancels and removes the active execution handle before
     * delegating to the protocol, so the old transfer future cannot race the termination.
     */
    @Test
    void terminateCancelsAndRemovesExecutionHandle() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-terminate-cancel")
                .processId("tp-terminate-cancel")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-terminate-cancel")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.terminateTransfer("df-terminate-cancel")).thenReturn(new CompletableFuture<>());

        DataFlowExecutionHandle handle = mock(DataFlowExecutionHandle.class);
        when(executionRegistry.find("tp-terminate-cancel")).thenReturn(Optional.of(handle));

        service.terminate("tp-terminate-cancel");

        InOrder inOrder = inOrder(handle, executionRegistry, protocol);
        inOrder.verify(handle).cancel();
        inOrder.verify(executionRegistry).remove("tp-terminate-cancel");
        inOrder.verify(protocol).terminateTransfer("df-terminate-cancel");
    }

    /**
     * Verifies that terminate() removes the handle and deletes the checkpoint immediately
     * when no protocol is registered.
     */
    @Test
    void terminateNoProtocolRemovesHandleAndDeletesCheckpoint() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-reg-terminate-noproto")
                .processId("tp-terminate-noproto")
                .transferType("Unknown-Protocol")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-terminate-noproto")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("Unknown-Protocol")).thenReturn(null);

        service.terminate("tp-terminate-noproto");

        verify(executionRegistry).remove("tp-terminate-noproto");
        verify(checkpointService).deleteByProcessId("tp-terminate-noproto");
    }

    /**
     * Verifies that suspend() cancels and removes the active execution handle from the registry
     * before delegating to the protocol. This prevents the old running future's callbacks from
     * flipping a SUSPENDED flow to COMPLETED or TERMINATED after suspend succeeds.
     */
    @Test
    void suspendCancelsAndRemovesExecutionHandle() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-suspend-cancel")
                .processId("tp-suspend-cancel")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-suspend-cancel")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.suspendTransfer(any())).thenReturn(new CompletableFuture<>());

        DataFlowExecutionHandle handle = mock(DataFlowExecutionHandle.class);
        when(executionRegistry.find("tp-suspend-cancel")).thenReturn(Optional.of(handle));

        service.suspend("tp-suspend-cancel");

        verify(handle).cancel();
        verify(executionRegistry).remove("tp-suspend-cancel");
    }

    /**
     * Verifies that a suspended flow is not silently completed when the old running future
     * fires its completion callback after the flow has been marked SUSPENDED. The stale
     * completion must be discarded: COMPLETED must never be persisted and the checkpoint
     * must not be deleted.
     */
    @Test
    void suspendedFlowNotCompletedByOldFuture() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-suspended-race")
                .transferType("HttpData-PULL")
                .build();

        CompletableFuture<DataFlowResult> future = new CompletableFuture<>();

        DataFlowEntity suspendedEntity = DataFlowEntity.Builder.newInstance()
                .processId("tp-suspended-race")
                .transferType("HttpData-PULL")
                .state(DataFlowState.SUSPENDED)
                .build();

        // First call: duplicate check; subsequent: entity is already SUSPENDED
        when(repository.findByProcessId("tp-suspended-race"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(suspendedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class))).thenReturn(future);

        service.start(dataFlow);

        // Simulate race: old future completes after the flow has been suspended
        future.complete(DataFlowResult.success());

        // COMPLETED must never be persisted and the checkpoint must remain intact
        verify(repository, never()).save(argThat(saved -> saved.getState() == DataFlowState.COMPLETED));
        verify(checkpointService, never()).deleteByProcessId("tp-suspended-race");
    }

    /**
     * Verifies that a CancellationException from the old future (caused by the suspend path
     * calling handle.cancel()) does not flip the suspended flow to TERMINATED or delete
     * the checkpoint. Cancellation is expected during suspend and must be silently ignored.
     */
    @Test
    void suspendedFlowNotTerminatedByCancellationException() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-cancel-race")
                .transferType("HttpData-PULL")
                .build();

        CompletableFuture<DataFlowResult> future = new CompletableFuture<>();

        when(repository.findByProcessId("tp-cancel-race")).thenReturn(Optional.empty());
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class))).thenReturn(future);

        service.start(dataFlow);

        // Simulate suspend cancelling the old future
        future.cancel(true);

        // TERMINATED must never be persisted and the checkpoint must remain intact
        verify(repository, never()).save(argThat(saved -> saved.getState() == DataFlowState.TERMINATED));
        verify(checkpointService, never()).deleteByProcessId("tp-cancel-race");
    }
}
