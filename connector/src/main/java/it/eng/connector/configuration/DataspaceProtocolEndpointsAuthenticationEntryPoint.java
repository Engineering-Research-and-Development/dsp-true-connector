package it.eng.connector.configuration;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component("delegatedAuthenticationEntryPoint")
public class DataspaceProtocolEndpointsAuthenticationEntryPoint implements AuthenticationEntryPoint {

	@Autowired
	private DataspaceProtocolEndpointsExceptionHandler exceptionHandler;

	@Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) 
      throws IOException, ServletException {
        
        // Set response status and content type
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        // Create error response using the exception handler
        WebRequest webRequest = new ServletWebRequest(request);
        String uri = request.getRequestURI();
        JsonNode errorResponse = exceptionHandler.createErrorResponse(authException, webRequest, uri);
        
        // Write JSON response
        response.getWriter().write(errorResponse.toString());
        response.getWriter().flush();
    }
}
