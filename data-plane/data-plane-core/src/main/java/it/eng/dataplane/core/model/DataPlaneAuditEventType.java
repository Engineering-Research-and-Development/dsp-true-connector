package it.eng.dataplane.core.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of audit event types emitted by the Data Plane.
 */
public enum DataPlaneAuditEventType {

    DATAFLOW_STARTED("Data flow started"),
    DATAFLOW_PREPARE_REQUESTED("Data flow prepare requested"),
    DATAFLOW_COMPLETED("Data flow completed"),
    DATAFLOW_FAILED("Data flow failed"),
    DATAFLOW_TERMINATED("Data flow terminated"),
    DATAFLOW_SUSPENDED("Data flow suspended"),
    DP_REGISTRATION_SUCCESS("Data Plane registered with Control Plane"),
    DP_REGISTRATION_FAILED("Data Plane registration with Control Plane failed");

    private final String description;

    DataPlaneAuditEventType(String description) {
        this.description = description;
    }

    /**
     * Returns the human-readable description used in JSON serialization.
     *
     * @return event type description
     */
    @Override
    @JsonValue
    public String toString() {
        return description;
    }
}
