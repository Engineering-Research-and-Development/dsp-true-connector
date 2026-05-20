package it.eng.tools.service;

import it.eng.tools.model.dashboard.HistoricalEventMetrics;
import it.eng.tools.model.dashboard.KeyCount;
import it.eng.tools.model.dashboard.TimeBucketCount;
import it.eng.tools.model.dashboard.TimeWindow;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventMetricsServiceTest {

    private static final String COLLECTION_NAME = "audit_events";

    @Mock
    private MongoTemplate mongoTemplate;

    @Captor
    private ArgumentCaptor<Aggregation> aggregationCaptor;

    private AuditEventMetricsService auditEventMetricsService;
    private TimeWindow timeWindow;

    @BeforeEach
    void setUp() {
        auditEventMetricsService = new AuditEventMetricsService(mongoTemplate);
        timeWindow = new TimeWindow(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T23:59:59Z"),
                "hour");
    }

    @Test
    @DisplayName("getHistoricalMetrics should map grouped aggregation results for the requested time window")
    void getHistoricalMetrics_shouldMapGroupedAggregationResultsForWindow() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class)))
                .thenReturn(
                        aggregationResults(List.of(
                                new Document("key", "APPLICATION_START").append("count", 2L),
                                new Document("key", "LOGIN").append("count", 1L)
                        )),
                        aggregationResults(List.of(
                                new Document("key", "admin").append("count", 2L),
                                new Document("key", "user").append("count", 1L)
                        )),
                        aggregationResults(List.of(
                                new Document("bucketStart", Date.from(Instant.parse("2025-01-01T10:00:00Z")))
                                        .append("key", "APPLICATION_START")
                                        .append("count", 2L),
                                new Document("bucketStart", Date.from(Instant.parse("2025-01-01T11:00:00Z")))
                                        .append("key", "LOGIN")
                                        .append("count", 1L)
                        )),
                        aggregationResults(List.of(new Document("total", 3L))));

        HistoricalEventMetrics metrics = auditEventMetricsService.getHistoricalMetrics(timeWindow, "tenant-a");

        assertIterableEquals(List.of(
                new KeyCount("APPLICATION_START", 2L),
                new KeyCount("LOGIN", 1L)
        ), metrics.countsByEventType());
        assertIterableEquals(List.of(
                new KeyCount("admin", 2L),
                new KeyCount("user", 1L)
        ), metrics.countsByRole());
        assertIterableEquals(List.of(
                new TimeBucketCount(Instant.parse("2025-01-01T10:00:00Z"), "APPLICATION_START", 2L),
                new TimeBucketCount(Instant.parse("2025-01-01T11:00:00Z"), "LOGIN", 1L)
        ), metrics.countsOverTime());
        assertEquals(3L, metrics.total());
    }

    @Test
    @DisplayName("getHistoricalMetrics should return empty metrics when the aggregation returns no rows")
    void getHistoricalMetrics_shouldReturnEmptyMetricsWhenNoRowsMatch() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class)))
                .thenReturn(
                        aggregationResults(List.of()),
                        aggregationResults(List.of()),
                        aggregationResults(List.of()),
                        aggregationResults(List.of()));

        HistoricalEventMetrics metrics = auditEventMetricsService.getHistoricalMetrics(timeWindow, "tenant-a");

        assertIterableEquals(List.of(), metrics.countsByEventType());
        assertIterableEquals(List.of(), metrics.countsByRole());
        assertIterableEquals(List.of(), metrics.countsOverTime());
        assertEquals(0L, metrics.total());
    }

    @Test
    @DisplayName("getHistoricalMetrics should include tenant filtering in the aggregation match stage when tenant id is provided")
    void getHistoricalMetrics_shouldIncludeTenantFilterWhenTenantIdProvided() {
        stubEmptyAggregationResults();

        auditEventMetricsService.getHistoricalMetrics(timeWindow, "tenant-a");

        verify(mongoTemplate, atLeastOnce()).aggregate(aggregationCaptor.capture(), eq(COLLECTION_NAME), eq(Document.class));
        String matchStage = String.valueOf(aggregationCaptor.getAllValues().get(0).toPipeline(Aggregation.DEFAULT_CONTEXT).get(0));

        assertTrue(matchStage.contains("tenantId"));
        assertTrue(matchStage.contains("tenant-a"));
        assertTrue(matchStage.contains("timestamp"));
    }

    @Test
    @DisplayName("getHistoricalMetrics should aggregate across tenants when tenant id is blank")
    void getHistoricalMetrics_shouldSkipTenantFilterWhenTenantIdBlank() {
        stubEmptyAggregationResults();

        auditEventMetricsService.getHistoricalMetrics(timeWindow, "   ");

        verify(mongoTemplate, atLeastOnce()).aggregate(aggregationCaptor.capture(), eq(COLLECTION_NAME), eq(Document.class));
        String matchStage = String.valueOf(aggregationCaptor.getAllValues().get(0).toPipeline(Aggregation.DEFAULT_CONTEXT).get(0));

        assertFalse(matchStage.contains("tenantId"));
        assertTrue(matchStage.contains("timestamp"));
    }

    private AggregationResults<Document> aggregationResults(List<Document> documents) {
        return new AggregationResults<>(documents, new Document());
    }

    private void stubEmptyAggregationResults() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class)))
                .thenReturn(
                        aggregationResults(List.of()),
                        aggregationResults(List.of()),
                        aggregationResults(List.of()),
                        aggregationResults(List.of()));
    }
}
