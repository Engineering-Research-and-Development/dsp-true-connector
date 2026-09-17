package it.eng.connector.model.dashboard;

import it.eng.tools.model.dashboard.HistoricalEventMetrics;
import it.eng.tools.model.dashboard.NegotiationSnapshotMetrics;
import it.eng.tools.model.dashboard.TransferSnapshotMetrics;

/**
 * Aggregated dashboard response for admin APIs.
 *
 * @param negotiations the negotiation snapshot metrics
 * @param transfers the transfer snapshot metrics
 * @param events the historical event metrics
 * @param runtime the runtime metrics
 */
public record DashboardSummaryResponse(
        NegotiationSnapshotMetrics negotiations,
        TransferSnapshotMetrics transfers,
        HistoricalEventMetrics events,
        RuntimeMetricsResponse runtime) {
}
