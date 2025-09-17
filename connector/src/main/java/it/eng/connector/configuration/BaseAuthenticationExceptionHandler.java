package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

/**
 * Base exception handler for authentication exceptions.
 * Provides common authentication exception handling logic that can be extended by specific handlers.
 */
@Slf4j
public abstract class BaseAuthenticationExceptionHandler {

    protected static final String EN = "en";
    protected static final String NOT_AUTH = "Not authorized";
    protected static final String NOT_AUTH_CODE = String.valueOf(HttpStatus.UNAUTHORIZED.value());

    /**
     * Handles authentication exceptions with a unified approach.
     * 
     * @param ex the authentication exception
     * @param request the web request
     * @return ResponseEntity with appropriate error response
     */
    @ExceptionHandler({AuthenticationException.class})
    @ResponseBody
    public ResponseEntity<JsonNode> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.debug("Handling authentication exception: {}", ex.getMessage());
        
        String uri = request.getDescription(false).replace("uri=", "");
        JsonNode errorResponse = createErrorResponse(ex, request, uri);
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
    }

    /**
     * Creates the appropriate error response based on the request context.
     * This method should be implemented by subclasses to provide specific response formatting.
     * 
     * @param ex the authentication exception
     * @param request the web request
     * @param uri the request URI
     * @return JsonNode representing the error response
     */
    protected abstract JsonNode createErrorResponse(AuthenticationException ex, WebRequest request, String uri);
}
