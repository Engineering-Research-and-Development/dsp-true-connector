package it.eng.tools.controller;

/**
 * URL values for API controllers.
 */
public interface ApiEndpoints {

    /**
     * Catalog module - v1 API endpoint for catalogs.
     */
    public static final String CATALOG_CATALOGS_V1 = "/api/v1/catalogs";
    /**
     * Catalog module - v1 API endpoint for dataServices.
     */
    public static final String CATALOG_DATA_SERVICES_V1 = "/api/v1/dataservices";
    /**
     * Catalog module - v1 API endpoint for dataset.
     */
    public static final String CATALOG_DATASETS_V1 = "/api/v1/datasets";
    /**
     * Catalog module - v1 API endpoint for distributions.
     */
    public static final String CATALOG_DISTRIBUTIONS_V1 = "/api/v1/distributions";
    /**
     * Catalog module - v1 API endpoint for offers.
     */
    public static final String CATALOG_OFFERS_V1 = "/api/v1/offers";
    /**
     * Catalog module - v1 API endpoint for artifact handling.
     */
    public static final String CATALOG_ARTIFACT_V1 = "/api/v1/artifacts";

    /**
     * Negotation module - v1 API endpoint for negotations.
     */
    public static final String NEGOTIATION_V1 = "/api/v1/negotiations";
    /**
     * Negotation module - v1 API endpoint for agreements.
     */
    public static final String NEGOTIATION_AGREEMENTS_V1 = "/api/v1/agreements";

    /**
     * DataTransfer module - v1 API endpoint for transfers.
     */
    public static final String TRANSFER_DATATRANSFER_V1 = "/api/v1/transfers";

    /**
     * Proxy endpoint for forwarding API requests to provider.
     */
    public static final String PROXY_V1 = "/api/v1/proxy";

    /**
     * Connector module - v1 API endpoint for user management.
     */
    public static final String USERS_V1 = "/api/v1/users";

    /**
     * Tools module - v1 API end point for application properties.
     */
    public static final String PROPERTIES_V1 = "/api/v1/properties";

    public static final String AUDIT_V1 = "/api/v1/audit";

    /**
     * Tools module - v1 API endpoint for tenant management.
     */
    public static final String TENANTS_V1 = "/api/v1/tenants";

    /**
     * DataTransfer module - v1 API endpoint for Data Plane registrations.
     */
    public static final String DATA_PLANES = "/api/v1/dataplanes";

    /**
     * DataTransfer module - v1 API callback endpoint for Data Plane completion signal.
     */
    public static final String DATAFLOW_CALLBACK_COMPLETE = "/api/v1/dataflows/complete";

    /**
     * DataTransfer module - v1 API callback endpoint for Data Plane error/termination signal.
     */
    public static final String DATAFLOW_CALLBACK_ERROR = "/api/v1/dataflows/error";

    /**
     * Canonical per-transfer Data Plane callback: resources prepared.
     * Path variable {@code {processId}} is the internal transfer process ID.
     */
    public static final String DATAFLOW_CALLBACK_PREPARED = TRANSFER_DATATRANSFER_V1 + "/{processId}/dataflow/prepared";

    /**
     * Canonical per-transfer Data Plane callback: transfer started.
     * Path variable {@code {processId}} is the internal transfer process ID.
     */
    public static final String DATAFLOW_CALLBACK_STARTED = TRANSFER_DATATRANSFER_V1 + "/{processId}/dataflow/started";

    /**
     * Canonical per-transfer Data Plane callback: transfer completed.
     * Path variable {@code {processId}} is the internal transfer process ID.
     */
    public static final String DATAFLOW_CALLBACK_COMPLETED = TRANSFER_DATATRANSFER_V1 + "/{processId}/dataflow/completed";

    /**
     * Canonical per-transfer Data Plane callback: transfer errored.
     * Path variable {@code {processId}} is the internal transfer process ID.
     */
    public static final String DATAFLOW_CALLBACK_ERRORED = TRANSFER_DATATRANSFER_V1 + "/{processId}/dataflow/errored";

    /**
     * Ant-style wildcard pattern matching all canonical per-transfer Data Plane callbacks.
     * Used in Spring Security {@code requestMatchers} to permit these endpoints without authentication.
     */
    public static final String DATAFLOW_CALLBACKS_PATTERN = TRANSFER_DATATRANSFER_V1 + "/*/dataflow/*";
}
