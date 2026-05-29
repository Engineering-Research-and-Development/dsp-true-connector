package it.eng.dataplane.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link DataFlowCheckpoint} copy-style update methods.
 */
class DataFlowCheckpointTest {

    @Test
    @DisplayName("withCompletedPart accepts explicit contiguous confirmed bytes")
    void withCompletedPart_acceptsExplicitContiguousConfirmedBytes() {
        DataFlowCheckpoint original = DataFlowCheckpoint.Builder.newInstance()
                .processId("proc-1")
                .dataFlowId("df-1")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .confirmedBytes(8L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Method withCompletedPart = assertDoesNotThrow(() -> DataFlowCheckpoint.class.getMethod(
                "withCompletedPart", int.class, long.class, String.class, long.class));

        DataFlowCheckpoint updated = assertDoesNotThrow(() -> (DataFlowCheckpoint) withCompletedPart.invoke(
                original, 3, 4L, "etag-3", 8L));

        assertEquals(List.of(3), updated.getCompletedParts());
        assertEquals(Map.of(3, 4L), updated.getPartSizes());
        assertEquals(Map.of(3, "etag-3"), updated.getPartETags());
        assertEquals(8L, updated.getConfirmedBytes());
    }
}
