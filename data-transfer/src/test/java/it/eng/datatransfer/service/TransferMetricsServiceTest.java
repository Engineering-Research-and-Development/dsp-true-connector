package it.eng.datatransfer.service;

import it.eng.tools.model.Tenant;
import it.eng.tools.model.dashboard.KeyCount;
import it.eng.tools.model.dashboard.TenantMetrics;
import it.eng.tools.model.dashboard.TransferSnapshotMetrics;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferMetricsServiceTest {

    private static final String COLLECTION_NAME = "transfer_process";

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private TenantRepository tenantRepository;

    @Captor
    private ArgumentCaptor<Aggregation> aggregationCaptor;

    private TransferMetricsService transferMetricsService;

    @BeforeEach
    void setUp() {
        transferMetricsService = new TransferMetricsService(mongoTemplate, tenantRepository);
    }

    @Test
    void getSnapshotShouldReturnNullByTenantWhenTenantIdProvided() {
        stubEmptyAggregationResults();

        TransferSnapshotMetrics result = transferMetricsService.getSnapshotMetrics("tenant-1");

        assertNull(result.byTenant());
    }

    @Test
    void getSnapshotShouldReturnZeroFilledByTenantWhenTenantIdBlank() {
        Tenant tenantA = Tenant.Builder.newInstance().id("tenant-a").name("Tenant A").participantId("participant-a").enabled(true).build();
        Tenant tenantB = Tenant.Builder.newInstance().id("tenant-b").name("Tenant B").participantId("participant-b").enabled(true).build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
        doReturn(aggregationResults(List.of(
                new Document("countsByState", List.of(
                        new Document("tenantId", "tenant-a").append("key", "STARTED").append("count", 2L)
                ))
                        .append("countsByRoleAndState", List.of(
                                new Document("tenantId", "tenant-a").append("key", "consumer:STARTED").append("count", 2L)
                        ))
                        .append("countsByFormat", List.of(
                                new Document("tenantId", "tenant-a").append("key", "HTTP_PULL").append("count", 2L)
                        ))
                        .append("countsByDownloadFlag", List.of(
                                new Document("tenantId", "tenant-a").append("key", "DOWNLOADED_FALSE").append("count", 2L)
                        ))
                        .append("total", List.of(new Document("tenantId", "tenant-a").append("count", 2L)))
        )))
                .when(mongoTemplate)
                .aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class));

        TransferSnapshotMetrics result = transferMetricsService.getSnapshotMetrics("");

        assertEquals(2, result.byTenant().size());
        TenantMetrics<TransferSnapshotMetrics> tenantBEntry = result.byTenant().stream()
                .filter(tm -> tm.tenantId().equals("tenant-b"))
                .findFirst().orElseThrow();
        assertThat(tenantBEntry.metrics().totalCount()).isZero();
        assertThat(tenantBEntry.metrics().downloadedCount()).isZero();
    }

    @Test
    @DisplayName("getSnapshotMetrics should derive all transfer metrics from one aggregation snapshot")
    void getSnapshotMetrics_shouldDeriveAllTransferMetricsFromOneAggregationSnapshot() {
        doReturn(aggregationResults(List.of(
                new Document("countsByState", List.of(
                        new Document("tenantId", "tenant-a").append("key", "STARTED").append("count", 2L),
                        new Document("tenantId", "tenant-a").append("key", "COMPLETED").append("count", 1L)
                ))
                        .append("countsByRoleAndState", List.of(
                                new Document("tenantId", "tenant-a").append("key", "consumer:STARTED").append("count", 2L),
                                new Document("tenantId", "tenant-a").append("key", "provider:COMPLETED").append("count", 1L)
                        ))
                        .append("countsByFormat", List.of(
                                new Document("tenantId", "tenant-a").append("key", "HTTP_PULL").append("count", 2L),
                                new Document("tenantId", "tenant-a").append("key", "HTTP_PUSH").append("count", 1L)
                        ))
                        .append("countsByDownloadFlag", List.of(
                                new Document("tenantId", "tenant-a").append("key", "DOWNLOADED_TRUE").append("count", 1L),
                                new Document("tenantId", "tenant-a").append("key", "DOWNLOADED_FALSE").append("count", 2L),
                                new Document("tenantId", "tenant-a").append("key", "DOWNLOAD_IN_PROGRESS_TRUE").append("count", 1L),
                                new Document("tenantId", "tenant-a").append("key", "DOWNLOAD_IN_PROGRESS_FALSE").append("count", 2L)
                        ))
                        .append("total", List.of(new Document("tenantId", "tenant-a").append("count", 3L)))
        )))
                .when(mongoTemplate)
                .aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class));

        TransferSnapshotMetrics metrics = transferMetricsService.getSnapshotMetrics("tenant-a");

        assertIterableEquals(List.of(
                new KeyCount("STARTED", 2L),
                new KeyCount("COMPLETED", 1L)
        ), metrics.byState());
        assertIterableEquals(List.of(
                new KeyCount("consumer:STARTED", 2L),
                new KeyCount("provider:COMPLETED", 1L)
        ), metrics.byRoleAndState());
        assertIterableEquals(List.of(
                new KeyCount("HTTP_PULL", 2L),
                new KeyCount("HTTP_PUSH", 1L)
        ), metrics.byFormat());
        assertEquals(1L, metrics.downloadedCount());
        assertEquals(1L, metrics.downloadInProgressCount());
        assertEquals(3L, metrics.totalCount());

        verify(mongoTemplate).aggregate(aggregationCaptor.capture(), eq(COLLECTION_NAME), eq(Document.class));
        String pipeline = String.valueOf(aggregationCaptor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT));
        assertTrue(pipeline.contains("$facet"));
        assertTrue(pipeline.contains("state"));
        assertTrue(pipeline.contains("role"));
        assertTrue(pipeline.contains("format"));
        assertTrue(pipeline.contains("isDownloaded"));
        assertTrue(pipeline.contains("isDownloadInProgress"));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    @DisplayName("getSnapshotMetrics should return empty metrics when aggregation returns no rows")
    void getSnapshotMetrics_shouldReturnEmptyMetricsWhenAggregationReturnsNoRows() {
        stubEmptyAggregationResults();

        TransferSnapshotMetrics metrics = transferMetricsService.getSnapshotMetrics("tenant-a");

        assertIterableEquals(List.of(), metrics.byState());
        assertIterableEquals(List.of(), metrics.byRoleAndState());
        assertIterableEquals(List.of(), metrics.byFormat());
        assertEquals(0L, metrics.downloadedCount());
        assertEquals(0L, metrics.downloadInProgressCount());
        assertEquals(0L, metrics.totalCount());
    }

    @Test
    @DisplayName("getSnapshotMetrics should include tenant filtering when tenant id is provided")
    void getSnapshotMetrics_shouldIncludeTenantFilteringWhenTenantIdIsProvided() {
        stubEmptyAggregationResults();

        transferMetricsService.getSnapshotMetrics("tenant-a");

        verify(mongoTemplate).aggregate(aggregationCaptor.capture(), eq(COLLECTION_NAME), eq(Document.class));
        String matchStage = String.valueOf(aggregationCaptor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).get(0));

        assertTrue(matchStage.contains("tenantId"));
        assertTrue(matchStage.contains("tenant-a"));
    }

    @Test
    @DisplayName("getSnapshotMetrics should aggregate across tenants when tenant id is blank")
    void getSnapshotMetrics_shouldAggregateAcrossTenantsWhenTenantIdIsBlank() {
        stubEmptyAggregationResults();

        transferMetricsService.getSnapshotMetrics("   ");

        verify(mongoTemplate).aggregate(aggregationCaptor.capture(), eq(COLLECTION_NAME), eq(Document.class));
        String matchStage = String.valueOf(aggregationCaptor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).get(0));

        assertFalse(matchStage.contains("tenantId"));
    }

    private AggregationResults<Document> aggregationResults(List<Document> documents) {
        return new AggregationResults<>(documents, new Document());
    }

    private void stubEmptyAggregationResults() {
        doReturn(aggregationResults(List.of(new Document("countsByState", List.of())
                .append("countsByRoleAndState", List.of())
                .append("countsByFormat", List.of())
                .append("countsByDownloadFlag", List.of())
                .append("total", List.of()))))
                .when(mongoTemplate)
                .aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class));
    }
}
