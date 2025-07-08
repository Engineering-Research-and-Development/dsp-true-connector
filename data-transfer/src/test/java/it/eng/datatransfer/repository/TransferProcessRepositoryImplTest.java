package it.eng.datatransfer.repository;

import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.tools.model.IConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferProcessRepositoryImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Captor
    private ArgumentCaptor<Query> queryCaptor;

    private TransferProcessRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new TransferProcessRepositoryImpl(mongoTemplate);
    }

    @Test
    @DisplayName("Find with dynamic filters - string values")
    void findWithDynamicFilters_stringValues() {
        Map<String, Object> filters = Map.of(
            "state", TransferState.STARTED.name(),
            "role", IConstants.ROLE_CONSUMER,
            "datasetId", DataTranferMockObjectUtil.DATASET_ID
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        // Verify that all string filters were added as criteria
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("state"));
        assertTrue(queryString.contains("role"));
        assertTrue(queryString.contains("datasetId"));
    }

    @Test
    @DisplayName("Find with dynamic filters - boolean values")
    void findWithDynamicFilters_booleanValues() {
        Map<String, Object> filters = Map.of(
            "isDownloaded", true,
            "role", IConstants.ROLE_CONSUMER
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("isDownloaded"));
        assertTrue(queryString.contains("role"));
    }

    @Test
    @DisplayName("Find with dynamic filters - datetime values")
    void findWithDynamicFilters_datetimeValues() {
        Instant testInstant = Instant.parse("2024-01-01T10:00:00Z");
        Map<String, Object> filters = Map.of(
            "created", testInstant,
            "state", TransferState.COMPLETED.name()
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("created"));
        assertTrue(queryString.contains("state"));
    }

    @Test
    @DisplayName("Find with dynamic filters - number values")
    void findWithDynamicFilters_numberValues() {
        Map<String, Object> filters = Map.of(
            "version", 123L,
            "count", 45.67,
            "role", IConstants.ROLE_PROVIDER
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("version"));
        assertTrue(queryString.contains("count"));
        assertTrue(queryString.contains("role"));
    }

    @Test
    @DisplayName("Find with dynamic filters - collection values (IN query)")
    void findWithDynamicFilters_collectionValues() {
        Map<String, Object> filters = Map.of(
            "state", Arrays.asList(TransferState.STARTED.name(), TransferState.COMPLETED.name()),
            "role", IConstants.ROLE_CONSUMER
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(
                    DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED,
                    DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER
                ));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(2, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("state"));
        assertTrue(queryString.contains("role"));
    }

    @Test
    @DisplayName("Find with dynamic filters - range values (Map)")
    void findWithDynamicFilters_rangeValues() {
        Map<String, Object> dateRange = Map.of(
            "gte", Instant.parse("2024-01-01T00:00:00Z"),
            "lte", Instant.parse("2024-01-31T23:59:59Z")
        );
        
        Map<String, Object> filters = Map.of(
            "created", dateRange,
            "role", IConstants.ROLE_CONSUMER
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("created"));
        assertTrue(queryString.contains("role"));
    }

    @Test
    @DisplayName("Find with dynamic filters - mixed types")
    void findWithDynamicFilters_mixedTypes() {
        Map<String, Object> filters = Map.of(
            "state", TransferState.STARTED.name(), // String
            "isDownloaded", true, // Boolean
            "version", 123L, // Number
            "created", Instant.parse("2024-01-01T10:00:00Z") // Instant
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        // Verify all criteria were added
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("state"));
        assertTrue(queryString.contains("isDownloaded"));
        assertTrue(queryString.contains("version"));
        assertTrue(queryString.contains("created"));
    }

    @Test
    @DisplayName("Find with dynamic filters - empty filters")
    void findWithDynamicFilters_emptyFilters() {
        Map<String, Object> filters = new HashMap<>();

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(
                    DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED,
                    DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER
                ));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(2, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        // Should create an empty query (no criteria)
        assertEquals(0, capturedQuery.getQueryObject().size());
    }

    @Test
    @DisplayName("Find with dynamic filters - single filter")
    void findWithDynamicFilters_singleFilter() {
        Map<String, Object> filters = Map.of("role", IConstants.ROLE_PROVIDER);

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("role"));
    }

    @Test
    @DisplayName("Find with dynamic filters - range operators")
    void findWithDynamicFilters_rangeOperators() {
        Map<String, Object> versionRange = Map.of(
            "gte", 1L,
            "lt", 10L
        );
        
        Map<String, Object> filters = Map.of(
            "version", versionRange,
            "state", TransferState.STARTED.name()
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("version"));
        assertTrue(queryString.contains("state"));
    }

    @Test
    @DisplayName("Find with dynamic filters - alternative range keywords")
    void findWithDynamicFilters_alternativeRangeKeywords() {
        Map<String, Object> dateRange = Map.of(
            "from", Instant.parse("2024-01-01T00:00:00Z"),
            "to", Instant.parse("2024-01-31T23:59:59Z")
        );
        
        Map<String, Object> filters = Map.of(
            "created", dateRange
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("created"));
    }

    @Test
    @DisplayName("Find with dynamic filters - no results")
    void findWithDynamicFilters_noResults() {
        Map<String, Object> filters = Map.of(
            "state", "NON_EXISTENT_STATE",
            "role", IConstants.ROLE_CONSUMER
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Collections.emptyList());

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(mongoTemplate).find(any(Query.class), eq(TransferProcess.class));
    }

    @Test
    @DisplayName("Find with dynamic filters - complex nested query")
    void findWithDynamicFilters_complexNestedQuery() {
        Map<String, Object> dateRange = Map.of(
            "gte", Instant.parse("2024-01-01T00:00:00Z"),
            "lte", Instant.parse("2024-12-31T23:59:59Z")
        );
        
        Map<String, Object> filters = Map.of(
            "state", Arrays.asList(TransferState.STARTED.name(), TransferState.COMPLETED.name()),
            "role", IConstants.ROLE_CONSUMER,
            "isDownloaded", true,
            "created", dateRange,
            "datasetId", DataTranferMockObjectUtil.DATASET_ID
        );

        when(mongoTemplate.find(any(Query.class), eq(TransferProcess.class)))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<TransferProcess> result = repository.findWithDynamicFilters(filters);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TransferProcess.class));
        Query capturedQuery = queryCaptor.getValue();
        
        // Verify all criteria types were added
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("state"));
        assertTrue(queryString.contains("role"));
        assertTrue(queryString.contains("isDownloaded"));
        assertTrue(queryString.contains("created"));
        assertTrue(queryString.contains("datasetId"));
    }
} 