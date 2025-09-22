package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Exception handler for API endpoints.
 * Handles authentication exceptions for /api/** endpoints with proper JSON responses.
 */
@ControllerAdvice
@Slf4j
public class ApiEndpointsExceptionHandler extends BaseAuthenticationExceptionHandler {

    private final ApiErrorResponseFormatter errorResponseFormatter;

    public ApiEndpointsExceptionHandler() {
        this.errorResponseFormatter = new ApiErrorResponseFormatter();
    }

    @Override
    protected JsonNode createErrorResponse(AuthenticationException ex, WebRequest request, String uri) {
        // Only handle API endpoints
        if (!uri.contains("api/")) {
            return null;
        }
        
        return errorResponseFormatter.formatAuthenticationError(ex, request, uri);
    }
}
