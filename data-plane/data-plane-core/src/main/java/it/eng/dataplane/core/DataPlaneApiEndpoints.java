package it.eng.dataplane.core;

/**
 * API endpoint path constants for Data Plane to Control Plane communication.
 *
 * <p><strong>Note:</strong> {@link #DATA_PLANES}, {@link #DATAFLOW_CALLBACK_COMPLETE}, and
 * {@link #DATAFLOW_CALLBACK_ERROR} duplicate values declared in
 * {@code it.eng.tools.controller.ApiEndpoints}. Both sides intentionally keep their own copy
 * to avoid a compile-time dependency between the Data Plane and the CP {@code tools} module.
 * If you change a path here, update the matching constant in {@code ApiEndpoints} too.</p>
 */
public final class DataPlaneApiEndpoints {

    /** Endpoint for registering data planes with the control plane. */
    public static final String DATA_PLANES = "/api/v1/dataplanes";

    /** Callback endpoint to report a successfully completed data flow. */
    public static final String DATAFLOW_CALLBACK_COMPLETE = "/api/v1/dataflows/complete";

    /** Callback endpoint to report a failed data flow. */
    public static final String DATAFLOW_CALLBACK_ERROR = "/api/v1/dataflows/error";

    /** Endpoint for querying Data Plane audit events. */
    public static final String AUDIT_EVENTS_V1 = "/api/v1/audit";

    private DataPlaneApiEndpoints() {
    }
}
