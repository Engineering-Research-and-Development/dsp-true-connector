package it.eng.tools.auth.keycloak.realm;

/**
 * Published when the realm credentials of a tenant are saved or deleted, so that cached
 * client registrations and tokens for that tenant can be evicted.
 *
 * @param tenantId the tenant whose credentials changed
 */
public record RealmCredentialsChangedEvent(String tenantId) {
}
