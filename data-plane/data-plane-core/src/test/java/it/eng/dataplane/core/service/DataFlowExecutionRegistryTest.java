package it.eng.dataplane.core.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DataFlowExecutionRegistry}.
 */
@ExtendWith(MockitoExtension.class)
class DataFlowExecutionRegistryTest {

    private final DataFlowExecutionRegistry registry = new DataFlowExecutionRegistry();

    private DataFlowExecutionHandle buildHandle(String processId) {
        return new DataFlowExecutionHandle() {
            @Override
            public String getProcessId() {
                return processId;
            }

            @Override
            public void cancel() {
                // no-op for tests
            }
        };
    }

    @Test
    @DisplayName("find returns empty when no handle registered")
    void find_returnsEmpty_whenNotRegistered() {
        Optional<DataFlowExecutionHandle> result = registry.find("not-registered");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("register and find return the same handle")
    void register_andFind_returnsSameHandle() {
        DataFlowExecutionHandle handle = buildHandle("proc-1");
        registry.register("proc-1", handle);

        Optional<DataFlowExecutionHandle> result = registry.find("proc-1");

        assertTrue(result.isPresent());
        assertSame(handle, result.get());
    }

    @Test
    @DisplayName("remove clears the registered handle")
    void remove_removesHandle() {
        DataFlowExecutionHandle handle = buildHandle("proc-2");
        registry.register("proc-2", handle);
        registry.remove("proc-2");

        Optional<DataFlowExecutionHandle> result = registry.find("proc-2");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("remove on non-existent key is a no-op")
    void remove_onMissing_isNoOp() {
        assertDoesNotThrow(() -> registry.remove("non-existent"));
    }

    @Test
    @DisplayName("register replaces existing handle")
    void register_replacesExistingHandle() {
        DataFlowExecutionHandle first = buildHandle("proc-3");
        DataFlowExecutionHandle second = buildHandle("proc-3");
        registry.register("proc-3", first);
        registry.register("proc-3", second);

        Optional<DataFlowExecutionHandle> result = registry.find("proc-3");

        assertTrue(result.isPresent());
        assertSame(second, result.get());
    }
}
