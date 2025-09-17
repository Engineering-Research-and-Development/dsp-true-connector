package it.eng.connector.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/**
 * Utility class for extracting JWT tokens from HTTP requests.
 * Centralizes JWT token extraction logic to ensure consistency across the application.
 */
public final class JwtTokenExtractor {

    private JwtTokenExtractor() {
    }

    /**
     * Extracts the JWT token from the Authorization header.
     *
     * @param request the HTTP request
     * @return the JWT token without the "Bearer " prefix, or null if not found
     */
    public static String extractTokenFromHeader(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * Checks if the request contains a valid Bearer token in the Authorization header.
     *
     * @param request the HTTP request
     * @return true if a valid Bearer token is present, false otherwise
     */
    public static boolean hasValidBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        return StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ");
    }
}
