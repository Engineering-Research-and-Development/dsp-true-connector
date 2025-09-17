package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.context.request.WebRequest;

/**
 * Error response formatter for API endpoints.
 * Formats authentication errors in a consistent API response format.
 */
public class ApiErrorResponseFormatter implements ErrorResponseFormatter {

    @Override
    public JsonNode formatAuthenticationError(AuthenticationException ex, WebRequest request, String uri) {
        // Use GenericApiResponse for consistent API error format
        GenericApiResponse<Object> apiResponse = GenericApiResponse.error(
            ex.getMessage() != null ? ex.getMessage() : "Authentication failed"
        );
        
        return ToolsSerializer.serializePlainJsonNode(apiResponse);
    }
}
