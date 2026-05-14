package it.eng.dataplane.core.security;

import it.eng.dataplane.core.config.DataPlaneProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

/**
 * Validates the {@code X-Api-Key} header on incoming requests to Data Plane endpoints.
 * If the header matches the configured {@code dataplane.apiKey}, the request is
 * authenticated as the Control Plane.
 */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private final DataPlaneProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()
                && MessageDigest.isEqual(apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        properties.getApiKey().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("control-plane", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
