package it.eng.connector.configuration;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * API Authentication Entry Point that returns JSON responses for API endpoints.
 * This entry point handles authentication failures for /api/** endpoints by returning
 * properly formatted JSON error responses with 401 status code and application/json content type.
 */
@Component("apiAuthenticationEntryPoint")
@Slf4j
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, 
                        AuthenticationException authException) throws IOException, ServletException {
        
        log.debug("Authentication failed for API endpoint: {} - {}", request.getRequestURI(), authException.getMessage());
        
        // Set response status and content type for JSON response
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        // Create error response using the same format as other API responses
        GenericApiResponse<Object> errorResponse = GenericApiResponse.error("Unauthorized: " + authException.getMessage());
        
        // Write JSON response using ToolsSerializer
        response.getWriter().write(ToolsSerializer.serializePlain(errorResponse));
        response.getWriter().flush();
    }
}
