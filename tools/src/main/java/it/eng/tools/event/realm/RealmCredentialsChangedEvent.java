package it.eng.tools.event.realm;

/**
 * Published after the realm credentials of a tenant were set or removed, so that holders of a cached token
 * obtained with the old secret can drop it.
 *
 * @param tenantId the tenant whose realm credentials changed
 */
public record RealmCredentialsChangedEvent(String tenantId) {
}
