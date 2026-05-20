package it.eng.connector.service;

import it.eng.connector.model.dashboard.DashboardSummaryResponse;
import it.eng.datatransfer.service.TransferMetricsService;
import it.eng.negotiation.service.NegotiationMetricsService;
import it.eng.tools.model.dashboard.TimeWindow;
import it.eng.tools.service.AuditEventMetricsService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Aggregates dashboard metrics for admin APIs.
 */
@Service
public class DashboardMetricsService {

    private static final String HOUR_BUCKET = "hour";
    private static final String DAY_BUCKET = "day";
    private static final long DEFAULT_WINDOW_SECONDS = 24L * 60L * 60L;

    private final NegotiationMetricsService negotiationMetricsService;
    private final TransferMetricsService transferMetricsService;
    private final AuditEventMetricsService auditEventMetricsService;
    private final RuntimeMetricsService runtimeMetricsService;
    private final Clock clock;

    public DashboardMetricsService(
            NegotiationMetricsService negotiationMetricsService,
            TransferMetricsService transferMetricsService,
            AuditEventMetricsService auditEventMetricsService,
            RuntimeMetricsService runtimeMetricsService) {
        this(
                negotiationMetricsService,
                transferMetricsService,
                auditEventMetricsService,
                runtimeMetricsService,
                Clock.systemUTC()
        );
    }

    DashboardMetricsService(
            NegotiationMetricsService negotiationMetricsService,
            TransferMetricsService transferMetricsService,
            AuditEventMetricsService auditEventMetricsService,
            RuntimeMetricsService runtimeMetricsService,
            Clock clock) {
        this.negotiationMetricsService = negotiationMetricsService;
        this.transferMetricsService = transferMetricsService;
        this.auditEventMetricsService = auditEventMetricsService;
        this.runtimeMetricsService = runtimeMetricsService;
        this.clock = clock;
    }

    /**
     * Returns the aggregated dashboard summary for the requested time window and tenant scope.
     *
     * @param window the requested time window
     * @param tenantId the tenant identifier, or {@code null} for cross-tenant scope
     * @return the aggregated dashboard summary
     */
    public DashboardSummaryResponse getSummary(TimeWindow window, String tenantId) {
        return new DashboardSummaryResponse(
                negotiationMetricsService.getSnapshotMetrics(tenantId),
                transferMetricsService.getSnapshotMetrics(tenantId),
                auditEventMetricsService.getHistoricalMetrics(window, tenantId),
                runtimeMetricsService.getRuntimeMetrics()
        );
    }

    /**
     * Parses the query parameter values into a validated time window.
     *
     * @param from the start instant in ISO-8601 format
     * @param to the end instant in ISO-8601 format
     * @param bucket the bucket size, either {@code hour} or {@code day}
     * @return the parsed and validated time window
     */
    public TimeWindow parseWindow(String from, String to, String bucket) {
        String normalizedBucket = normalizeBucket(bucket);
        Instant resolvedTo = parseInstantOrDefault(to, Instant.now(clock));
        Instant resolvedFrom = parseInstantOrDefault(from, resolvedTo.minusSeconds(DEFAULT_WINDOW_SECONDS));
        validateWindow(resolvedFrom, resolvedTo);
        return new TimeWindow(resolvedFrom, resolvedTo, normalizedBucket);
    }

    private String normalizeBucket(String bucket) {
        if (!StringUtils.hasText(bucket)) {
            return HOUR_BUCKET;
        }
        String normalizedBucket = bucket.trim().toLowerCase(Locale.ROOT);
        if (HOUR_BUCKET.equals(normalizedBucket) || DAY_BUCKET.equals(normalizedBucket)) {
            return normalizedBucket;
        }
        throw new IllegalArgumentException("Unsupported time bucket: " + bucket + ". Supported values are: hour, day.");
    }

    private Instant parseInstantOrDefault(String rawValue, Instant defaultValue) {
        if (!StringUtils.hasText(rawValue)) {
            return defaultValue;
        }
        try {
            return Instant.parse(rawValue.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid timestamp: " + rawValue + ". Expected ISO-8601 instant format.",
                    exception
            );
        }
    }

    private void validateWindow(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Invalid time window: 'from' must be before or equal to 'to'.");
        }
    }
}
