package it.eng.tools.model.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TenantMetricsTest {

    @Test
    void shouldExposeTenantIdTenantNameAndMetrics() {
        TenantMetrics<Long> tenantMetrics = new TenantMetrics<>("tenant-1", "Tenant One", 42L);

        assertEquals("tenant-1", tenantMetrics.tenantId());
        assertEquals("Tenant One", tenantMetrics.tenantName());
        assertEquals(42L, tenantMetrics.metrics());
    }
}
