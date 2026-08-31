package it.eng.tools.model.dashboard;

/**
 * Wraps a per-tenant slice of dashboard metrics data.
 *
 * @param tenantId   the tenant's technical identifier
 * @param tenantName the tenant's display name
 * @param metrics    the metrics payload scoped to this tenant
 * @param <T>        the metrics payload type (e.g. {@link NegotiationSnapshotMetrics})
 */
public record TenantMetrics<T>(String tenantId, String tenantName, T metrics)  {
}
