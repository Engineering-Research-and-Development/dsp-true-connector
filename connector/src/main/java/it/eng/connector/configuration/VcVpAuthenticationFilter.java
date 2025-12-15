package it.eng.connector.configuration;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;

import it.eng.dcp.model.PresentationResponseMessage;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Filter to extract and validate Verifiable Presentations from Authorization header.
 * Expects: Authorization: Bearer {JWT-with-VP-claim}
 *
 * The VP can be in different formats:
 * 1. JWT token with VP in "vp" or "presentation" claim (primary format)
 * 2. PresentationResponseMessage serialized as JSON
 * 3. Base64-encoded JSON
 *
 * NOTE: This filter only PARSES and EXTRACTS the VP. It does NOT verify signatures.
 * All signature verification is performed by VcVpAuthenticationProvider.
 *
 * Can be enabled/disabled via the dcp.vp.enabled property.
 */
@Slf4j
public class VcVpAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;
    private final ObjectMapper objectMapper;
    private final boolean vcVpEnabled;

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
            .getContextHolderStrategy();

    public VcVpAuthenticationFilter(AuthenticationManager authenticationManager,
                                     ObjectMapper objectMapper,
                                     boolean vcVpEnabled) {
        super();
        this.authenticationManager = authenticationManager;
        this.objectMapper = objectMapper;
        this.vcVpEnabled = vcVpEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Check if VC/VP authentication is enabled
        if (!vcVpEnabled) {
            log.trace("VC/VP authentication is disabled (dcp.vp.enabled=false), skipping VP filter");
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);

            // Try to parse as PresentationResponseMessage
            PresentationResponseMessage presentation = parsePresentation(token);

            if (presentation != null) {
                log.debug("Extracted Verifiable Presentation from Authorization header");
                // Pass both the presentation AND the raw token so the provider can verify signatures
                Authentication authRequest = new VcVpAuthenticationToken(presentation, token);
                Authentication authResult = this.authenticationManager.authenticate(authRequest);

                // Only set security context if authResult is not null (successful authentication)
                if (authResult != null && authResult.isAuthenticated()) {
                    SecurityContext context = this.securityContextHolderStrategy.createEmptyContext();
                    context.setAuthentication(authResult);
                    this.securityContextHolderStrategy.setContext(context);
                    onSuccessfulAuthentication(request, response, authResult);
                } else {
                    log.debug("VP authentication returned null, continuing to next auth method");
                }
            } else {
                // Not a VP, let other filters handle it
                log.trace("Token is not VP format, continuing to next auth method");
            }
        } catch (AuthenticationException ex) {
            this.securityContextHolderStrategy.clearContext();
            log.debug("Failed to process VP authentication request: {}, continuing to next auth method", ex.getMessage());
            onUnsuccessfulAuthentication(request, response, ex);
            // Don't return - continue filter chain to allow fallback to other auth methods
        } catch (Exception ex) {
            log.debug("Failed to parse VP from Authorization header, trying other auth methods: {}", ex.getMessage());
            // Continue filter chain - this allows fallback to JWT/Basic auth
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String requestPath = request.getRequestURI();
        // Skip VP validation for endpoints that don't need it
        return requestPath != null && (requestPath.startsWith("/issuer")
                || requestPath.startsWith("/dcp")
                || requestPath.startsWith("/api/dev/token")
                || requestPath.startsWith("/api/"));
    }

    /**
     * Parse the presentation from the token string.
     * NOTE: This method only PARSES the token, it does NOT verify signatures.
     * Signature verification is done by VcVpAuthenticationProvider.
     *
     * Supports:
     * 1. JWT format with VP in claims (e.g., vp claim or presentation claim)
     * 2. Direct JSON (PresentationResponseMessage)
     * 3. Base64-encoded JSON
     * @param token The token string from Authorization header
     * @return Parsed PresentationResponseMessage or null if not a VP format
     */
    private PresentationResponseMessage parsePresentation(String token) {
        // Try to parse as JWT first (most common format for VPs)
        try {
            JWT jwt = JWTParser.parse(token);
            Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();

            // Look for VP in standard claims
            Object vpClaim = claims.get("vp");
            if (vpClaim == null) {
                // Try alternative claim names
                vpClaim = claims.get("presentation");
            }

            if (vpClaim != null) {
                log.debug("Found VP claim in JWT token");
                // Store the raw JWT token so the provider can verify the signature
                PresentationResponseMessage presentation = vpClaimToPresentation(vpClaim);

                // TODO: Consider storing the raw JWT token in the presentation for signature verification by the provider
                // For now, the provider will re-parse from the original token stored in VcVpAuthenticationToken

                return presentation;
            } else {
                log.trace("JWT token does not contain vp or presentation claim");
                return null;
            }
        } catch (ParseException e) {
            log.trace("Token is not a valid JWT, trying other formats: {}", e.getMessage());
        } catch (Exception e) {
            log.trace("Failed to extract VP from JWT: {}", e.getMessage());
        }

        // Fallback: Try direct JSON parse
        try {
            return objectMapper.readValue(token, PresentationResponseMessage.class);
        } catch (Exception e1) {
            log.trace("Token is not direct JSON: {}", e1.getMessage());
        }

        // Fallback: Try base64 decode
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(token);
            String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            return objectMapper.readValue(json, PresentationResponseMessage.class);
        } catch (Exception e2) {
            log.trace("Token is not base64-encoded JSON: {}", e2.getMessage());
        }

        // Not a VP format we recognize
        return null;
    }


    /**
     * Convert VP claim from JWT to PresentationResponseMessage.
     * The VP claim can be a Map or other structure depending on the JWT format.
     * @param vpClaim The VP claim object from JWT
     * @return PresentationResponseMessage
     */
    private PresentationResponseMessage vpClaimToPresentation(Object vpClaim) {
        try {
            // Convert the VP claim to PresentationResponseMessage
            // The claim should contain the VP structure
            String vpJson = objectMapper.writeValueAsString(vpClaim);

            // Parse as a map to construct PresentationResponseMessage
            @SuppressWarnings("unchecked")
            Map<String, Object> vpMap = objectMapper.readValue(vpJson, Map.class);

            // Build PresentationResponseMessage from the VP claim
            PresentationResponseMessage.Builder builder = PresentationResponseMessage.Builder.newInstance();

            // Add the verifiableCredential array if present
            Object credentialsObj = vpMap.get("verifiableCredential");
            if (credentialsObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> credentials = (java.util.List<Object>) credentialsObj;
                builder.presentation(credentials);
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("Failed to convert VP claim to PresentationResponseMessage: {}", e.getMessage());
            return null;
        }
    }

    protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
            Authentication authResult) throws IOException {
        log.info("VC/VP authentication successful for: {}", authResult.getName());
    }

    protected void onUnsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException failed) throws IOException {
        log.warn("VC/VP authentication failed: {}", failed.getMessage());
    }
}

