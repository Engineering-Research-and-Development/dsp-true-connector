package it.eng.connector.service;

import it.eng.connector.model.dashboard.DashboardSummaryResponse;
import it.eng.connector.model.dashboard.RuntimeMetricsResponse;
import it.eng.datatransfer.service.TransferMetricsService;
import it.eng.negotiation.service.NegotiationMetricsService;
import it.eng.tools.model.dashboard.HistoricalEventMetrics;
import it.eng.tools.model.dashboard.KeyCount;
import it.eng.tools.model.dashboard.NegotiationSnapshotMetrics;
import it.eng.tools.model.dashboard.TimeBucketCount;
import it.eng.tools.model.dashboard.TimeWindow;
import it.eng.tools.model.dashboard.TransferSnapshotMetrics;
import it.eng.tools.model.dashboard.TenantMetrics;
import it.eng.tools.service.AuditEventMetricsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardMetricsServiceTest {

    private static final String TENANT_ID = "tenant-a";

    @Mock
    private NegotiationMetricsService negotiationMetricsService;

    @Mock
    private TransferMetricsService transferMetricsService;

    @Mock
    private AuditEventMetricsService auditEventMetricsService;

    @Mock
    private RuntimeMetricsService runtimeMetricsService;

    @Test
    @DisplayName("Get summary aggregates child service results")
    void getSummary_aggregatesChildServiceResults() {
        DashboardMetricsService dashboardMetricsService = new DashboardMetricsService(
                negotiationMetricsService,
                transferMetricsService,
                auditEventMetricsService,
                runtimeMetricsService,
                Clock.systemUTC()
        );
        TimeWindow window = new TimeWindow(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"),
                "day"
        );
        NegotiationSnapshotMetrics negotiations = new NegotiationSnapshotMetrics(2, List.of(new KeyCount("CONFIRMED", 2)), List.of());
        TransferSnapshotMetrics transfers = new TransferSnapshotMetrics(3, List.of(new KeyCount("COMPLETED", 3)), List.of(), List.of(), 1, 0);
        HistoricalEventMetrics events = new HistoricalEventMetrics(
                4,
                List.of(new KeyCount("LOGIN", 4)),
                List.of(new KeyCount("PROVIDER", 4)),
                List.of(new TimeBucketCount(Instant.parse("2025-01-01T00:00:00Z"), "LOGIN", 4))
        );
        RuntimeMetricsResponse runtime = new RuntimeMetricsResponse(0.1d, 0.2d, 10L, 100L, 20L, 5, 1000L);

        when(negotiationMetricsService.getSnapshotMetrics(TENANT_ID)).thenReturn(negotiations);
        when(transferMetricsService.getSnapshotMetrics(TENANT_ID)).thenReturn(transfers);
        when(auditEventMetricsService.getHistoricalMetrics(window, TENANT_ID)).thenReturn(events);
        when(runtimeMetricsService.getRuntimeMetrics()).thenReturn(runtime);

        DashboardSummaryResponse response = dashboardMetricsService.getSummary(window, TENANT_ID);

        assertNotNull(response);
        assertEquals(negotiations, response.negotiations());
        assertEquals(transfers, response.transfers());
        assertEquals(events, response.events());
        assertEquals(runtime, response.runtime());
    }

    @Test
    @DisplayName("Parse window defaults to previous 24 hours and hour bucket")
    void parseWindow_defaultsToPrevious24HoursAndHourBucket() {
        Instant now = Instant.parse("2025-01-10T12:00:00Z");
        DashboardMetricsService dashboardMetricsService = new DashboardMetricsService(
                negotiationMetricsService,
                transferMetricsService,
                auditEventMetricsService,
                runtimeMetricsService,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        TimeWindow window = dashboardMetricsService.parseWindow(null, null, null);

        assertEquals(now.minusSeconds(24 * 60 * 60), window.from());
        assertEquals(now, window.to());
        assertEquals("hour", window.bucket());
    }

    @Test
    @DisplayName("Parse window rejects unsupported buckets")
    void parseWindow_rejectsUnsupportedBucket() {
        DashboardMetricsService dashboardMetricsService = new DashboardMetricsService(
                negotiationMetricsService,
                transferMetricsService,
                auditEventMetricsService,
                runtimeMetricsService,
                Clock.systemUTC()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> dashboardMetricsService.parseWindow(null, null, "week")
        );

        assertEquals("Unsupported time bucket: week. Supported values are: hour, day.", exception.getMessage());
    }

    @Test
    @DisplayName("Parse window rejects from values after to values")
    void parseWindow_rejectsFromAfterTo() {
        DashboardMetricsService dashboardMetricsService = new DashboardMetricsService(
                negotiationMetricsService,
                transferMetricsService,
                auditEventMetricsService,
                runtimeMetricsService,
                Clock.systemUTC()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> dashboardMetricsService.parseWindow("2025-01-11T00:00:00Z", "2025-01-10T00:00:00Z", "day")
        );

        assertEquals("Invalid time window: 'from' must be before or equal to 'to'.", exception.getMessage());
    }

    @Test
    @DisplayName("Get summary should include byTenant when superadmin requests all tenants")
    void getSummary_shouldIncludeByTenantWhenSuperadminRequestsAllTenants() {
        DashboardMetricsService dashboardMetricsService = new DashboardMetricsService(
                negotiationMetricsService,
                transferMetricsService,
                auditEventMetricsService,
                runtimeMetricsService,
                Clock.systemUTC()
        );
        TimeWindow window = new TimeWindow(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"),
                "day"
        );
        NegotiationSnapshotMetrics negotiations = new NegotiationSnapshotMetrics(
                2, List.of(new KeyCount("CONFIRMED", 2)), List.of(),
                List.of(new TenantMetrics<>("tenant-1", "Tenant One",
                        new NegotiationSnapshotMetrics(2, List.of(new KeyCount("CONFIRMED", 2)), List.of(), null))));
        TransferSnapshotMetrics transfers = new TransferSnapshotMetrics(
                3, List.of(new KeyCount("COMPLETED", 3)), List.of(), List.of(), 1, 0,
                List.of(new TenantMetrics<>("tenant-1", "Tenant One",
                        new TransferSnapshotMetrics(3, List.of(new KeyCount("COMPLETED", 3)), List.of(), List.of(), 1, 0, null))));
        HistoricalEventMetrics events = new HistoricalEventMetrics(
                4,
                List.of(new KeyCount("LOGIN", 4)),
                List.of(new KeyCount("PROVIDER", 4)),
                List.of(new TimeBucketCount(Instant.parse("2025-01-01T00:00:00Z"), "LOGIN", 4)),
                List.of(new TenantMetrics<>("tenant-1", "Tenant One",
                        new HistoricalEventMetrics(4, List.of(new KeyCount("LOGIN", 4)), List.of(new KeyCount("PROVIDER", 4)),
                                List.of(new TimeBucketCount(Instant.parse("2025-01-01T00:00:00Z"), "LOGIN", 4)), null))));
        RuntimeMetricsResponse runtime = new RuntimeMetricsResponse(0.1d, 0.2d, 10L, 100L, 20L, 5, 1000L);

        when(negotiationMetricsService.getSnapshotMetrics(null)).thenReturn(negotiations);
        when(transferMetricsService.getSnapshotMetrics(null)).thenReturn(transfers);
        when(auditEventMetricsService.getHistoricalMetrics(window, null)).thenReturn(events);
        when(runtimeMetricsService.getRuntimeMetrics()).thenReturn(runtime);

        DashboardSummaryResponse response = dashboardMetricsService.getSummary(window, null);

        assertNotNull(response);
        assertEquals(negotiations, response.negotiations());
        assertEquals(transfers, response.transfers());
        assertEquals(events, response.events());
        assertEquals(runtime, response.runtime());
    }

    @Test
    @DisplayName("Get summary should omit byTenant when admin requests own tenant")
    void getSummary_shouldOmitByTenantWhenAdminRequestsOwnTenant() {
        DashboardMetricsService dashboardMetricsService = new DashboardMetricsService(
                negotiationMetricsService,
                transferMetricsService,
                auditEventMetricsService,
                runtimeMetricsService,
                Clock.systemUTC()
        );
        TimeWindow window = new TimeWindow(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"),
                "day"
        );
        NegotiationSnapshotMetrics negotiations = new NegotiationSnapshotMetrics(2, List.of(new KeyCount("CONFIRMED", 2)), List.of());
        TransferSnapshotMetrics transfers = new TransferSnapshotMetrics(3, List.of(new KeyCount("COMPLETED", 3)), List.of(), List.of(), 1, 0);
        HistoricalEventMetrics events = new HistoricalEventMetrics(
                4,
                List.of(new KeyCount("LOGIN", 4)),
                List.of(new KeyCount("PROVIDER", 4)),
                List.of(new TimeBucketCount(Instant.parse("2025-01-01T00:00:00Z"), "LOGIN", 4))
        );
        RuntimeMetricsResponse runtime = new RuntimeMetricsResponse(0.1d, 0.2d, 10L, 100L, 20L, 5, 1000L);

        when(negotiationMetricsService.getSnapshotMetrics(TENANT_ID)).thenReturn(negotiations);
        when(transferMetricsService.getSnapshotMetrics(TENANT_ID)).thenReturn(transfers);
        when(auditEventMetricsService.getHistoricalMetrics(window, TENANT_ID)).thenReturn(events);
        when(runtimeMetricsService.getRuntimeMetrics()).thenReturn(runtime);

        DashboardSummaryResponse response = dashboardMetricsService.getSummary(window, TENANT_ID);

        assertNotNull(response);
        assertEquals(negotiations, response.negotiations());
        assertEquals(transfers, response.transfers());
        assertEquals(events, response.events());
        assertEquals(runtime, response.runtime());
    }
}
