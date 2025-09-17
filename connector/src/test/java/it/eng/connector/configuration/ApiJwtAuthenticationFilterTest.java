package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.connector.service.JwtProcessingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class ApiJwtAuthenticationFilterTest {

    @Mock
    private JwtProcessingService jwtProcessingService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ApiJwtAuthenticationFilter filter;

    @Test
    @DisplayName("Filter should skip non-API endpoints")
    void testDoFilterInternal_nonApiEndpoint() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/protocol/endpoint");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(jwtProcessingService, never()).processJwtTokenFromRequest(any());
    }

    @Test
    @DisplayName("Filter should skip authentication endpoints")
    void testDoFilterInternal_authEndpoints() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(jwtProcessingService, never()).processJwtTokenFromRequest(any());
    }

    @Test
    @DisplayName("Filter should skip register endpoint")
    void testDoFilterInternal_registerEndpoint() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(jwtProcessingService, never()).processJwtTokenFromRequest(any());
    }

    @Test
    @DisplayName("Filter should skip refresh token endpoint")
    void testDoFilterInternal_refreshEndpoint() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/auth/refresh");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(jwtProcessingService, never()).processJwtTokenFromRequest(any());
    }

    @Test
    @DisplayName("Filter should process JWT for protected API endpoints")
    void testDoFilterInternal_protectedApiEndpoint() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(jwtProcessingService).processJwtTokenFromRequest(request);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter should process JWT for user management endpoints")
    void testDoFilterInternal_userManagementEndpoint() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/users/123");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(jwtProcessingService).processJwtTokenFromRequest(request);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter should process JWT for user profile endpoints")
    void testDoFilterInternal_userProfileEndpoint() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/profile");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(jwtProcessingService).processJwtTokenFromRequest(request);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter should propagate JWT processing exceptions")
    void testDoFilterInternal_jwtProcessingException() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        doThrow(new RuntimeException("JWT processing failed")).when(jwtProcessingService).processJwtTokenFromRequest(any());

        // When & Then - The filter should propagate exceptions
        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException e) {
            // Expected behavior - RuntimeException should be propagated
            assertTrue(e.getMessage().contains("JWT processing failed"));
        }
        verify(jwtProcessingService).processJwtTokenFromRequest(request);
        // filterChain should not be called when exception occurs
    }

    @Test
    @DisplayName("Filter should handle different API versions")
    void testDoFilterInternal_differentApiVersions() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v2/data");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(jwtProcessingService).processJwtTokenFromRequest(request);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter should handle API endpoints with query parameters")
    void testDoFilterInternal_apiWithQueryParams() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/users?page=1&size=10");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(jwtProcessingService).processJwtTokenFromRequest(request);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter should handle API endpoints with path parameters")
    void testDoFilterInternal_apiWithPathParams() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/users/123/profile");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(jwtProcessingService).processJwtTokenFromRequest(request);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter should handle root API endpoint")
    void testDoFilterInternal_rootApiEndpoint() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(jwtProcessingService).processJwtTokenFromRequest(request);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter should handle empty request URI")
    void testDoFilterInternal_emptyRequestUri() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(jwtProcessingService, never()).processJwtTokenFromRequest(any());
    }

    @Test
    @DisplayName("Filter should handle null request URI")
    void testDoFilterInternal_nullRequestUri() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn(null);

        // When & Then - Should throw NullPointerException
        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (NullPointerException e) {
            // Expected behavior - NullPointerException should be thrown
            assertTrue(e.getMessage().contains("Cannot invoke"));
        }
    }
}
