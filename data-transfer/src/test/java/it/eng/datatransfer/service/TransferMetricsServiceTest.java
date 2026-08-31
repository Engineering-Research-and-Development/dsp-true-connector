package it.eng.datatransfer.service;

import it.eng.tools.model.dashboard.KeyCount;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
    @DisplayName("getSnapshotMetrics should derive all transfer metrics from one aggregation snapshot")
    void getSnapshotMetrics_shouldDeriveAllTransferMetricsFromOneAggregationSnapshot() {
        doReturn(aggregationResults(List.of(
                new Document("countsByState", List.of(
                        new Document("key", "STARTED").append("count", 2L),
                        new Document("key", "COMPLETED").append("count", 1L)
                ))
                        .append("countsByRoleAndState", List.of(
                                new Document("key", "consumer:STARTED").append("count", 2L),
                                new Document("key", "provider:COMPLETED").append("count", 1L)
                        ))
                        .append("countsByFormat", List.of(
                                new Document("key", "HTTP_PULL").append("count", 2L),
                                new Document("key", "HTTP_PUSH").append("count", 1L)
                        ))
                        .append("countsByDownloadFlag", List.of(
                                new Document("key", "DOWNLOADED_TRUE").append("count", 1L),
                                new Document("key", "DOWNLOADED_FALSE").append("count", 2L),
                                new Document("key", "DOWNLOAD_IN_PROGRESS_TRUE").append("count", 1L),
                                new Document("key", "DOWNLOAD_IN_PROGRESS_FALSE").append("count", 2L)
                        ))
                        .append("total", List.of(new Document("count", 3L)))
        )))
                .when(mongoTemplate)
                .aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class));

        TransferSnapshotMetrics metrics = transferMetricsService.getSnapshotMetrics("tenant-a");

        assertIterableEquals(List.of(
                new KeyCount("STARTED", 2L),
                new KeyCount("COMPLETED", 1L)
        ), metrics.countsByState());
        assertIterableEquals(List.of(
                new KeyCount("consumer:STARTED", 2L),
                new KeyCount("provider:COMPLETED", 1L)
        ), metrics.countsByRoleAndState());
        assertIterableEquals(List.of(
                new KeyCount("HTTP_PULL", 2L),
                new KeyCount("HTTP_PUSH", 1L)
        ), metrics.countsByFormat());
        assertIterableEquals(List.of(
                new KeyCount("DOWNLOADED_FALSE", 2L),
                new KeyCount("DOWNLOAD_IN_PROGRESS_FALSE", 2L),
                new KeyCount("DOWNLOADED_TRUE", 1L),
                new KeyCount("DOWNLOAD_IN_PROGRESS_TRUE", 1L)
        ), metrics.countsByDownloadFlag());
        assertEquals(3L, metrics.total());

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

        assertIterableEquals(List.of(), metrics.countsByState());
        assertIterableEquals(List.of(), metrics.countsByRoleAndState());
        assertIterableEquals(List.of(), metrics.countsByFormat());
        assertIterableEquals(List.of(), metrics.countsByDownloadFlag());
        assertEquals(0L, metrics.total());
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
