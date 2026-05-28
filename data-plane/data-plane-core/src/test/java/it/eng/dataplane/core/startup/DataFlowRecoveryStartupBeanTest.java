package it.eng.dataplane.core.startup;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.repository.DataFlowRepository;
import it.eng.dataplane.core.service.DataFlowCheckpointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataFlowRecoveryStartupBean}.
 */
@ExtendWith(MockitoExtension.class)
class DataFlowRecoveryStartupBeanTest {

    @Mock
    private DataFlowRepository repository;

    @Mock
    private DataFlowCheckpointService checkpointService;

    @Mock
    private DataTransferProtocolRegistry protocolRegistry;

    @Mock
    private DataTransferProtocol protocol;

    @InjectMocks
    private DataFlowRecoveryStartupBean startupBean;

    private DataFlowEntity buildEntity(String processId, DataFlowState state) {
        return DataFlowEntity.Builder.newInstance()
                .id("df-" + processId)
                .processId(processId)
                .state(state)
                .transferType("HttpData-PULL")
                .build();
    }

    @Test
    @DisplayName("recoverOnStartup marks STARTED flow with resumable checkpoint as SUSPENDED")
    void startupRecovery_marksResumableFlowSuspended() {
        DataFlowEntity entity = buildEntity("proc-1", DataFlowState.STARTED);
        when(repository.findAllByStateIn(Set.of(DataFlowState.STARTING, DataFlowState.STARTED)))
                .thenReturn(List.of(entity));
        when(checkpointService.hasResumableCheckpoint("proc-1")).thenReturn(true);
        when(protocolRegistry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.hasUsableAccessMaterial(any(DataFlow.class))).thenReturn(true);

        startupBean.recoverOnStartup();

        ArgumentCaptor<DataFlowEntity> captor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(DataFlowState.SUSPENDED, captor.getValue().getState());
        assertEquals("proc-1", captor.getValue().getProcessId());
    }

    @Test
    @DisplayName("recoverOnStartup marks flow without resumable checkpoint as TERMINATED")
    void startupRecovery_marksUnresumableFlowTerminated() {
        DataFlowEntity entity = buildEntity("proc-2", DataFlowState.STARTED);
        when(repository.findAllByStateIn(Set.of(DataFlowState.STARTING, DataFlowState.STARTED)))
                .thenReturn(List.of(entity));
        when(checkpointService.hasResumableCheckpoint("proc-2")).thenReturn(false);

        startupBean.recoverOnStartup();

        ArgumentCaptor<DataFlowEntity> captor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(DataFlowState.TERMINATED, captor.getValue().getState());
        assertNotNull(captor.getValue().getErrorMessage());
        assertTrue(captor.getValue().getErrorMessage().contains("unrecoverable"));
    }

    @Test
    @DisplayName("recoverOnStartup marks flow with no usable access material as TERMINATED")
    void startupRecovery_marksNoAccessMaterialFlowTerminated() {
        DataFlowEntity entity = buildEntity("proc-3", DataFlowState.STARTING);
        when(repository.findAllByStateIn(Set.of(DataFlowState.STARTING, DataFlowState.STARTED)))
                .thenReturn(List.of(entity));
        when(checkpointService.hasResumableCheckpoint("proc-3")).thenReturn(true);
        when(protocolRegistry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.hasUsableAccessMaterial(any(DataFlow.class))).thenReturn(false);

        startupBean.recoverOnStartup();

        ArgumentCaptor<DataFlowEntity> captor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(DataFlowState.TERMINATED, captor.getValue().getState());
    }

    @Test
    @DisplayName("recoverOnStartup marks flow with null protocol as TERMINATED")
    void startupRecovery_marksNullProtocolFlowTerminated() {
        DataFlowEntity entity = buildEntity("proc-4", DataFlowState.STARTED);
        when(repository.findAllByStateIn(Set.of(DataFlowState.STARTING, DataFlowState.STARTED)))
                .thenReturn(List.of(entity));
        when(checkpointService.hasResumableCheckpoint("proc-4")).thenReturn(true);
        when(protocolRegistry.getProtocol("HttpData-PULL")).thenReturn(null);

        startupBean.recoverOnStartup();

        ArgumentCaptor<DataFlowEntity> captor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(DataFlowState.TERMINATED, captor.getValue().getState());
    }

    @Test
    @DisplayName("recoverOnStartup does nothing when no in-flight flows exist")
    void startupRecovery_doesNothing_whenNoFlows() {
        when(repository.findAllByStateIn(Set.of(DataFlowState.STARTING, DataFlowState.STARTED)))
                .thenReturn(List.of());

        startupBean.recoverOnStartup();

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("recoverOnStartup handles multiple flows independently")
    void startupRecovery_handlesMultipleFlows() {
        DataFlowEntity resumable = buildEntity("proc-r", DataFlowState.STARTED);
        DataFlowEntity unresumable = buildEntity("proc-u", DataFlowState.STARTED);

        when(repository.findAllByStateIn(Set.of(DataFlowState.STARTING, DataFlowState.STARTED)))
                .thenReturn(List.of(resumable, unresumable));
        when(checkpointService.hasResumableCheckpoint("proc-r")).thenReturn(true);
        when(checkpointService.hasResumableCheckpoint("proc-u")).thenReturn(false);
        when(protocolRegistry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.hasUsableAccessMaterial(any(DataFlow.class))).thenReturn(true);

        startupBean.recoverOnStartup();

        ArgumentCaptor<DataFlowEntity> captor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository, times(2)).save(captor.capture());

        List<DataFlowEntity> saved = captor.getAllValues();
        DataFlowEntity savedResumable = saved.stream()
                .filter(e -> "proc-r".equals(e.getProcessId()))
                .findFirst().orElseThrow();
        DataFlowEntity savedUnresumable = saved.stream()
                .filter(e -> "proc-u".equals(e.getProcessId()))
                .findFirst().orElseThrow();

        assertEquals(DataFlowState.SUSPENDED, savedResumable.getState());
        assertEquals(DataFlowState.TERMINATED, savedUnresumable.getState());
    }
}
