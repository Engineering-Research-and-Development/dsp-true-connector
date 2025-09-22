package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
@Slf4j
public class DataspaceProtocolEndpointsExceptionHandler extends BaseAuthenticationExceptionHandler {

    private final ProtocolErrorResponseFormatter errorResponseFormatter;

    public DataspaceProtocolEndpointsExceptionHandler() {
        this.errorResponseFormatter = new ProtocolErrorResponseFormatter();
    }

    @Override
    protected JsonNode createErrorResponse(AuthenticationException ex, WebRequest request, String uri) {
        // Skip API endpoints - they should be handled by API exception handler
        if (uri.contains("api/")) {
            return null;
        }
        
        return errorResponseFormatter.formatAuthenticationError(ex, request, uri);
    }
}
