package it.eng.tools.model.dashboard;

import java.util.List;

/**
 * Snapshot of historical audit event counts, optionally broken down per tenant.
 *
 * @param totalCount   total event count in scope
 * @param byEventType  counts grouped by event type
 * @param byRole       counts grouped by role
 * @param overTime     counts grouped by time bucket
 * @param byTenant     per-tenant breakdown; {@code null} unless the request was made
 *                     by a superadmin without a tenant scope
 */
public record HistoricalEventMetrics(
          long totalCount,
          List<KeyCount> byEventType,
          List<KeyCount> byRole,
          List<TimeBucketCount> overTime,
          List<TenantMetrics<HistoricalEventMetrics>> byTenant){

    /**
     * Backward-compatible constructor for callers that predate the per-tenant breakdown.
     * {@code byTenant} is left {@code null}.
     *
     * @param totalCount  total event count in scope
     * @param byEventType counts grouped by event type
     * @param byRole      counts grouped by role
     * @param overTime    counts grouped by time bucket
     */
    public HistoricalEventMetrics(long totalCount, List<KeyCount> byEventType, List<KeyCount> byRole,
                                  List<TimeBucketCount> overTime) {
        this(totalCount, byEventType, byRole, overTime, null);
    }
}
