package it.eng.dataplane.core.service;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.model.DataPlaneAuditEventType;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.repository.DataFlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private DataFlowService service;

    @BeforeEach
    void setUp() {
        service = new DataFlowService(repository, registry, auditEventService, stateMachine);
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

    @Test
    @DisplayName("start throws DataFlowConflictException when flow is in STARTED state (lifecycle conflict, not idempotent retry)")
    void startThrowsDataFlowConflictExceptionForStartedState() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("proc-started")
                .transferType("HttpData-PULL")
                .build();
        DataFlowEntity existing = DataFlowEntity.Builder.newInstance()
                .processId("proc-started")
                .state(DataFlowState.STARTED)
                .build();
        when(repository.findByProcessId("proc-started")).thenReturn(Optional.of(existing));

        assertThrows(DataFlowConflictException.class, () -> service.start(dataFlow));
    }

    @Test
    @DisplayName("start throws DataFlowConflictException when flow is in COMPLETED state (lifecycle conflict)")
    void startThrowsDataFlowConflictExceptionForCompletedState() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("proc-completed")
                .transferType("HttpData-PULL")
                .build();
        DataFlowEntity existing = DataFlowEntity.Builder.newInstance()
                .processId("proc-completed")
                .state(DataFlowState.COMPLETED)
                .build();
        when(repository.findByProcessId("proc-completed")).thenReturn(Optional.of(existing));

        assertThrows(DataFlowConflictException.class, () -> service.start(dataFlow));
    }

    @Test
    @DisplayName("start throws DataFlowConflictException when flow is in TERMINATED state (lifecycle conflict)")
    void startThrowsDataFlowConflictExceptionForTerminatedState() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("proc-terminated")
                .transferType("HttpData-PULL")
                .build();
        DataFlowEntity existing = DataFlowEntity.Builder.newInstance()
                .processId("proc-terminated")
                .state(DataFlowState.TERMINATED)
                .build();
        when(repository.findByProcessId("proc-terminated")).thenReturn(Optional.of(existing));

        assertThrows(DataFlowConflictException.class, () -> service.start(dataFlow));
    }

    @Test
    @DisplayName("start throws plain IllegalStateException (not DataFlowConflictException) for STARTING state (idempotent in-flight retry)")
    void startThrowsPlainIllegalStateExceptionForStartingState() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("proc-starting")
                .transferType("HttpData-PULL")
                .build();
        DataFlowEntity existing = DataFlowEntity.Builder.newInstance()
                .processId("proc-starting")
                .state(DataFlowState.STARTING)
                .build();
        when(repository.findByProcessId("proc-starting")).thenReturn(Optional.of(existing));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.start(dataFlow));
        assertFalse(ex instanceof DataFlowConflictException,
                "STARTING state represents an in-flight duplicate, not a lifecycle conflict — must not throw DataFlowConflictException");
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
        when(protocol.completeTransfer("tp-complete"))
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
     * Verifies that start() persists structured metadata from the incoming DataFlow.
     */
    @Test
    @DisplayName("start() persists metadata from the start message")
    void startPersistsMetadataFromStartMessage() {
        Map<String, Object> metadata = Map.of(
                DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                        DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket")));
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-runtime")
                .transferType("HttpData-PUSH")
                .metadata(metadata)
                .build();

        when(repository.findByProcessId("tp-runtime")).thenReturn(Optional.empty());
        when(registry.getProtocol("HttpData-PUSH")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class))).thenReturn(new CompletableFuture<>());

        service.start(dataFlow);

        ArgumentCaptor<DataFlowEntity> entityCaptor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository, times(2)).save(entityCaptor.capture());
        assertEquals(metadata, entityCaptor.getAllValues().get(0).getMetadata());
        assertEquals(metadata, entityCaptor.getAllValues().get(1).getMetadata());
    }

    /**
     * Verifies that completion cleanup is invoked before the data flow is persisted as COMPLETED.
     */
    @Test
    @DisplayName("handleCompletion() invokes protocol cleanup before persisting COMPLETED")
    void handleCompletionInvokesProtocolCleanupBeforeCompletedState() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-runtime")
                .transferType("HttpData-PULL")
                .build();

        DataFlowEntity startedEntity = DataFlowEntity.Builder.newInstance()
                .id("df-runtime")
                .processId("tp-runtime")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-runtime"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(startedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.initiateTransfer(any(DataFlow.class)))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));
        when(protocol.completeTransfer("tp-runtime"))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));

        service.start(dataFlow);

        InOrder inOrder = inOrder(protocol, repository);
        inOrder.verify(protocol).initiateTransfer(dataFlow);
        inOrder.verify(protocol).completeTransfer("tp-runtime");
        inOrder.verify(repository).save(argThat((DataFlowEntity entity) -> entity.getState() == DataFlowState.COMPLETED));
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
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-1")
                .processId("tp-1")
                .transferType("HttpData-PULL")
                .state(DataFlowState.SUSPENDED)
                .build();

        when(repository.findByProcessId("tp-1")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.resumeTransfer("tp-1")).thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));

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
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-2")
                .processId("tp-2")
                .transferType("HttpData-PULL")
                .state(DataFlowState.SUSPENDED)
                .build();

        when(repository.findByProcessId("tp-2")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.resumeTransfer("tp-2"))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.failure("resume-error")));

        service.resume("tp-2");

        verify(repository, never()).save(argThat(saved -> saved.getState() == DataFlowState.STARTED));
        verify(repository, atLeastOnce()).save(argThat(saved -> saved.getState() == DataFlowState.TERMINATED));
    }

    /**
     * Verifies that terminate() invokes the protocol with the process ID, not the internal entity ID.
     * The processId is the stable external identifier shared across CP and DP.
     */
    @Test
    @DisplayName("terminate() uses processId for protocol call, not entity id")
    void terminateUsesProcessIdForProtocolCall() {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-3")
                .processId("tp-3")
                .transferType("HttpData-PULL")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("tp-3")).thenReturn(Optional.of(entity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.terminateTransfer("tp-3"))
                .thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));

        service.terminate("tp-3");

        verify(protocol).terminateTransfer("tp-3");
        verify(protocol, never()).terminateTransfer("df-3");
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
        verify(protocol).suspendTransfer("tp-5");
        verify(protocol, never()).suspendTransfer("df-5");
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
        when(protocol.completeTransfer("tp-stale"))
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
     * Verifies that prepare() persists a {@link DataFlowState#PREPARED} entity keyed by processId,
     * with the correct transferType, before returning the protocol response.
     */
    @Test
    @DisplayName("prepare() persists PREPARED entity keyed by processId")
    void preparePersistedAsPreparedState() {
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("prepare-proc-1")
                .transferType("HttpData-PUSH")
                .agreementId("agree-prepare-1")
                .datasetId("dataset-prepare-1")
                .callbackAddress("http://cp/callback")
                .build();
        when(repository.findByProcessId("prepare-proc-1")).thenReturn(Optional.empty());
        when(registry.getProtocol("HttpData-PUSH")).thenReturn(protocol);
        when(protocol.prepare(message)).thenReturn(
                DataFlowPrepareResponse.Builder.newInstance().processId("prepare-proc-1").build());

        service.prepare(message);

        ArgumentCaptor<DataFlowEntity> entityCaptor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository).save(entityCaptor.capture());
        DataFlowEntity saved = entityCaptor.getValue();
        assertEquals("prepare-proc-1", saved.getId());
        assertEquals("prepare-proc-1", saved.getProcessId());
        assertEquals(DataFlowState.PREPARED, saved.getState());
        assertEquals("HttpData-PUSH", saved.getTransferType());
    }

    /**
     * Verifies that a repeated prepare() for an already PREPARED flow reuses the persisted
     * response instead of invoking the protocol again or overwriting the document.
     */
    @Test
    void prepareReusesExistingPreparedFlow() {
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("prepare-proc-2")
                .transferType("HttpData-PUSH")
                .agreementId("agree-prepare-2")
                .datasetId("dataset-prepare-2")
                .callbackAddress("http://cp/callback")
                .build();
        DataFlowEntity existing = DataFlowEntity.Builder.newInstance()
                .id("prepare-proc-2")
                .processId("prepare-proc-2")
                .transferType("HttpData-PUSH")
                .state(DataFlowState.PREPARED)
                .dataAddress(Map.of("bucketName", "prepared-bucket"))
                .build();

        when(repository.findByProcessId("prepare-proc-2")).thenReturn(Optional.of(existing));

        DataFlowPrepareResponse response = service.prepare(message);

        assertEquals("prepare-proc-2", response.getProcessId());
        assertEquals("prepared-bucket", response.getDataAddress().get("bucketName"));
        verify(protocol, never()).prepare(any(DataFlowPrepareMessage.class));
        verify(repository, never()).save(any(DataFlowEntity.class));
    }

    /**
     * Verifies that prepare() rejects a retry for a non-PREPARED flow instead of replacing
     * an already active persisted entity with a new PREPARED document.
     */
    @Test
    void prepareRejectsExistingStartedFlow() {
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("prepare-proc-3")
                .transferType("HttpData-PUSH")
                .agreementId("agree-prepare-3")
                .datasetId("dataset-prepare-3")
                .callbackAddress("http://cp/callback")
                .build();
        DataFlowEntity existing = DataFlowEntity.Builder.newInstance()
                .id("prepare-proc-3")
                .processId("prepare-proc-3")
                .transferType("HttpData-PUSH")
                .state(DataFlowState.STARTED)
                .build();

        when(repository.findByProcessId("prepare-proc-3")).thenReturn(Optional.of(existing));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.prepare(message));

        assertTrue(exception.getMessage().contains("prepare-proc-3"));
        assertTrue(exception.getMessage().contains("STARTED"));
        verify(protocol, never()).prepare(any(DataFlowPrepareMessage.class));
        verify(repository, never()).save(any(DataFlowEntity.class));
    }

    /**
     * Verifies that prepare() allows a fresh prepare when the existing entity is in TERMINATED state.
     * This covers HTTP-PULL retry after rollback/cleanup left a terminal DP record for the same processId.
     */
    @Test
    @DisplayName("prepare() allows fresh prepare when existing entity is TERMINATED (HTTP-PULL retry scenario)")
    void prepareAllowsFreshPrepareAfterTerminatedState() {
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("prepare-retry-tp")
                .transferType("HttpData-PULL")
                .agreementId("agree-retry-1")
                .datasetId("dataset-retry-1")
                .callbackAddress("http://cp/callback")
                .build();
        DataFlowEntity terminatedEntity = DataFlowEntity.Builder.newInstance()
                .id("prepare-retry-tp")
                .processId("prepare-retry-tp")
                .transferType("HttpData-PULL")
                .state(DataFlowState.TERMINATED)
                .build();

        when(repository.findByProcessId("prepare-retry-tp")).thenReturn(Optional.of(terminatedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.prepare(message)).thenReturn(
                DataFlowPrepareResponse.Builder.newInstance().processId("prepare-retry-tp").build());

        // Must not throw even though a TERMINATED entity exists for the same processId
        assertDoesNotThrow(() -> service.prepare(message));

        verify(protocol).prepare(message);
        verify(repository).save(argThat(saved ->
                "prepare-retry-tp".equals(saved.getProcessId()) && saved.getState() == DataFlowState.PREPARED));
    }

    /**
     * Verifies that prepare() allows a fresh prepare when the existing entity is in COMPLETED state.
     * This covers HTTP-PULL VIEW: after the consumer DP completes the download, viewData() calls
     * prepare() again with the same transfer ID to generate a presigned URL.
     */
    @Test
    @DisplayName("prepare() allows fresh prepare when existing entity is COMPLETED (HTTP-PULL VIEW scenario)")
    void prepareAllowsFreshPrepareAfterCompletedState() {
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("prepare-view-tp")
                .transferType("HttpData-PULL")
                .agreementId("agree-view-1")
                .datasetId("dataset-view-1")
                .callbackAddress("http://cp/callback")
                .build();
        DataFlowEntity completedEntity = DataFlowEntity.Builder.newInstance()
                .id("prepare-view-tp")
                .processId("prepare-view-tp")
                .transferType("HttpData-PULL")
                .state(DataFlowState.COMPLETED)
                .build();

        when(repository.findByProcessId("prepare-view-tp")).thenReturn(Optional.of(completedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.prepare(message)).thenReturn(
                DataFlowPrepareResponse.Builder.newInstance().processId("prepare-view-tp").build());

        assertDoesNotThrow(() -> service.prepare(message));

        verify(protocol).prepare(message);
        verify(repository).save(argThat(saved ->
                "prepare-view-tp".equals(saved.getProcessId()) && saved.getState() == DataFlowState.PREPARED));
    }

    /**
     * Verifies that start() allows a PREPARED flow to transition to STARTING instead of throwing
     * an {@link IllegalStateException}. PREPARED is a valid predecessor state per the DPS state machine.
     */
    @Test
    @DisplayName("start() reuses a PREPARED flow (PREPARED -> STARTING) instead of rejecting it")
    void startReusesPreparedFlow() {
        DataFlowEntity preparedEntity = DataFlowEntity.Builder.newInstance()
                .id("df-prepared-1")
                .processId("prepared-tp")
                .transferType("HttpData-PUSH")
                .state(DataFlowState.PREPARED)
                .build();
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("prepared-tp")
                .transferType("HttpData-PUSH")
                .build();

        when(repository.findByProcessId("prepared-tp")).thenReturn(Optional.of(preparedEntity));
        when(registry.getProtocol("HttpData-PUSH")).thenReturn(protocol);
        when(protocol.initiateTransfer(any())).thenReturn(new CompletableFuture<>());

        // Must not throw even though an entity with this processId already exists
        assertDoesNotThrow(() -> service.start(dataFlow));
        verify(protocol).initiateTransfer(dataFlow);
        verify(repository, atLeastOnce()).save(argThat(saved ->
                "df-prepared-1".equals(saved.getId())
                        && "prepared-tp".equals(saved.getProcessId())
                        && DataFlowState.STARTING == saved.getState()));
    }

    /**
     * Issue 1: VIEW mode prepare should bypass the PREPARED entity cache.
     * When a PREPARED entity already exists for the processId and the incoming prepare message
     * carries VIEW mode ({@code sink.mode = "VIEW"}), a fresh presigned URL must be generated
     * instead of returning the cached (stale) data address.
     */
    @Test
    @DisplayName("prepare() bypasses PREPARED cache for VIEW mode — generates fresh presigned URL")
    void prepareViewModeBypassesPreparedCache() {
        Map<String, Object> sinkMeta = Map.of(DataPlaneConstants.METADATA_FIELD_MODE, "VIEW");
        Map<String, Object> metadata = Map.of(DataPlaneConstants.METADATA_SECTION_SINK, sinkMeta);
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("view-cached-proc")
                .transferType("HttpData-PULL")
                .agreementId("agree-view-cached")
                .datasetId("dataset-view-cached")
                .callbackAddress("http://cp/callback")
                .metadata(metadata)
                .build();
        DataFlowEntity preparedEntity = DataFlowEntity.Builder.newInstance()
                .id("view-cached-proc")
                .processId("view-cached-proc")
                .transferType("HttpData-PULL")
                .state(DataFlowState.PREPARED)
                .dataAddress(Map.of("presignedUrl", "https://stale-url.example.com"))
                .build();
        DataFlowPrepareResponse freshResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId("view-cached-proc")
                .dataAddress(Map.of("presignedUrl", "https://fresh-url.example.com"))
                .build();

        when(repository.findByProcessId("view-cached-proc")).thenReturn(Optional.of(preparedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.prepare(message)).thenReturn(freshResponse);

        DataFlowPrepareResponse response = service.prepare(message);

        assertEquals("https://fresh-url.example.com", response.getDataAddress().get("presignedUrl"),
                "VIEW mode prepare must return fresh URL, not the stale cached one");
        verify(protocol).prepare(message);
        verify(repository).save(argThat(saved ->
                "view-cached-proc".equals(saved.getProcessId()) && saved.getState() == DataFlowState.PREPARED));
    }

    /**
     * Verifies that the non-VIEW PREPARED idempotency behavior is preserved after the VIEW fix.
     * A non-VIEW prepare for an already PREPARED flow must still return the cached response.
     */
    @Test
    @DisplayName("prepare() still reuses PREPARED cache for non-VIEW mode — idempotency preserved")
    void prepareNonViewModeStillReusesPreparedCache() {
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("push-cached-proc")
                .transferType("HttpData-PUSH")
                .agreementId("agree-push-cached")
                .datasetId("dataset-push-cached")
                .callbackAddress("http://cp/callback")
                .build();
        DataFlowEntity preparedEntity = DataFlowEntity.Builder.newInstance()
                .id("push-cached-proc")
                .processId("push-cached-proc")
                .transferType("HttpData-PUSH")
                .state(DataFlowState.PREPARED)
                .dataAddress(Map.of("endpointUrl", "https://push-endpoint.example.com"))
                .build();

        when(repository.findByProcessId("push-cached-proc")).thenReturn(Optional.of(preparedEntity));

        DataFlowPrepareResponse response = service.prepare(message);

        assertEquals("https://push-endpoint.example.com", response.getDataAddress().get("endpointUrl"),
                "Non-VIEW prepare for PREPARED entity must return cached response");
        verify(protocol, never()).prepare(any(DataFlowPrepareMessage.class));
        verify(repository, never()).save(any(DataFlowEntity.class));
    }

    /**
     * Issue 1 — re-prepare over a terminal entity whose Mongo {@code _id} differs from {@code processId}.
     *
     * <p>When {@code start()} creates an entity without a prior {@code prepare()}, the entity receives a
     * random UUID {@code _id} that differs from {@code processId}. If the flow later reaches a terminal
     * state and the CP calls {@code prepare()} again with the same {@code processId}, the old code
     * always built the new entity with {@code id = processId}. Because the unique index on
     * {@code processId} already has a document with a different {@code _id}, MongoDB would reject the
     * INSERT with DuplicateKey. The fix must reuse the existing entity's {@code _id}.</p>
     */
    @Test
    @DisplayName("prepare() over TERMINATED entity with UUID-based id reuses existing id to avoid DuplicateKey")
    void prepareOverTerminalWithDifferentIdReusesExistingId() {
        String processId = "re-prepare-tp";
        String existingEntityId = "uuid-generated-by-start";

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId(processId)
                .transferType("HttpData-PULL")
                .agreementId("agree-reuse-1")
                .datasetId("dataset-reuse-1")
                .callbackAddress("http://cp/callback")
                .build();
        DataFlowEntity terminatedEntity = DataFlowEntity.Builder.newInstance()
                .id(existingEntityId)
                .processId(processId)
                .transferType("HttpData-PULL")
                .state(DataFlowState.TERMINATED)
                .build();

        when(repository.findByProcessId(processId)).thenReturn(Optional.of(terminatedEntity));
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.prepare(message)).thenReturn(
                DataFlowPrepareResponse.Builder.newInstance().processId(processId).build());

        assertDoesNotThrow(() -> service.prepare(message));

        verify(repository).save(argThat(saved ->
                existingEntityId.equals(saved.getId())
                        && processId.equals(saved.getProcessId())
                        && saved.getState() == DataFlowState.PREPARED));
    }

    /**
     * Verifies that terminate() on a PREPARED flow calls {@code protocol.terminateTransfer(processId)},
     * enabling cleanup of resources allocated during prepare (e.g. HTTP-PUSH temp credentials).
     */
    @Test
    @DisplayName("terminate() on a PREPARED flow calls protocol.terminateTransfer(processId)")
    void terminateOnPreparedFlowUsesProcessId() {
        DataFlowEntity preparedEntity = DataFlowEntity.Builder.newInstance()
                .id("df-prepared-2")
                .processId("prepared-tp-2")
                .transferType("HttpData-PUSH")
                .state(DataFlowState.PREPARED)
                .build();

        when(repository.findByProcessId("prepared-tp-2")).thenReturn(Optional.of(preparedEntity));
        when(registry.getProtocol("HttpData-PUSH")).thenReturn(protocol);
        when(protocol.terminateTransfer("prepared-tp-2")).thenReturn(new CompletableFuture<>());

        service.terminate("prepared-tp-2");

        verify(protocol).terminateTransfer("prepared-tp-2");
        verify(protocol, never()).terminateTransfer("df-prepared-2");
    }
}
