package it.eng.tools.model.dashboard;

import java.util.List;

/**
 * Snapshot of negotiation counts, optionally broken down per tenant.
 *
 * @param totalCount       total negotiation count in scope
 * @param byState          counts grouped by negotiation state
 * @param byRoleAndState   counts grouped by role and state combined
 * @param byTenant         per-tenant breakdown; {@code null} unless the request was made
 *                         by a superadmin without a tenant scope
 */
public record NegotiationSnapshotMetrics(
        long totalCount,
        List<KeyCount> byState,
        List<KeyCount> byRoleAndState,
        List<TenantMetrics<NegotiationSnapshotMetrics>> byTenant)  {

    /**
     * Backward-compatible constructor for callers that predate the per-tenant breakdown.
     * {@code byTenant} is left {@code null}.
     *
     * @param totalCount     total negotiation count in scope
     * @param byState        counts grouped by negotiation state
     * @param byRoleAndState counts grouped by role and state combined
     */
    public NegotiationSnapshotMetrics(long totalCount, List<KeyCount> byState, List<KeyCount> byRoleAndState) {
        this(totalCount, byState, byRoleAndState, null);
    }
}
