package it.eng.dataplane.core.service;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.repository.DataFlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
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
    private DataTransferProtocol protocol;

    private DataFlowService service;

    @BeforeEach
    void setUp() {
        service = new DataFlowService(repository, registry, controlPlaneClient);
    }

    /**
     * Verifies that starting a data flow delegates to the correct protocol implementation
     * and saves the entity to the repository.
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

        // Then
        verify(protocol, times(1)).initiateTransfer(dataFlow);
        
        ArgumentCaptor<DataFlowEntity> entityCaptor = ArgumentCaptor.forClass(DataFlowEntity.class);
        verify(repository).save(entityCaptor.capture());
        
        DataFlowEntity savedEntity = entityCaptor.getValue();
        assertEquals("test-process-123", savedEntity.getProcessId());
        assertEquals("HttpData-PULL", savedEntity.getTransferType());
        assertEquals(DataFlowState.STARTED, savedEntity.getState());
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

        DataFlowEntity existingEntity = new DataFlowEntity();
        existingEntity.setProcessId("duplicate-process");
        existingEntity.setState(DataFlowState.STARTED);

        when(repository.findByProcessId("duplicate-process")).thenReturn(Optional.of(existingEntity));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> service.start(dataFlow));
        
        assertTrue(exception.getMessage().contains("duplicate-process"));
        assertTrue(exception.getMessage().contains("already exists"));
        
        verify(registry, never()).getProtocol(anyString());
        verify(repository, never()).save(any(DataFlowEntity.class));
    }
}
