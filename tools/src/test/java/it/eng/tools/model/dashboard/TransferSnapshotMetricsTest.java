package it.eng.tools.model.dashboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TransferSnapshotMetricsTest {

    @Test
    void legacyConstructorShouldLeaveByTenantNull() {
        TransferSnapshotMetrics metrics = new TransferSnapshotMetrics(
                3L, List.of(), List.of(), List.of(), 1L, 0L);

        assertNull(metrics.byTenant());
        assertEquals(3L, metrics.totalCount());
    }

    @Test
    void canonicalConstructorShouldAcceptByTenant() {
        TransferSnapshotMetrics perTenant = new TransferSnapshotMetrics(
                1L, List.of(), List.of(), List.of(), 0L, 0L, null);
        TransferSnapshotMetrics metrics = new TransferSnapshotMetrics(
                3L, List.of(), List.of(), List.of(), 1L, 0L,
                List.of(new TenantMetrics<>("tenant-1", "Tenant One", perTenant)));

        assertEquals(1, metrics.byTenant().size());
        assertEquals(1L, metrics.byTenant().getFirst().metrics().totalCount());
    }
}
