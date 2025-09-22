package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.context.request.WebRequest;

/**
 * Interface for formatting error responses.
 * Allows different response formats for API vs Protocol endpoints.
 */
public interface ErrorResponseFormatter {

    /**
     * Formats an authentication error response.
     * 
     * @param ex the authentication exception
     * @param request the web request
     * @param uri the request URI
     * @return JsonNode representing the formatted error response
     */
    JsonNode formatAuthenticationError(AuthenticationException ex, WebRequest request, String uri);
}
