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
 *
 * <p>The Control Plane never sends the raw API key back: it hashes the raw key with
 * {@code it.eng.tools.security.ApiKeyHasher} at registration time and forwards only that hash on
 * every subsequent CP→DP call (see {@code DataPlaneClient}). This filter therefore hashes its own
 * configured {@code dataplane.api-key} with the local {@link ApiKeyHasher} before comparing, so
 * the comparison is hash-to-hash rather than hash-to-plaintext.
 */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private final DataPlaneProperties properties;
    private final ApiKeyHasher apiKeyHasher;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String configuredKey = properties.getApiKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("dataplane.api-key is not configured — all requests will be unauthenticated");
            filterChain.doFilter(request, response);
            return;
        }
        String apiKey = request.getHeader(API_KEY_HEADER);
        String expectedHash = apiKeyHasher.hash(configuredKey);
        if (apiKey != null && !apiKey.isBlank()
                && MessageDigest.isEqual(apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("control-plane", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
