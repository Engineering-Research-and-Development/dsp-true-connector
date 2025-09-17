package it.eng.connector.configuration;

import it.eng.connector.service.JwtProcessingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter for API endpoints.
 * This filter handles JWT tokens for /api/** endpoints and works alongside the existing DAPS JWT filter.
 */
@Slf4j
public class ApiJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProcessingService jwtProcessingService;

    public ApiJwtAuthenticationFilter(JwtProcessingService jwtProcessingService) {
        this.jwtProcessingService = jwtProcessingService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only process /api/** endpoints (API endpoints)
        // Non-API endpoints are handled by JwtAuthenticationFilter for protocol authentication
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip authentication endpoints (login, register, refresh) - these don't require authentication
        if (request.getRequestURI().startsWith("/api/v1/auth/login") ||
            request.getRequestURI().startsWith("/api/v1/auth/register") ||
            request.getRequestURI().startsWith("/api/v1/auth/refresh")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Process JWT token using the centralized service
        jwtProcessingService.processJwtTokenFromRequest(request);

        filterChain.doFilter(request, response);
    }
}
