package it.eng.dataplane.core;

/**
 * API endpoint path constants for Data Plane to Control Plane communication.
 */
public final class DataPlaneApiEndpoints {

    /** Endpoint for registering data planes with the control plane. */
    public static final String DATA_PLANES = "/api/v1/dataplanes";

    /** Callback endpoint to report a successfully completed data flow. */
    public static final String DATAFLOW_CALLBACK_COMPLETE = "/api/v1/dataflows/complete";

    /** Callback endpoint to report a failed data flow. */
    public static final String DATAFLOW_CALLBACK_ERROR = "/api/v1/dataflows/error";

    private DataPlaneApiEndpoints() {
    }
}
