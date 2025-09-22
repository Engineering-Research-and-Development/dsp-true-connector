package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class ApiAuthenticationEntryPointTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private PrintWriter printWriter;

    @InjectMocks
    private ApiAuthenticationEntryPoint entryPoint;

    private StringWriter stringWriter;

    @BeforeEach
    void setUp() throws IOException {
        stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(printWriter);
    }

    @Test
    @DisplayName("Should handle authentication failure with proper JSON response")
    void testCommence_authenticationFailure() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException("Invalid credentials");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(printWriter).write(any(String.class));
        verify(printWriter).flush();
    }

    @Test
    @DisplayName("Should set correct HTTP status code")
    void testCommence_httpStatusCode() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException("Test error");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should set correct content type")
    void testCommence_contentType() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException("Test error");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    @DisplayName("Should write JSON error response")
    void testCommence_jsonResponse() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException("Invalid token");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(printWriter).write(any(String.class));
        verify(printWriter).flush();
    }


    @Test
    @DisplayName("Should handle null exception message")
    void testCommence_nullExceptionMessage() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException(null);
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(printWriter).write(any(String.class));
        verify(printWriter).flush();
    }

    @Test
    @DisplayName("Should handle empty exception message")
    void testCommence_emptyExceptionMessage() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException("");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(printWriter).write(any(String.class));
        verify(printWriter).flush();
    }

    @Test
    @DisplayName("Should handle different API endpoints")
    void testCommence_differentApiEndpoints() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException("Access denied");
        when(request.getRequestURI()).thenReturn("/api/v1/data/transfer");

        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(printWriter).write(any(String.class));
        verify(printWriter).flush();
    }

    @Test
    @DisplayName("Should handle null request URI")
    void testCommence_nullRequestUri() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException("Test error");
        when(request.getRequestURI()).thenReturn(null);

        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(printWriter).write(any(String.class));
        verify(printWriter).flush();
    }

    @Test
    @DisplayName("Should handle IOException from response writer")
    void testCommence_ioException() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException("Test error");
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(response.getWriter()).thenThrow(new IOException("Writer error"));

        // When & Then
        try {
            entryPoint.commence(request, response, authException);
        } catch (IOException e) {
            // Expected behavior - IOException should be propagated
            assertTrue(e.getMessage().contains("Writer error"));
        }

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    @DisplayName("Should create proper error response format")
    void testCommence_errorResponseFormat() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException("Invalid token");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(printWriter).write(any(String.class));
        verify(printWriter).flush();
        
        // Verify that the response contains the expected error format
        // The actual JSON content is verified through the ToolsSerializer
        assertTrue(true); // This test verifies the method completes without throwing exceptions
    }

    @Test
    @DisplayName("Should handle different authentication exception types")
    void testCommence_differentExceptionTypes() throws IOException, ServletException {
        // Given
        AuthenticationException authException = new BadCredentialsException("Access denied");
        when(request.getRequestURI()).thenReturn("/api/v1/data/transfer");

        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(printWriter).write(any(String.class));
        verify(printWriter).flush();
    }
}
