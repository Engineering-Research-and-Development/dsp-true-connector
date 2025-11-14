package it.eng.tools.client.rest;

import it.eng.tools.filter.CorrelationIdFilter;
import okhttp3.*;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CorrelationIdInterceptor.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CorrelationIdInterceptor Tests")
class CorrelationIdInterceptorTest {

    @Mock
    private Interceptor.Chain chain;

    @Mock
    private Response response;

    private CorrelationIdInterceptor interceptor;
    private Request originalRequest;

    @BeforeEach
    void setUp() {
        interceptor = new CorrelationIdInterceptor();
        originalRequest = new Request.Builder()
                .url("https://example.com/api/test")
                .build();

        // Ensure MDC is clean before each test
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        // Clean up MDC after each test
        MDC.clear();
    }

    @Test
    @DisplayName("Should add correlation ID header when present in MDC")
    void testAddCorrelationIdHeader_WhenPresentInMdc() throws IOException {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);

        when(chain.request()).thenReturn(originalRequest);
        when(chain.proceed(any(Request.class))).thenReturn(response);

        // Act
        Response result = interceptor.intercept(chain);

        // Assert
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(chain).proceed(requestCaptor.capture());

        Request modifiedRequest = requestCaptor.getValue();
        String headerValue = modifiedRequest.header(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertEquals(correlationId, headerValue,
            "Request should have correlation ID header with value from MDC");
        assertSame(response, result, "Should return the response from chain");
    }

    @Test
    @DisplayName("Should not modify request when correlation ID absent from MDC")
    void testNoModification_WhenCorrelationIdAbsentFromMdc() throws IOException {
        // Arrange - MDC is clean from setUp()
        when(chain.request()).thenReturn(originalRequest);
        when(chain.proceed(any(Request.class))).thenReturn(response);

        // Act
        Response result = interceptor.intercept(chain);

        // Assert
        verify(chain).proceed(originalRequest);
        assertSame(response, result, "Should return the response from chain");
    }

    @Test
    @DisplayName("Should not add header when correlation ID in MDC is blank")
    void testNoHeader_WhenCorrelationIdBlank() throws IOException {
        // Arrange
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "   ");

        when(chain.request()).thenReturn(originalRequest);
        when(chain.proceed(any(Request.class))).thenReturn(response);

        // Act
        Response result = interceptor.intercept(chain);

        // Assert
        verify(chain).proceed(originalRequest);
        assertSame(response, result, "Should return the response from chain");
    }

    @Test
    @DisplayName("Should propagate correlation ID to multiple outbound requests")
    void testPropagateToMultipleRequests() throws IOException {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);

        Request secondRequest = new Request.Builder()
                .url("https://example.com/api/another")
                .build();

        when(chain.request()).thenReturn(originalRequest).thenReturn(secondRequest);
        when(chain.proceed(any(Request.class))).thenReturn(response);

        // Act - First request
        interceptor.intercept(chain);

        // Act - Second request
        when(chain.request()).thenReturn(secondRequest);
        interceptor.intercept(chain);

        // Assert
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(chain, times(2)).proceed(requestCaptor.capture());

        for (Request request : requestCaptor.getAllValues()) {
            String headerValue = request.header(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertEquals(correlationId, headerValue,
                "All requests should have the same correlation ID");
        }
    }

    @Test
    @DisplayName("Should preserve existing headers while adding correlation ID")
    void testPreserveExistingHeaders() throws IOException {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);

        Request requestWithHeaders = new Request.Builder()
                .url("https://example.com/api/test")
                .addHeader("Authorization", "Bearer token123")
                .addHeader("Content-Type", "application/json")
                .build();

        when(chain.request()).thenReturn(requestWithHeaders);
        when(chain.proceed(any(Request.class))).thenReturn(response);

        // Act
        interceptor.intercept(chain);

        // Assert
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(chain).proceed(requestCaptor.capture());

        Request modifiedRequest = requestCaptor.getValue();
        assertEquals(correlationId, modifiedRequest.header(CorrelationIdFilter.CORRELATION_ID_HEADER),
            "Correlation ID header should be added");
        assertEquals("Bearer token123", modifiedRequest.header("Authorization"),
            "Authorization header should be preserved");
        assertEquals("application/json", modifiedRequest.header("Content-Type"),
            "Content-Type header should be preserved");
    }

    @Test
    @DisplayName("Should override existing correlation ID header with MDC value")
    void testOverrideExistingCorrelationIdHeader() throws IOException {
        // Arrange
        String oldCorrelationId = UUID.randomUUID().toString();
        String newCorrelationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, newCorrelationId);

        Request requestWithOldCorrelationId = new Request.Builder()
                .url("https://example.com/api/test")
                .addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, oldCorrelationId)
                .build();

        when(chain.request()).thenReturn(requestWithOldCorrelationId);
        when(chain.proceed(any(Request.class))).thenReturn(response);

        // Act
        interceptor.intercept(chain);

        // Assert
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(chain).proceed(requestCaptor.capture());

        Request modifiedRequest = requestCaptor.getValue();
        String headerValue = modifiedRequest.header(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertEquals(newCorrelationId, headerValue,
            "Should use correlation ID from MDC, not the existing header");
        assertNotEquals(oldCorrelationId, headerValue,
            "Old correlation ID should be replaced");
    }

    @Test
    @DisplayName("Should handle chain proceed throwing IOException")
    void testHandleChainException() throws IOException {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);

        when(chain.request()).thenReturn(originalRequest);
        IOException expectedException = new IOException("Network error");
        when(chain.proceed(any(Request.class))).thenThrow(expectedException);

        // Act & Assert
        IOException actualException = assertThrows(IOException.class,
            () -> interceptor.intercept(chain));

        assertSame(expectedException, actualException,
            "Should propagate IOException from chain");
    }

    @Test
    @DisplayName("Should work with different HTTP methods")
    void testWorkWithDifferentHttpMethods() throws IOException {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);

        Request postRequest = new Request.Builder()
                .url("https://example.com/api/test")
                .post(RequestBody.create("{}", MediaType.parse("application/json")))
                .build();

        when(chain.request()).thenReturn(postRequest);
        when(chain.proceed(any(Request.class))).thenReturn(response);

        // Act
        interceptor.intercept(chain);

        // Assert
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(chain).proceed(requestCaptor.capture());

        Request modifiedRequest = requestCaptor.getValue();
        assertEquals(correlationId, modifiedRequest.header(CorrelationIdFilter.CORRELATION_ID_HEADER),
            "Correlation ID should be added to POST request");
        assertEquals("POST", modifiedRequest.method(),
            "HTTP method should be preserved");
    }
}

