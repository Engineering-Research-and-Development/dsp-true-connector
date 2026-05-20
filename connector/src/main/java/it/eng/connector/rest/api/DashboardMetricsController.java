package it.eng.connector.rest.api;

import it.eng.connector.model.dashboard.DashboardSummaryResponse;
import it.eng.connector.model.dashboard.RuntimeMetricsResponse;
import it.eng.connector.service.DashboardMetricsService;
import it.eng.connector.service.RuntimeMetricsService;
import it.eng.datatransfer.service.TransferMetricsService;
import it.eng.negotiation.service.NegotiationMetricsService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.dashboard.HistoricalEventMetrics;
import it.eng.tools.model.dashboard.NegotiationSnapshotMetrics;
import it.eng.tools.model.dashboard.TimeWindow;
import it.eng.tools.model.dashboard.TransferSnapshotMetrics;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventMetricsService;
import it.eng.tools.service.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for dashboard metrics admin endpoints.
 */
@RestController
@RequestMapping(
        path = ApiEndpoints.DASHBOARD_V1,
        consumes = MediaType.ALL_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class DashboardMetricsController {

    private final DashboardMetricsService dashboardMetricsService;
    private final RuntimeMetricsService runtimeMetricsService;
    private final NegotiationMetricsService negotiationMetricsService;
    private final TransferMetricsService transferMetricsService;
    private final AuditEventMetricsService auditEventMetricsService;

    public DashboardMetricsController(
            DashboardMetricsService dashboardMetricsService,
            RuntimeMetricsService runtimeMetricsService,
            NegotiationMetricsService negotiationMetricsService,
            TransferMetricsService transferMetricsService,
            AuditEventMetricsService auditEventMetricsService) {
        this.dashboardMetricsService = dashboardMetricsService;
        this.runtimeMetricsService = runtimeMetricsService;
        this.negotiationMetricsService = negotiationMetricsService;
        this.transferMetricsService = transferMetricsService;
        this.auditEventMetricsService = auditEventMetricsService;
    }

    /**
     * Returns the aggregated dashboard summary.
     *
     * @param from the optional start instant in ISO-8601 format
     * @param to the optional end instant in ISO-8601 format
     * @param bucket the optional aggregation bucket
     * @return 200 OK with the dashboard summary
     */
    @GetMapping(path = "/summary")
    public ResponseEntity<GenericApiResponse<DashboardSummaryResponse>> getSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String bucket) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Fetching dashboard summary for tenant {}", tenantId);
        TimeWindow window = dashboardMetricsService.parseWindow(from, to, bucket);
        DashboardSummaryResponse summary = dashboardMetricsService.getSummary(window, tenantId);
        return ResponseEntity.ok(GenericApiResponse.success(summary, "Dashboard summary fetched"));
    }

    /**
     * Returns runtime-only dashboard metrics.
     *
     * @return 200 OK with runtime metrics
     */
    @GetMapping(path = "/runtime")
    public ResponseEntity<GenericApiResponse<RuntimeMetricsResponse>> getRuntime() {
        log.info("Fetching dashboard runtime metrics");
        RuntimeMetricsResponse runtimeMetrics = runtimeMetricsService.getRuntimeMetrics();
        return ResponseEntity.ok(GenericApiResponse.success(runtimeMetrics, "Dashboard runtime metrics fetched"));
    }

    /**
     * Returns negotiation snapshot metrics.
     *
     * @return 200 OK with negotiation snapshot metrics
     */
    @GetMapping(path = "/negotiations")
    public ResponseEntity<GenericApiResponse<NegotiationSnapshotMetrics>> getNegotiations() {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Fetching dashboard negotiation metrics for tenant {}", tenantId);
        NegotiationSnapshotMetrics metrics = negotiationMetricsService.getSnapshotMetrics(tenantId);
        return ResponseEntity.ok(GenericApiResponse.success(metrics, "Dashboard negotiation metrics fetched"));
    }

    /**
     * Returns transfer snapshot metrics.
     *
     * @return 200 OK with transfer snapshot metrics
     */
    @GetMapping(path = "/transfers")
    public ResponseEntity<GenericApiResponse<TransferSnapshotMetrics>> getTransfers() {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Fetching dashboard transfer metrics for tenant {}", tenantId);
        TransferSnapshotMetrics metrics = transferMetricsService.getSnapshotMetrics(tenantId);
        return ResponseEntity.ok(GenericApiResponse.success(metrics, "Dashboard transfer metrics fetched"));
    }

    /**
     * Returns historical event metrics for the requested time window.
     *
     * @param from the optional start instant in ISO-8601 format
     * @param to the optional end instant in ISO-8601 format
     * @param bucket the optional aggregation bucket
     * @return 200 OK with historical event metrics
     */
    @GetMapping(path = "/events")
    public ResponseEntity<GenericApiResponse<HistoricalEventMetrics>> getEvents(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String bucket) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Fetching dashboard event metrics for tenant {}", tenantId);
        TimeWindow window = dashboardMetricsService.parseWindow(from, to, bucket);
        HistoricalEventMetrics metrics = auditEventMetricsService.getHistoricalMetrics(window, tenantId);
        return ResponseEntity.ok(GenericApiResponse.success(metrics, "Dashboard event metrics fetched"));
    }
}
