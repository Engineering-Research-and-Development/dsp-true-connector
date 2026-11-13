package it.eng.tools.auth.keycloak.issuer;

/**
 * The kind of a trusted Keycloak issuer. Route rules decide from this type which issuers they accept.
 */
public enum IssuerType {

    /**
     * The machine-to-machine realm ({@code application.keycloak.platform-realm}).
     */
    PLATFORM,

    /**
     * The connector administrator realm ({@code application.keycloak.admin-realm}).
     */
    ADMIN,

    /**
     * The realm of an enabled tenant ({@code Tenant.realm}).
     */
    TENANT
}
