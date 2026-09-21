package it.eng.connector.rest.api;

import it.eng.connector.model.dashboard.DashboardSummaryResponse;
import it.eng.connector.model.dashboard.RuntimeMetricsResponse;
import it.eng.connector.service.DashboardMetricsService;
import it.eng.connector.service.RuntimeMetricsService;
import it.eng.datatransfer.service.TransferMetricsService;
import it.eng.negotiation.service.NegotiationMetricsService;
import it.eng.tools.model.dashboard.HistoricalEventMetrics;
import it.eng.tools.model.dashboard.NegotiationSnapshotMetrics;
import it.eng.tools.model.dashboard.TimeWindow;
import it.eng.tools.model.dashboard.TransferSnapshotMetrics;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventMetricsService;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.model.dashboard.TenantMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardMetricsControllerTest {

    private static final String TENANT_ID = "tenant-a";

    @Mock
    private DashboardMetricsService dashboardMetricsService;

    @Mock
    private RuntimeMetricsService runtimeMetricsService;

    @Mock
    private NegotiationMetricsService negotiationMetricsService;

    @Mock
    private TransferMetricsService transferMetricsService;

    @Mock
    private AuditEventMetricsService auditEventMetricsService;

    @InjectMocks
    private DashboardMetricsController controller;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Summary endpoint returns success response and delegates to service")
    void getSummary_returnsSuccessResponseAndDelegates() {
        TenantContextHolder.setTenantId(TENANT_ID);
        TimeWindow window = new TimeWindow(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"),
                "day"
        );
        DashboardSummaryResponse summary = new DashboardSummaryResponse(
                new NegotiationSnapshotMetrics(0L, List.of(), List.of()),
                new TransferSnapshotMetrics(0L, List.of(), List.of(), List.of(), 0L, 0L),
                new HistoricalEventMetrics(0L, List.of(), List.of(), List.of()),
                new RuntimeMetricsResponse(0.1d, 0.2d, 10L, 100L, 20L, 5, 1000L)
        );
        when(dashboardMetricsService.parseWindow("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z", "day")).thenReturn(window);
        when(dashboardMetricsService.getSummary(window, TENANT_ID)).thenReturn(summary);

        ResponseEntity<GenericApiResponse<DashboardSummaryResponse>> response = controller.getSummary(
                "2025-01-01T00:00:00Z",
                "2025-01-02T00:00:00Z",
                "day"
        );

        assertNotNull(response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(response.getBody().isSuccess());
        assertEquals(summary, response.getBody().getData());
        verify(dashboardMetricsService).parseWindow("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z", "day");
        verify(dashboardMetricsService).getSummary(window, TENANT_ID);
    }

    @Test
    @DisplayName("Runtime endpoint returns success response and delegates to service")
    void getRuntime_returnsSuccessResponseAndDelegates() {
        RuntimeMetricsResponse runtime = new RuntimeMetricsResponse(0.1d, 0.2d, 10L, 100L, 20L, 5, 1000L);
        when(runtimeMetricsService.getRuntimeMetrics()).thenReturn(runtime);

        ResponseEntity<GenericApiResponse<RuntimeMetricsResponse>> response = controller.getRuntime();

        assertNotNull(response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(response.getBody().isSuccess());
        assertEquals(runtime, response.getBody().getData());
        verify(runtimeMetricsService).getRuntimeMetrics();
    }

    @Test
    @DisplayName("Negotiations endpoint returns success response and delegates to service")
    void getNegotiations_returnsSuccessResponseAndDelegates() {
        TenantContextHolder.setTenantId(TENANT_ID);
        NegotiationSnapshotMetrics metrics = new NegotiationSnapshotMetrics(4L, List.of(), List.of());
        when(negotiationMetricsService.getSnapshotMetrics(TENANT_ID)).thenReturn(metrics);

        ResponseEntity<GenericApiResponse<NegotiationSnapshotMetrics>> response = controller.getNegotiations();

        assertNotNull(response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(response.getBody().isSuccess());
        assertEquals(metrics, response.getBody().getData());
        verify(negotiationMetricsService).getSnapshotMetrics(TENANT_ID);
    }

    @Test
    @DisplayName("Transfers endpoint returns success response and delegates to service")
    void getTransfers_returnsSuccessResponseAndDelegates() {
        TenantContextHolder.setTenantId(TENANT_ID);
        TransferSnapshotMetrics metrics = new TransferSnapshotMetrics(6L, List.of(), List.of(), List.of(), 1L, 1L);
        when(transferMetricsService.getSnapshotMetrics(TENANT_ID)).thenReturn(metrics);

        ResponseEntity<GenericApiResponse<TransferSnapshotMetrics>> response = controller.getTransfers();

        assertNotNull(response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(response.getBody().isSuccess());
        assertEquals(metrics, response.getBody().getData());
        verify(transferMetricsService).getSnapshotMetrics(TENANT_ID);
    }

    @Test
    @DisplayName("Events endpoint returns success response and delegates to service")
    void getEvents_returnsSuccessResponseAndDelegates() {
        TenantContextHolder.setTenantId(TENANT_ID);
        TimeWindow window = new TimeWindow(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"),
                "day"
        );
        HistoricalEventMetrics metrics = new HistoricalEventMetrics(3L, List.of(), List.of(), List.of());
        when(dashboardMetricsService.parseWindow("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z", "day")).thenReturn(window);
        when(auditEventMetricsService.getHistoricalMetrics(window, TENANT_ID)).thenReturn(metrics);

        ResponseEntity<GenericApiResponse<HistoricalEventMetrics>> response = controller.getEvents(
                "2025-01-01T00:00:00Z",
                "2025-01-02T00:00:00Z",
                "day"
        );

        assertNotNull(response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(response.getBody().isSuccess());
        assertEquals(metrics, response.getBody().getData());
        verify(dashboardMetricsService).parseWindow("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z", "day");
        verify(auditEventMetricsService).getHistoricalMetrics(window, TENANT_ID);
    }

    @Test
    @DisplayName("Summary should include byTenant when superadmin requests all tenants")
    void getSummary_shouldIncludeByTenantWhenSuperadminRequestsAllTenants() {
        TenantContextHolder.setTenantId(null);
        TimeWindow window = new TimeWindow(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"),
                "day"
        );
        NegotiationSnapshotMetrics negotiationMetrics = new NegotiationSnapshotMetrics(
                1L, List.of(), List.of(),
                List.of(new TenantMetrics<>("tenant-1", "Tenant One",
                        new NegotiationSnapshotMetrics(1L, List.of(), List.of(), null))));
        TransferSnapshotMetrics transferMetrics = new TransferSnapshotMetrics(
                1L, List.of(), List.of(), List.of(), 0L, 0L,
                List.of(new TenantMetrics<>("tenant-1", "Tenant One",
                        new TransferSnapshotMetrics(1L, List.of(), List.of(), List.of(), 0L, 0L, null))));
        HistoricalEventMetrics eventMetrics = new HistoricalEventMetrics(
                1L, List.of(), List.of(), List.of(),
                List.of(new TenantMetrics<>("tenant-1", "Tenant One",
                        new HistoricalEventMetrics(1L, List.of(), List.of(), List.of(), null))));
        DashboardSummaryResponse summary = new DashboardSummaryResponse(
                negotiationMetrics,
                transferMetrics,
                eventMetrics,
                new RuntimeMetricsResponse(0.1d, 0.2d, 10L, 100L, 20L, 5, 1000L)
        );
        when(dashboardMetricsService.parseWindow("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z", "day")).thenReturn(window);
        when(dashboardMetricsService.getSummary(window, null)).thenReturn(summary);

        ResponseEntity<GenericApiResponse<DashboardSummaryResponse>> response = controller.getSummary(
                "2025-01-01T00:00:00Z",
                "2025-01-02T00:00:00Z",
                "day"
        );

        assertNotNull(response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(response.getBody().isSuccess());
        DashboardSummaryResponse data = response.getBody().getData();
        assertEquals(summary, data);
    }

    @Test
    @DisplayName("Summary should omit byTenant when admin requests own tenant")
    void getSummary_shouldOmitByTenantWhenAdminRequestsOwnTenant() {
        TenantContextHolder.setTenantId(TENANT_ID);
        TimeWindow window = new TimeWindow(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"),
                "day"
        );
        NegotiationSnapshotMetrics negotiationMetrics = new NegotiationSnapshotMetrics(1L, List.of(), List.of());
        TransferSnapshotMetrics transferMetrics = new TransferSnapshotMetrics(1L, List.of(), List.of(), List.of(), 0L, 0L);
        HistoricalEventMetrics eventMetrics = new HistoricalEventMetrics(1L, List.of(), List.of(), List.of());
        DashboardSummaryResponse summary = new DashboardSummaryResponse(
                negotiationMetrics,
                transferMetrics,
                eventMetrics,
                new RuntimeMetricsResponse(0.1d, 0.2d, 10L, 100L, 20L, 5, 1000L)
        );
        when(dashboardMetricsService.parseWindow("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z", "day")).thenReturn(window);
        when(dashboardMetricsService.getSummary(window, TENANT_ID)).thenReturn(summary);

        ResponseEntity<GenericApiResponse<DashboardSummaryResponse>> response = controller.getSummary(
                "2025-01-01T00:00:00Z",
                "2025-01-02T00:00:00Z",
                "day"
        );

        assertNotNull(response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(response.getBody().isSuccess());
        DashboardSummaryResponse data = response.getBody().getData();
        assertEquals(summary, data);
    }
}
