package it.eng.tools.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that ensures every request has a correlation ID for distributed tracing.
 * The correlation ID is extracted from the X-Correlation-Id header if present,
 * or generated as a new UUID if absent. The ID is stored in the MDC (Mapped Diagnostic Context)
 * for logging and propagated to downstream services via the X-Correlation-Id header.
 */
@Component
@Slf4j
@Order(1) // Execute early in the filter chain
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Header name for correlation ID.
     */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /**
     * MDC key for correlation ID.
     */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Extract correlation ID from request header or generate a new one
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);

            if (correlationId == null || correlationId.isBlank()) {
                correlationId = generateCorrelationId();
                log.debug("Generated new correlation ID: {}", correlationId);
            } else {
                log.debug("Using existing correlation ID from header: {}", correlationId);
            }

            // Store correlation ID in MDC for logging
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

            // Add correlation ID to response header for traceability
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            log.debug("Processing request with correlation ID: {}", correlationId);

            // Continue with the filter chain
            filterChain.doFilter(request, response);
        } finally {
            // Clear MDC to prevent memory leaks and thread pollution
            MDC.remove(CORRELATION_ID_MDC_KEY);
            log.debug("Cleared correlation ID from MDC");
        }
    }

    /**
     * Generates a new correlation ID using UUID.
     *
     * @return A new correlation ID as a string
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Gets the current correlation ID from MDC.
     *
     * @return The current correlation ID or null if not set
     */
    public static String getCurrentCorrelationId() {
        return MDC.get(CORRELATION_ID_MDC_KEY);
    }
}

