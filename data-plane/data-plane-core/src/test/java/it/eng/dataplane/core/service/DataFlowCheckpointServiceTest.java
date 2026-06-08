package it.eng.dataplane.core.service;

import it.eng.dataplane.core.model.DataFlowCheckpoint;
import it.eng.dataplane.core.repository.DataFlowCheckpointRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataFlowCheckpointService}.
 */
@ExtendWith(MockitoExtension.class)
class DataFlowCheckpointServiceTest {

    @Mock
    private DataFlowCheckpointRepository repository;

    @InjectMocks
    private DataFlowCheckpointService service;

    private DataFlowCheckpoint buildCheckpoint(String processId) {
        return DataFlowCheckpoint.Builder.newInstance()
                .processId(processId)
                .dataFlowId("df-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("findByProcessId returns empty when no checkpoint exists")
    void findByProcessId_returnsEmpty_whenNotFound() {
        when(repository.findById("proc-1")).thenReturn(Optional.empty());

        Optional<DataFlowCheckpoint> result = service.findByProcessId("proc-1");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findByProcessId returns checkpoint when it exists")
    void findByProcessId_returnsCheckpoint_whenFound() {
        DataFlowCheckpoint checkpoint = buildCheckpoint("proc-2");
        when(repository.findById("proc-2")).thenReturn(Optional.of(checkpoint));

        Optional<DataFlowCheckpoint> result = service.findByProcessId("proc-2");

        assertTrue(result.isPresent());
        assertEquals("proc-2", result.get().getProcessId());
    }

    @Test
    @DisplayName("findRequiredByProcessId throws when checkpoint not found")
    void findRequiredByProcessId_throws_whenNotFound() {
        when(repository.findById("proc-missing")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.findRequiredByProcessId("proc-missing"));
    }

    @Test
    @DisplayName("findRequiredByProcessId returns checkpoint when it exists")
    void findRequiredByProcessId_returnsCheckpoint_whenFound() {
        DataFlowCheckpoint checkpoint = buildCheckpoint("proc-3");
        when(repository.findById("proc-3")).thenReturn(Optional.of(checkpoint));

        DataFlowCheckpoint result = service.findRequiredByProcessId("proc-3");

        assertEquals("proc-3", result.getProcessId());
    }

    @Test
    @DisplayName("hasResumableCheckpoint returns false when no checkpoint exists")
    void hasResumableCheckpoint_returnsFalse_whenNoCheckpoint() {
        when(repository.findById("proc-no")).thenReturn(Optional.empty());

        assertFalse(service.hasResumableCheckpoint("proc-no"));
    }

    @Test
    @DisplayName("hasResumableCheckpoint returns true when checkpoint exists")
    void hasResumableCheckpoint_returnsTrue_whenCheckpointExists() {
        DataFlowCheckpoint checkpoint = buildCheckpoint("proc-yes");
        when(repository.findById("proc-yes")).thenReturn(Optional.of(checkpoint));

        assertTrue(service.hasResumableCheckpoint("proc-yes"));
    }

    @Test
    @DisplayName("save delegates to repository")
    void save_delegatesToRepository() {
        DataFlowCheckpoint checkpoint = buildCheckpoint("proc-save");
        when(repository.save(checkpoint)).thenReturn(checkpoint);

        DataFlowCheckpoint saved = service.save(checkpoint);

        verify(repository).save(checkpoint);
        assertEquals("proc-save", saved.getProcessId());
    }

    @Test
    @DisplayName("deleteByProcessId delegates to repository deleteById")
    void deleteByProcessId_delegatesToRepository() {
        service.deleteByProcessId("proc-del");

        verify(repository).deleteById("proc-del");
    }
}
