package it.eng.tools.model.dashboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HistoricalEventMetricsTest {

    @Test
    void legacyConstructorShouldLeaveByTenantNull() {
        HistoricalEventMetrics metrics = new HistoricalEventMetrics(
                7L, List.of(), List.of(), List.of());

        assertNull(metrics.byTenant());
        assertEquals(7L, metrics.totalCount());
    }

    @Test
    void canonicalConstructorShouldAcceptByTenant() {
        HistoricalEventMetrics perTenant = new HistoricalEventMetrics(2L, List.of(), List.of(), List.of(), null);
        HistoricalEventMetrics metrics = new HistoricalEventMetrics(
                7L, List.of(), List.of(), List.of(),
                List.of(new TenantMetrics<>("tenant-1", "Tenant One", perTenant)));

        assertEquals(1, metrics.byTenant().size());
        assertEquals(2L, metrics.byTenant().getFirst().metrics().totalCount());
    }
}
