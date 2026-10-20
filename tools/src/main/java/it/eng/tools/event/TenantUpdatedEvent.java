package it.eng.tools.event;

/**
 * Application event published after a tenant's settings have been saved.
 *
 * <p>Listeners that keep a copy of tenant data outside MongoDB (e.g. the tenant name stored as a
 * Keycloak user attribute) use it to resynchronize. It is published on every update, not only on
 * a rename, so re-saving a tenant repairs a copy that a previously failed synchronization left
 * stale.
 *
 * @param tenantId   the tenant identifier
 * @param tenantName the tenant's current display name
 */
public record TenantUpdatedEvent(String tenantId, String tenantName) {
}
