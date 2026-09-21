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

    public static final String DASHBOARD_V1 = "/api/v1/dashboard";

    public static final String DASHBOARD_RUNTIME_V1 = DASHBOARD_V1 + "/runtime";

    public static final String DASHBOARD_NEGOTIATIONS_V1 = DASHBOARD_V1 + "/negotiations";

    public static final String DASHBOARD_TRANSFERS_V1 = DASHBOARD_V1 + "/transfers";

    public static final String DASHBOARD_EVENTS_V1 = DASHBOARD_V1 + "/events";

    public static final String DASHBOARD_SUMMARY_V1 = DASHBOARD_V1 + "/summary";

    /**
     * Connector module - v1 API endpoint for the unified auth contract (login/refresh/logout).
     */
    public static final String AUTH_V1 = "/api/v1/auth";
}
