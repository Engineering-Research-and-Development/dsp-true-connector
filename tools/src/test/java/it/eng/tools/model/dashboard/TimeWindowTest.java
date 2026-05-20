package it.eng.tools.model.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.tools.controller.ApiEndpoints;

class TimeWindowTest {

    @Test
    @DisplayName("Dashboard API endpoints expose the shared dashboard routes")
    void dashboardApiEndpointsExposeSharedRoutes() {
        assertEquals("/api/v1/dashboard", ApiEndpoints.DASHBOARD_V1);
        assertEquals("/api/v1/dashboard/runtime", ApiEndpoints.DASHBOARD_RUNTIME_V1);
        assertEquals("/api/v1/dashboard/negotiations", ApiEndpoints.DASHBOARD_NEGOTIATIONS_V1);
        assertEquals("/api/v1/dashboard/transfers", ApiEndpoints.DASHBOARD_TRANSFERS_V1);
        assertEquals("/api/v1/dashboard/events", ApiEndpoints.DASHBOARD_EVENTS_V1);
        assertEquals("/api/v1/dashboard/summary", ApiEndpoints.DASHBOARD_SUMMARY_V1);
    }

    @Test
    @DisplayName("Dashboard records expose constructor values")
    void dashboardRecordsExposeConstructorValues() {
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");
        TimeWindow timeWindow = new TimeWindow(from, to, "day");

        KeyCount eventTypeCount = new KeyCount("TRANSFER_STARTED", 4L);
        KeyCount roleCount = new KeyCount("provider", 2L);
        TimeBucketCount timeBucketCount = new TimeBucketCount(from, "TRANSFER_STARTED", 3L);
        HistoricalEventMetrics historicalEventMetrics = new HistoricalEventMetrics(
                List.of(eventTypeCount),
                List.of(roleCount),
                List.of(timeBucketCount),
                6L);

        KeyCount negotiationStateCount = new KeyCount("REQUESTED", 5L);
        KeyCount negotiationRoleStateCount = new KeyCount("consumer:REQUESTED", 5L);
        NegotiationSnapshotMetrics negotiationSnapshotMetrics = new NegotiationSnapshotMetrics(
                List.of(negotiationStateCount),
                List.of(negotiationRoleStateCount),
                5L);

        KeyCount transferStateCount = new KeyCount("STARTED", 7L);
        KeyCount transferRoleStateCount = new KeyCount("provider:STARTED", 7L);
        KeyCount formatCount = new KeyCount("application/json", 3L);
        KeyCount downloadFlagCount = new KeyCount("true", 2L);
        TransferSnapshotMetrics transferSnapshotMetrics = new TransferSnapshotMetrics(
                List.of(transferStateCount),
                List.of(transferRoleStateCount),
                List.of(formatCount),
                List.of(downloadFlagCount),
                7L);

        assertEquals(from, timeWindow.from());
        assertEquals(to, timeWindow.to());
        assertEquals("day", timeWindow.bucket());

        assertEquals("TRANSFER_STARTED", eventTypeCount.key());
        assertEquals(4L, eventTypeCount.count());
        assertEquals(from, timeBucketCount.bucketStart());
        assertEquals("TRANSFER_STARTED", timeBucketCount.key());
        assertEquals(3L, timeBucketCount.count());

        assertIterableEquals(List.of(eventTypeCount), historicalEventMetrics.countsByEventType());
        assertIterableEquals(List.of(roleCount), historicalEventMetrics.countsByRole());
        assertIterableEquals(List.of(timeBucketCount), historicalEventMetrics.countsOverTime());
        assertEquals(6L, historicalEventMetrics.total());

        assertIterableEquals(List.of(negotiationStateCount), negotiationSnapshotMetrics.countsByState());
        assertIterableEquals(List.of(negotiationRoleStateCount), negotiationSnapshotMetrics.countsByRoleAndState());
        assertEquals(5L, negotiationSnapshotMetrics.total());

        assertIterableEquals(List.of(transferStateCount), transferSnapshotMetrics.countsByState());
        assertIterableEquals(List.of(transferRoleStateCount), transferSnapshotMetrics.countsByRoleAndState());
        assertIterableEquals(List.of(formatCount), transferSnapshotMetrics.countsByFormat());
        assertIterableEquals(List.of(downloadFlagCount), transferSnapshotMetrics.countsByDownloadFlag());
        assertEquals(7L, transferSnapshotMetrics.total());
    }
}
