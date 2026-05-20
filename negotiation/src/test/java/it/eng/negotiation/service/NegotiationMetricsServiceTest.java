package it.eng.negotiation.service;

import it.eng.tools.model.dashboard.KeyCount;
import it.eng.tools.model.dashboard.NegotiationSnapshotMetrics;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NegotiationMetricsServiceTest {

    private static final String COLLECTION_NAME = "contract_negotiations";

    @Mock
    private MongoTemplate mongoTemplate;

    @Captor
    private ArgumentCaptor<Aggregation> aggregationCaptor;

    private NegotiationMetricsService negotiationMetricsService;

    @BeforeEach
    void setUp() {
        negotiationMetricsService = new NegotiationMetricsService(mongoTemplate);
    }

    @Test
    @DisplayName("getSnapshotMetrics should map grouped counts by state and role-state")
    void getSnapshotMetrics_shouldMapGroupedCountsByStateAndRoleState() {
        doReturn(aggregationResults(List.of(
                new Document("key", "REQUESTED").append("count", 3L),
                new Document("key", "AGREED").append("count", 1L)
        )))
                .doReturn(aggregationResults(List.of(
                        new Document("key", "consumer:REQUESTED").append("count", 2L),
                        new Document("key", "provider:REQUESTED").append("count", 1L),
                        new Document("key", "consumer:AGREED").append("count", 1L)
                )))
                .doReturn(aggregationResults(List.of(new Document("total", 4L))))
                .when(mongoTemplate)
                .aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class));

        NegotiationSnapshotMetrics metrics = negotiationMetricsService.getSnapshotMetrics("tenant-a");

        assertIterableEquals(List.of(
                new KeyCount("REQUESTED", 3L),
                new KeyCount("AGREED", 1L)
        ), metrics.countsByState());
        assertIterableEquals(List.of(
                new KeyCount("consumer:REQUESTED", 2L),
                new KeyCount("provider:REQUESTED", 1L),
                new KeyCount("consumer:AGREED", 1L)
        ), metrics.countsByRoleAndState());
        assertEquals(4L, metrics.total());
    }

    @Test
    @DisplayName("getSnapshotMetrics should return empty metrics when aggregations return no rows")
    void getSnapshotMetrics_shouldReturnEmptyMetricsWhenNoRowsMatch() {
        stubEmptyAggregationResults();

        NegotiationSnapshotMetrics metrics = negotiationMetricsService.getSnapshotMetrics("tenant-a");

        assertIterableEquals(List.of(), metrics.countsByState());
        assertIterableEquals(List.of(), metrics.countsByRoleAndState());
        assertEquals(0L, metrics.total());
    }

    @Test
    @DisplayName("getSnapshotMetrics should include tenant filtering when tenant id is provided")
    void getSnapshotMetrics_shouldIncludeTenantFilterWhenTenantIdProvided() {
        stubEmptyAggregationResults();

        negotiationMetricsService.getSnapshotMetrics("tenant-a");

        verify(mongoTemplate, atLeastOnce()).aggregate(aggregationCaptor.capture(), eq(COLLECTION_NAME), eq(Document.class));
        String matchStage = String.valueOf(aggregationCaptor.getAllValues().get(0).toPipeline(Aggregation.DEFAULT_CONTEXT).get(0));

        assertTrue(matchStage.contains("tenantId"));
        assertTrue(matchStage.contains("tenant-a"));
    }

    @Test
    @DisplayName("getSnapshotMetrics should aggregate across tenants when tenant id is blank")
    void getSnapshotMetrics_shouldAggregateAcrossTenantsWhenTenantIdBlank() {
        stubEmptyAggregationResults();

        negotiationMetricsService.getSnapshotMetrics("   ");

        verify(mongoTemplate, atLeastOnce()).aggregate(aggregationCaptor.capture(), eq(COLLECTION_NAME), eq(Document.class));
        String matchStage = String.valueOf(aggregationCaptor.getAllValues().get(0).toPipeline(Aggregation.DEFAULT_CONTEXT).get(0));

        assertFalse(matchStage.contains("tenantId"));
    }

    private AggregationResults<Document> aggregationResults(List<Document> documents) {
        return new AggregationResults<>(documents, new Document());
    }

    private void stubEmptyAggregationResults() {
        doReturn(aggregationResults(List.of()))
                .doReturn(aggregationResults(List.of()))
                .doReturn(aggregationResults(List.of()))
                .when(mongoTemplate)
                .aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class));
    }
}
