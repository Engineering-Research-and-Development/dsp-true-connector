package it.eng.dataplane.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DataPlaneAuditEventType} enum serialisation.
 */
class DataPlaneAuditEventTypeTest {

    @Test
    @DisplayName("toString returns human-readable description, not enum name")
    void toStringReturnsDescription() {
        assertEquals("Data flow started", DataPlaneAuditEventType.DATAFLOW_STARTED.toString());
        assertEquals("Data flow completed", DataPlaneAuditEventType.DATAFLOW_COMPLETED.toString());
        assertEquals("Data flow failed", DataPlaneAuditEventType.DATAFLOW_FAILED.toString());
        assertEquals("Data flow terminated", DataPlaneAuditEventType.DATAFLOW_TERMINATED.toString());
        assertEquals("Data flow suspended", DataPlaneAuditEventType.DATAFLOW_SUSPENDED.toString());
        assertEquals("Data flow resumed", DataPlaneAuditEventType.DATAFLOW_RESUMED.toString());
        assertEquals("Data flow prepare requested", DataPlaneAuditEventType.DATAFLOW_PREPARE_REQUESTED.toString());
        assertEquals("Data Plane registered with Control Plane", DataPlaneAuditEventType.DP_REGISTRATION_SUCCESS.toString());
        assertEquals("Data Plane registration with Control Plane failed", DataPlaneAuditEventType.DP_REGISTRATION_FAILED.toString());
        assertEquals("Data Plane deregistered from Control Plane", DataPlaneAuditEventType.DP_DEREGISTRATION_SUCCESS.toString());
        assertEquals("Data Plane deregistration from Control Plane failed", DataPlaneAuditEventType.DP_DEREGISTRATION_FAILED.toString());
    }

    @Test
    @DisplayName("All expected event types are present")
    void allExpectedTypesPresent() {
        assertEquals(11, DataPlaneAuditEventType.values().length);
    }
}
