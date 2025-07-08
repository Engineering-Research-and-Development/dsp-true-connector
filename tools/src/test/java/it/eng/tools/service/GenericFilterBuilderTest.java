package it.eng.tools.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GenericFilterBuilderTest {

    private GenericFilterBuilder filterBuilder;

    @BeforeEach
    void setUp() {
        filterBuilder = new GenericFilterBuilder();
    }

    @Test
    @DisplayName("Build filters from request with string parameters")
    void buildFromRequest_stringParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", "STARTED");
        request.setParameter("role", "CONSUMER");
        request.setParameter("datasetId", "dataset123");

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(3, filters.size());
        assertEquals("STARTED", filters.get("state"));
        assertEquals("CONSUMER", filters.get("role"));
        assertEquals("dataset123", filters.get("datasetId"));
    }

    @Test
    @DisplayName("Build filters from request with boolean and numeric parameters")
    void buildFromRequest_booleanParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("isDownloaded", "true");
        request.setParameter("isActive", "false");
        request.setParameter("flag1", "1");
        request.setParameter("flag2", "0");
        request.setParameter("option1", "yes");
        request.setParameter("option2", "no");

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(6, filters.size());
        assertEquals(true, filters.get("isDownloaded"));  // "true" → boolean
        assertEquals(false, filters.get("isActive"));     // "false" → boolean
        assertEquals(1L, filters.get("flag1"));           // "1" → number (not boolean)
        assertEquals(0L, filters.get("flag2"));           // "0" → number (not boolean)
        assertEquals(true, filters.get("option1"));       // "yes" → boolean
        assertEquals(false, filters.get("option2"));      // "no" → boolean
    }

    @Test
    @DisplayName("Build filters from request with datetime parameters")
    void buildFromRequest_datetimeParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("created", "2024-01-01T10:00:00Z");
        request.setParameter("modified", "2024-01-01");
        request.setParameter("timestamp", "1640995200000"); // milliseconds

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(2, filters.size()); // timestamp is excluded
        assertInstanceOf(Instant.class, filters.get("created"));
        assertInstanceOf(Instant.class, filters.get("modified"));
        
        Instant expectedCreated = Instant.parse("2024-01-01T10:00:00Z");
        assertEquals(expectedCreated, filters.get("created"));
    }

    @Test
    @DisplayName("Build filters from request with number parameters")
    void buildFromRequest_numberParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("count", "123");
        request.setParameter("price", "45.67");
        request.setParameter("negative", "-42");
        request.setParameter("scientific", "1.23e+10");

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(4, filters.size());
        assertEquals(123L, filters.get("count"));
        assertEquals(45.67, filters.get("price"));
        assertEquals(-42L, filters.get("negative"));
        assertEquals(1.23e+10, filters.get("scientific"));
    }

    @Test
    @DisplayName("Build filters from request with multiple values")
    void buildFromRequest_multipleValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", new String[]{"STARTED", "COMPLETED"});
        request.setParameter("role", "CONSUMER");

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(2, filters.size());
        assertEquals("CONSUMER", filters.get("role"));
        
        Object stateValue = filters.get("state");
        assertInstanceOf(List.class, stateValue);
        @SuppressWarnings("unchecked")
        List<String> stateList = (List<String>) stateValue;
        assertEquals(2, stateList.size());
        assertTrue(stateList.containsAll(Arrays.asList("STARTED", "COMPLETED")));
    }

    @ParameterizedTest
    @DisplayName("Filter out null and empty parameters")
    @ValueSource(strings = {"", "   ", "null", "undefined", "nil", "none"})
    void buildFromRequest_filterOutInvalidValues(String invalidValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("validParam", "validValue");
        request.setParameter("invalidParam", invalidValue);

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(1, filters.size());
        assertEquals("validValue", filters.get("validParam"));
        assertFalse(filters.containsKey("invalidParam"));
    }

    @ParameterizedTest
    @DisplayName("Filter out excluded parameters")
    @ValueSource(strings = {"page", "size", "sort", "_", "timestamp"})
    void buildFromRequest_filterOutExcludedParameters(String excludedParam) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("validParam", "validValue");
        request.setParameter(excludedParam, "someValue");

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(1, filters.size());
        assertEquals("validValue", filters.get("validParam"));
        assertFalse(filters.containsKey(excludedParam));
    }

    @ParameterizedTest
    @DisplayName("Filter out suspicious patterns")
    @MethodSource("suspiciousPatterns")
    void buildFromRequest_filterOutSuspiciousPatterns(String suspiciousValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("validParam", "validValue");
        request.setParameter("suspiciousParam", suspiciousValue);

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(1, filters.size());
        assertEquals("validValue", filters.get("validParam"));
        assertFalse(filters.containsKey("suspiciousParam"));
    }

    @ParameterizedTest
    @DisplayName("Filter out reserved MongoDB field names")
    @ValueSource(strings = {"$where", "$gt", "_id", "$regex"})
    void buildFromRequest_filterOutReservedFieldNames(String reservedFieldName) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("validParam", "validValue");
        request.setParameter(reservedFieldName, "someValue");

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(1, filters.size());
        assertEquals("validValue", filters.get("validParam"));
        assertFalse(filters.containsKey(reservedFieldName));
    }

    @Test
    @DisplayName("Handle invalid datetime formats gracefully")
    void buildFromRequest_invalidDateTimeFormats() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("validParam", "validValue");
        request.setParameter("invalidDate1", "2024-02-30T10:00:00Z"); // Invalid date
        request.setParameter("invalidDate2", "2024-13-01T10:00:00Z"); // Invalid month
        request.setParameter("invalidDate3", "not-a-date");

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(2, filters.size()); // validParam + invalidDate3 (as string)
        assertEquals("validValue", filters.get("validParam"));
        assertEquals("not-a-date", filters.get("invalidDate3"));
        assertFalse(filters.containsKey("invalidDate1"));
        assertFalse(filters.containsKey("invalidDate2"));
    }

    @Test
    @DisplayName("Handle number overflow gracefully")
    void buildFromRequest_numberOverflow() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("validNumber", "123");
        request.setParameter("overflowNumber", "999999999999999999999"); // Too large
        request.setParameter("invalidNumber", "not-a-number");
        request.setParameter("specialNumber", "Infinity");

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(2, filters.size()); // validNumber + invalidNumber (as string)
        assertEquals(123L, filters.get("validNumber"));
        assertEquals("not-a-number", filters.get("invalidNumber"));
        assertFalse(filters.containsKey("overflowNumber"));
        assertFalse(filters.containsKey("specialNumber"));
    }

    @Test
    @DisplayName("Handle extreme date ranges")
    void buildFromRequest_extremeDateRanges() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("validDate", "2024-01-01T10:00:00Z");
        request.setParameter("tooEarly", "1969-01-01T10:00:00Z"); // Before 1970
        request.setParameter("tooLate", "2101-01-01T10:00:00Z"); // After 2100
        request.setParameter("yearZero", "0000-01-01T10:00:00Z"); // Year 0

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(1, filters.size());
        assertInstanceOf(Instant.class, filters.get("validDate"));
        assertFalse(filters.containsKey("tooEarly"));
        assertFalse(filters.containsKey("tooLate"));
        assertFalse(filters.containsKey("yearZero"));
    }

    @Test
    @DisplayName("Enforce parameter count limit")
    void buildFromRequest_parameterCountLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        // Add more than 50 parameters
        for (int i = 0; i < 60; i++) {
            request.setParameter("param" + i, "value" + i);
        }

        assertThrows(IllegalArgumentException.class, () -> filterBuilder.buildFromRequest(request));
    }

    @Test
    @DisplayName("Handle nested field names correctly")
    void buildFromRequest_nestedFieldNames() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("user.profile.name", "john");
        request.setParameter("metadata.tags", "tag1");
        request.setParameter("invalid.$field", "value"); // Should be rejected

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(2, filters.size());
        assertEquals("john", filters.get("user.profile.name"));
        assertEquals("tag1", filters.get("metadata.tags"));
        assertFalse(filters.containsKey("invalid.$field"));
    }

    @Test
    @DisplayName("Return empty map for request with no valid parameters")
    void buildFromRequest_noValidParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("page", "1"); // Excluded
        request.setParameter("size", "10"); // Excluded
        request.setParameter("", "empty"); // Invalid field name
        request.setParameter("suspicious", "$where malicious"); // Suspicious

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertTrue(filters.isEmpty());
    }

    @Test
    @DisplayName("Handle case insensitive boolean values")
    void buildFromRequest_caseInsensitiveBooleans() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("flag1", "TRUE");
        request.setParameter("flag2", "False");
        request.setParameter("flag3", "YES");
        request.setParameter("flag4", "no");

        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        assertEquals(4, filters.size());
        assertEquals(true, filters.get("flag1"));   // "TRUE" → boolean
        assertEquals(false, filters.get("flag2"));  // "False" → boolean
        assertEquals(true, filters.get("flag3"));   // "YES" → boolean  
        assertEquals(false, filters.get("flag4"));  // "no" → boolean
    }

    private static Stream<Arguments> suspiciousPatterns() {
        return Stream.of(
            Arguments.of("$where: malicious"),
            Arguments.of("javascript:alert('xss')"),
            Arguments.of("<script>alert('xss')</script>"),
            Arguments.of("DROP TABLE users"),
            Arguments.of("INSERT INTO users"),
            Arguments.of("DELETE FROM users"),
            Arguments.of("UPDATE SET password"),
            Arguments.of("UNION SELECT * FROM")
        );
    }
} 