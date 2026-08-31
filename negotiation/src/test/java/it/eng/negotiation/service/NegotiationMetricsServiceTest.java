package it.eng.negotiation.service;

import it.eng.tools.model.Tenant;
import it.eng.tools.model.dashboard.KeyCount;
import it.eng.tools.model.dashboard.NegotiationSnapshotMetrics;
import it.eng.tools.model.dashboard.TenantMetrics;
import it.eng.tools.repository.TenantRepository;
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NegotiationMetricsServiceTest {

    private static final String COLLECTION_NAME = "contract_negotiations";

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private TenantRepository tenantRepository;

    @Captor
    private ArgumentCaptor<Aggregation> aggregationCaptor;

    private NegotiationMetricsService negotiationMetricsService;

    @BeforeEach
    void setUp() {
        negotiationMetricsService = new NegotiationMetricsService(mongoTemplate, tenantRepository);
    }

    @Test
    void getSnapshotShouldReturnNullByTenantWhenTenantIdProvided() {
        stubEmptyAggregationResults();

        NegotiationSnapshotMetrics result = negotiationMetricsService.getSnapshotMetrics("tenant-1");

        assertNull(result.byTenant());
    }

    @Test
    void getSnapshotShouldReturnZeroFilledByTenantWhenTenantIdBlank() {
        Tenant tenantA = Tenant.Builder.newInstance().id("tenant-a").name("Tenant A").enabled(true).build();
        Tenant tenantB = Tenant.Builder.newInstance().id("tenant-b").name("Tenant B").enabled(true).build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
        doReturn(aggregationResults(List.of(
                new Document("tenantId", "tenant-a").append("state", "REQUESTED")
                        .append("key", "consumer:REQUESTED").append("count", 2L)
        )))
                .when(mongoTemplate)
                .aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class));

        NegotiationSnapshotMetrics result = negotiationMetricsService.getSnapshotMetrics("");

        assertEquals(2, result.byTenant().size());
//        assertThat(result.byTenant())
//                .extracting(TenantMetrics<NegotiationSnapshotMetrics>::tenantId)
//                .containsExactlyInAnyOrder("tenant-a", "tenant-b");
        TenantMetrics<NegotiationSnapshotMetrics> tenantBEntry = result.byTenant().stream()
                .filter(tm -> tm.tenantId().equals("tenant-b"))
                .findFirst().orElseThrow();
        assertThat(tenantBEntry.metrics().totalCount()).isZero();
    }

    @Test
    @DisplayName("getSnapshotMetrics should derive all metrics from one grouped aggregation")
    void getSnapshotMetrics_shouldDeriveAllMetricsFromOneGroupedAggregation() {
        doReturn(aggregationResults(List.of(
                new Document("state", "REQUESTED").append("key", "consumer:REQUESTED").append("count", 2L),
                new Document("state", "AGREED").append("key", "consumer:AGREED").append("count", 1L),
                new Document("state", "REQUESTED").append("key", "provider:REQUESTED").append("count", 1L)
        )))
                .when(mongoTemplate)
                .aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class));

        NegotiationSnapshotMetrics metrics = negotiationMetricsService.getSnapshotMetrics("tenant-a");

        assertIterableEquals(List.of(
                new KeyCount("REQUESTED", 3L),
                new KeyCount("AGREED", 1L)
        ), metrics.byState());
        assertIterableEquals(List.of(
                new KeyCount("consumer:REQUESTED", 2L),
                new KeyCount("consumer:AGREED", 1L),
                new KeyCount("provider:REQUESTED", 1L)
        ), metrics.byRoleAndState());
        assertEquals(4L, metrics.totalCount());
        verify(mongoTemplate).aggregate(aggregationCaptor.capture(), eq(COLLECTION_NAME), eq(Document.class));
        String pipeline = String.valueOf(aggregationCaptor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT));
        Document projectStage = (Document) aggregationCaptor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).get(2);
        Document projectFields = (Document) projectStage.get("$project");
        assertTrue(pipeline.contains("role"));
        assertTrue(pipeline.contains("state"));
        assertEquals("$count", projectFields.get("count"));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    @DisplayName("getSnapshotMetrics should return empty metrics when grouped aggregation returns no rows")
    void getSnapshotMetrics_shouldReturnEmptyMetricsWhenNoRowsMatch() {
        stubEmptyAggregationResults();

        NegotiationSnapshotMetrics metrics = negotiationMetricsService.getSnapshotMetrics("tenant-a");

        assertIterableEquals(List.of(), metrics.byState());
        assertIterableEquals(List.of(), metrics.byRoleAndState());
        assertEquals(0L, metrics.totalCount());
    }

    @Test
    @DisplayName("getSnapshotMetrics should include tenant filtering when tenant id is provided")
    void getSnapshotMetrics_shouldIncludeTenantFilterWhenTenantIdProvided() {
        stubEmptyAggregationResults();

        negotiationMetricsService.getSnapshotMetrics("tenant-a");

        verify(mongoTemplate).aggregate(aggregationCaptor.capture(), eq(COLLECTION_NAME), eq(Document.class));
        String matchStage = String.valueOf(aggregationCaptor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).get(0));

        assertTrue(matchStage.contains("tenantId"));
        assertTrue(matchStage.contains("tenant-a"));
    }

    @Test
    @DisplayName("getSnapshotMetrics should aggregate across tenants when tenant id is blank")
    void getSnapshotMetrics_shouldAggregateAcrossTenantsWhenTenantIdBlank() {
        stubEmptyAggregationResults();
        when(tenantRepository.findAll()).thenReturn(List.of());

        negotiationMetricsService.getSnapshotMetrics("   ");

        verify(mongoTemplate).aggregate(aggregationCaptor.capture(), eq(COLLECTION_NAME), eq(Document.class));
        String matchStage = String.valueOf(aggregationCaptor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).get(0));

        assertFalse(matchStage.contains("tenantId"));
    }

    private AggregationResults<Document> aggregationResults(List<Document> documents) {
        return new AggregationResults<>(documents, new Document());
    }

    private void stubEmptyAggregationResults() {
        doReturn(aggregationResults(List.of()))
                .when(mongoTemplate)
                .aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class));
    }
}
