package it.eng.tools.model.dashboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NegotiationSnapshotMetricsTest {

    @Test
    void legacyConstructorShouldLeaveByTenantNull() {
        NegotiationSnapshotMetrics metrics = new NegotiationSnapshotMetrics(
                5L, List.of(new KeyCount("REQUESTED", 5L)), List.of(new KeyCount("REQUESTED_PROVIDER", 5L)));

        assertNull(metrics.byTenant());
        assertEquals(5L, metrics.totalCount());
    }

    @Test
    void canonicalConstructorShouldAcceptByTenant() {
        NegotiationSnapshotMetrics perTenant = new NegotiationSnapshotMetrics(
                2L, List.of(new KeyCount("REQUESTED", 2L)), List.of(), null);
        NegotiationSnapshotMetrics metrics = new NegotiationSnapshotMetrics(
                5L, List.of(new KeyCount("REQUESTED", 5L)), List.of(),
                List.of(new TenantMetrics<>("tenant-1", "Tenant One", perTenant)));

        assertEquals(1, metrics.byTenant().size());
        assertEquals("tenant-1", metrics.byTenant().getFirst().tenantId());
        assertNull(metrics.byTenant().getFirst().metrics().byTenant());
    }
}
