package it.eng.tools.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CorrelationIdFilter.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CorrelationIdFilter Tests")
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        // Ensure MDC is clean before each test
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        // Clean up MDC after each test
        MDC.clear();
    }

    @Test
    @DisplayName("Should generate new correlation ID when header is absent")
    void testGenerateNewCorrelationId_WhenHeaderAbsent() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        ArgumentCaptor<String> correlationIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), correlationIdCaptor.capture());

        String correlationId = correlationIdCaptor.getValue();
        assertNotNull(correlationId, "Correlation ID should not be null");
        assertFalse(correlationId.isBlank(), "Correlation ID should not be blank");

        // Verify it's a valid UUID format
        assertDoesNotThrow(() -> UUID.fromString(correlationId),
            "Correlation ID should be a valid UUID");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should use existing correlation ID from header")
    void testUseExistingCorrelationId_WhenHeaderPresent() throws ServletException, IOException {
        // Arrange
        String existingCorrelationId = UUID.randomUUID().toString();
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(existingCorrelationId);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should generate new correlation ID when header is blank")
    void testGenerateNewCorrelationId_WhenHeaderBlank() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("   ");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        ArgumentCaptor<String> correlationIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), correlationIdCaptor.capture());

        String correlationId = correlationIdCaptor.getValue();
        assertNotNull(correlationId, "Correlation ID should not be null");
        assertFalse(correlationId.isBlank(), "Correlation ID should not be blank");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should store correlation ID in MDC during request processing")
    void testStoreCorrelationIdInMdc() throws ServletException, IOException {
        // Arrange
        String expectedCorrelationId = UUID.randomUUID().toString();
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(expectedCorrelationId);

        // Act
        doAnswer(invocation -> {
            // Check MDC during filter chain execution
            String mdcCorrelationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
            assertEquals(expectedCorrelationId, mdcCorrelationId,
                "Correlation ID should be in MDC during request processing");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should clear correlation ID from MDC after request completes")
    void testClearMdcAfterRequest() throws ServletException, IOException {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(correlationId);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        String mdcCorrelationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        assertNull(mdcCorrelationId, "Correlation ID should be cleared from MDC after request");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should clear MDC even when filter chain throws exception")
    void testClearMdcOnException() throws ServletException, IOException {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(correlationId);
        doThrow(new ServletException("Test exception")).when(filterChain).doFilter(request, response);

        // Act & Assert
        assertThrows(ServletException.class, () ->
            filter.doFilterInternal(request, response, filterChain));

        // Verify MDC is still cleared
        String mdcCorrelationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        assertNull(mdcCorrelationId, "Correlation ID should be cleared from MDC even after exception");
    }

    @Test
    @DisplayName("Should propagate correlation ID to response header")
    void testPropagateCorrelationIdToResponseHeader() throws ServletException, IOException {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(correlationId);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
    }

    @Test
    @DisplayName("Should maintain same correlation ID across multiple filter invocations")
    void testSameCorrelationIdAcrossSteps() throws ServletException, IOException {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(correlationId);

        // Act - First invocation
        filter.doFilterInternal(request, response, filterChain);

        // Arrange - Second invocation with same correlation ID
        reset(response, filterChain);
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(correlationId);

        // Act - Second invocation
        filter.doFilterInternal(request, response, filterChain);

        // Assert - Both invocations should use the same correlation ID
        verify(response, times(1)).setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
    }

    @Test
    @DisplayName("Should generate different correlation IDs for different requests without header")
    void testDifferentCorrelationIdsForDifferentRequests() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

        // Act - First request
        filter.doFilterInternal(request, response, filterChain);
        ArgumentCaptor<String> firstCorrelationIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), firstCorrelationIdCaptor.capture());
        String firstCorrelationId = firstCorrelationIdCaptor.getValue();

        // Reset mocks for second request
        reset(response, filterChain);
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

        // Act - Second request
        filter.doFilterInternal(request, response, filterChain);
        ArgumentCaptor<String> secondCorrelationIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), secondCorrelationIdCaptor.capture());
        String secondCorrelationId = secondCorrelationIdCaptor.getValue();

        // Assert - Should generate different correlation IDs
        assertNotEquals(firstCorrelationId, secondCorrelationId,
            "Different requests should have different correlation IDs");
    }

    @Test
    @DisplayName("Should provide static method to get current correlation ID from MDC")
    void testGetCurrentCorrelationId() {
        // Arrange
        String expectedCorrelationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, expectedCorrelationId);

        // Act
        String actualCorrelationId = CorrelationIdFilter.getCurrentCorrelationId();

        // Assert
        assertEquals(expectedCorrelationId, actualCorrelationId,
            "Static method should return correlation ID from MDC");

        // Clean up
        MDC.clear();
    }

    @Test
    @DisplayName("Should return null from static method when no correlation ID in MDC")
    void testGetCurrentCorrelationId_WhenNoCorrelationIdInMdc() {
        // Arrange - MDC is already clear from setUp()

        // Act
        String correlationId = CorrelationIdFilter.getCurrentCorrelationId();

        // Assert
        assertNull(correlationId, "Should return null when no correlation ID in MDC");
    }
}

