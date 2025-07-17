package it.eng.tools.repository;

import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.IConstants;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenericDynamicFilterRepositoryImplTest {

    // Test entity for generic repository testing
    @Data
    private static class TestEntity {
        private String id;
        private String stringField;
        private Boolean booleanField;
        private Integer numberField;
        private Instant timestampField;
        private LocalDateTime localDateTimeField;
        private LocalDate localDateField;
        private List<String> listField;
        private TestEnum enumField;
    }

    private enum TestEnum {
        VALUE_1, VALUE_2
    }

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private Pageable pageable;

    @Captor
    private ArgumentCaptor<Query> queryCaptor;

    private GenericDynamicFilterRepositoryImpl<TestEntity, String> repository;

    @BeforeEach
    void setUp() {
        repository = new GenericDynamicFilterRepositoryImpl<>(mongoTemplate);

        // Configure mock Pageable
        when(pageable.getSort()).thenReturn(Sort.by(Sort.Direction.DESC, "timestamp"));
    }

    @Test
    @DisplayName("Find with dynamic filters - string values")
    void findWithDynamicFilters_stringValues() {
        Map<String, Object> filters = Map.of(
                "eventType", AuditEventType.APPLICATION_START.name(),
                "user", "testUser",
                "description", "test description"
        );

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(List.of(mockEvent));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
        Query capturedQuery = queryCaptor.getValue();
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("eventType"));
        assertTrue(queryString.contains("user"));
        assertTrue(queryString.contains("description"));
    }

    @Test
    @DisplayName("Find with dynamic filters - boolean values")
    void findWithDynamicFilters_booleanValues() {
        Map<String, Object> filters = Map.of(
                "isDownloaded", true,
                "role", IConstants.ROLE_CONSUMER
        );

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(List.of(mockEvent));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
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
                "state", TestEnum.VALUE_1
        );

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(List.of(mockEvent));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
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

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(List.of(mockEvent));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
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
                "state", Arrays.asList(TestEnum.VALUE_2, TestEnum.VALUE_1),
                "role", IConstants.ROLE_CONSUMER
        );

        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(Arrays.asList(
                        createMockEntity(),
                        createMockEntity()
                ));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(2, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
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

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(List.of(mockEvent));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
        Query capturedQuery = queryCaptor.getValue();

        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("created"));
        assertTrue(queryString.contains("role"));
    }

    @Test
    @DisplayName("Find with dynamic filters - mixed types")
    void findWithDynamicFilters_mixedTypes() {
        Map<String, Object> filters = Map.of(
                "state", TestEnum.VALUE_2, // String
                "isDownloaded", true, // Boolean
                "version", 123L, // Number
                "created", Instant.parse("2024-01-01T10:00:00Z") // Instant
        );

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(List.of(mockEvent));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
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

        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(Arrays.asList(
                        createMockEntity(),
                        createMockEntity()
                ));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(2, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
        Query capturedQuery = queryCaptor.getValue();

        // Should create an empty query (no criteria)
        assertEquals(0, capturedQuery.getQueryObject().size());
    }

    @Test
    @DisplayName("Find with dynamic filters - single filter")
    void findWithDynamicFilters_singleFilter() {
        Map<String, Object> filters = Map.of("role", IConstants.ROLE_PROVIDER);

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(List.of(mockEvent));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
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
                "state", TestEnum.VALUE_1
        );

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(List.of(mockEvent));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
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

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(List.of(mockEvent));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
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

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(Collections.emptyList());

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    @DisplayName("Find with dynamic filters - complex nested query")
    void findWithDynamicFilters_complexNestedQuery() {
        Map<String, Object> dateRange = Map.of(
                "gte", Instant.parse("2024-01-01T00:00:00Z"),
                "lte", Instant.parse("2024-12-31T23:59:59Z")
        );

        Map<String, Object> filters = Map.of(
                "state", Arrays.asList(TestEnum.VALUE_1, TestEnum.VALUE_2),
                "role", IConstants.ROLE_CONSUMER,
                "isDownloaded", true,
                "created", dateRange,
                "datasetId", UUID.randomUUID().toString()
        );

        TestEntity mockEvent = createMockEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class)))
                .thenReturn(List.of(mockEvent));

        Page<TestEntity> result = repository.findWithDynamicFilters(filters, TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfElements());

        verify(mongoTemplate).find(queryCaptor.capture(), eq(TestEntity.class));
        Query capturedQuery = queryCaptor.getValue();

        // Verify all criteria types were added
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("state"));
        assertTrue(queryString.contains("role"));
        assertTrue(queryString.contains("isDownloaded"));
        assertTrue(queryString.contains("created"));
        assertTrue(queryString.contains("datasetId"));
    }

    private TestEntity createMockEntity() {
        TestEntity entity = new TestEntity();
        entity.setId("test-id");
        entity.setStringField("test value");
        entity.setBooleanField(true);
        entity.setNumberField(123);
        entity.setTimestampField(Instant.now());
        entity.setEnumField(TestEnum.VALUE_1);
        return entity;
    }
}