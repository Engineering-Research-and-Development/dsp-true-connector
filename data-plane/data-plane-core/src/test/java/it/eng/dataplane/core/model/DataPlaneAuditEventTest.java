package it.eng.dataplane.core.model;

import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DataPlaneAuditEvent} builder and validation.
 */
class DataPlaneAuditEventTest {

    @Test
    @DisplayName("Builder sets all fields and defaults timestamp when not provided")
    void builderSetsAllFields() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        DataPlaneAuditEvent event = DataPlaneAuditEvent.Builder.newInstance()
                .id("audit-1")
                .eventType(DataPlaneAuditEventType.DATAFLOW_STARTED)
                .processId("proc-1")
                .transferType("HttpData-PULL")
                .description("Data flow started")
                .details(Map.of("key", "value"))
                .source("http://dp:9090")
                .build();

        assertEquals("audit-1", event.getId());
        assertEquals(DataPlaneAuditEventType.DATAFLOW_STARTED, event.getEventType());
        assertEquals("proc-1", event.getProcessId());
        assertEquals("HttpData-PULL", event.getTransferType());
        assertEquals("Data flow started", event.getDescription());
        assertEquals(Map.of("key", "value"), event.getDetails());
        assertEquals("http://dp:9090", event.getSource());
        assertNotNull(event.getTimestamp());
        assertTrue(event.getTimestamp().isAfter(before));
    }

    @Test
    @DisplayName("Builder uses provided timestamp when set")
    void builderUsesProvidedTimestamp() {
        LocalDateTime ts = LocalDateTime.of(2026, 1, 1, 12, 0);

        DataPlaneAuditEvent event = DataPlaneAuditEvent.Builder.newInstance()
                .eventType(DataPlaneAuditEventType.DATAFLOW_COMPLETED)
                .timestamp(ts)
                .build();

        assertEquals(ts, event.getTimestamp());
    }

    @Test
    @DisplayName("Build throws ValidationException when eventType is null")
    void buildThrowsWhenEventTypeNull() {
        assertThrows(ValidationException.class, () ->
                DataPlaneAuditEvent.Builder.newInstance()
                        .processId("proc-1")
                        .build());
    }

    @Test
    @DisplayName("Builder with only eventType builds successfully")
    void builderMinimalFields() {
        DataPlaneAuditEvent event = DataPlaneAuditEvent.Builder.newInstance()
                .eventType(DataPlaneAuditEventType.DP_REGISTRATION_SUCCESS)
                .build();

        assertEquals(DataPlaneAuditEventType.DP_REGISTRATION_SUCCESS, event.getEventType());
        assertNull(event.getProcessId());
        assertNull(event.getDetails());
        assertNotNull(event.getTimestamp());
    }

    @Test
    @DisplayName("Builder with null details stores null")
    void builderNullDetails() {
        DataPlaneAuditEvent event = DataPlaneAuditEvent.Builder.newInstance()
                .eventType(DataPlaneAuditEventType.DATAFLOW_FAILED)
                .details(null)
                .build();

        assertNull(event.getDetails());
    }
}
