package it.eng.tools.model.dashboard;

import java.util.List;

/**
 * Snapshot of transfer process counts, optionally broken down per tenant.
 *
 * @param totalCount                total transfer process count in scope
 * @param byState                   counts grouped by transfer state
 * @param byRoleAndState            counts grouped by role and state combined
 * @param byFormat                  counts grouped by transfer format
 * @param downloadedCount           count of transfers marked as downloaded
 * @param downloadInProgressCount   count of transfers with a download in progress
 * @param byTenant                  per-tenant breakdown; {@code null} unless the request was made
 *                                  by a superadmin without a tenant scope
 */
public record TransferSnapshotMetrics(
            long totalCount,
            List<KeyCount> byState,
            List<KeyCount> byRoleAndState,
            List<KeyCount> byFormat,
            long downloadedCount,
            long downloadInProgressCount,
            List<TenantMetrics<TransferSnapshotMetrics>> byTenant) {

    /**
     * Backward-compatible constructor for callers that predate the per-tenant breakdown.
     * {@code byTenant} is left {@code null}.
     *
     * @param totalCount              total transfer process count in scope
     * @param byState                 counts grouped by transfer state
     * @param byRoleAndState          counts grouped by role and state combined
     * @param byFormat                counts grouped by transfer format
     * @param downloadedCount         count of transfers marked as downloaded
     * @param downloadInProgressCount count of transfers with a download in progress
     */
    public TransferSnapshotMetrics(long totalCount, List<KeyCount> byState, List<KeyCount> byRoleAndState,
                                   List<KeyCount> byFormat, long downloadedCount, long downloadInProgressCount) {
        this(totalCount, byState, byRoleAndState, byFormat, downloadedCount, downloadInProgressCount, null);
    }
}
