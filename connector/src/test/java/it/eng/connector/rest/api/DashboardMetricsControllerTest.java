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
                new NegotiationSnapshotMetrics(List.of(), List.of(), 0L),
                new TransferSnapshotMetrics(List.of(), List.of(), List.of(), List.of(), 0L),
                new HistoricalEventMetrics(List.of(), List.of(), List.of(), 0L),
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
        assertTrue(response.getBody().isSuccess());
        assertEquals(runtime, response.getBody().getData());
        verify(runtimeMetricsService).getRuntimeMetrics();
    }
}
