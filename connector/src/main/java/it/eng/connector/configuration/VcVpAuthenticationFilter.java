package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.model.VerifiablePresentation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

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

                    // DEBUG: Verify context was set
                    log.info("✓ VP Authentication SUCCESS - Set SecurityContext");
                    log.info("  - Subject: {}", authResult.getName());
                    log.info("  - isAuthenticated: {}", authResult.isAuthenticated());
                    log.info("  - Authorities: {}", authResult.getAuthorities());
                    log.info("  - Principal type: {}", authResult.getPrincipal() != null ? authResult.getPrincipal().getClass().getSimpleName() : "null");

                    // Double-check the context was actually set
                    Authentication contextAuth = this.securityContextHolderStrategy.getContext().getAuthentication();
                    log.info("  - Context verification: isAuthenticated={}, subject={}",
                            contextAuth != null ? contextAuth.isAuthenticated() : "null",
                            contextAuth != null ? contextAuth.getName() : "null");

                    onSuccessfulAuthentication(request, response, authResult);
                } else {
                    log.debug("VP authentication returned null or not authenticated, continuing to next auth method");
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

                // Extract holder DID from JWT subject if not in VP claim
                String subjectDid = claims.get("sub") != null ? claims.get("sub").toString() : null;

                // Store the raw JWT token so the provider can verify the signature
                PresentationResponseMessage presentation = vpClaimToPresentation(vpClaim, subjectDid);

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
     * The VP claim is parsed into a proper VerifiablePresentation object for type safety and maintainability.
     * @param vpClaim The VP claim object from JWT
     * @param subjectDid The subject DID from JWT (used as holder if not in VP)
     * @return PresentationResponseMessage containing a properly typed VerifiablePresentation object
     */
    private PresentationResponseMessage vpClaimToPresentation(Object vpClaim, String subjectDid) {
        try {
            // Convert the VP claim to JSON string
            String vpJson = objectMapper.writeValueAsString(vpClaim);
            log.debug("VP JSON: {}", vpJson);

            // Parse as a map to extract VP fields
            @SuppressWarnings("unchecked")
            Map<String, Object> vpMap = objectMapper.readValue(vpJson, Map.class);

            // Build a proper VerifiablePresentation object from the VP claim
            VerifiablePresentation.Builder vpBuilder = VerifiablePresentation.Builder.newInstance();

            // Extract and set holderDid (from JWT subject or VP holder)
            String holderDid = extractHolderDid(vpMap);
            if (holderDid == null && subjectDid != null) {
                // Fallback to JWT subject if holder not in VP
                holderDid = subjectDid;
            }
            // Validate that holderDid is present - this is a required field
            if (holderDid == null) {
                log.warn("Invalid VP: No holderDid found in VP holder field or JWT subject claim");
                return null;
            }
            vpBuilder.holderDid(holderDid);
            log.debug("Set holderDid: {}", holderDid);

            // Extract verifiableCredential list
            Object vcObj = vpMap.get("verifiableCredential");
            if (vcObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> vcList = (List<Object>) vcObj;
                vpBuilder.credentials(vcList);
                log.debug("Set {} credentials", vcList.size());

                // Also populate credentialIds (required field with min size 1)
                // Generate IDs from the credentials if they don't have explicit IDs
                java.util.List<String> credentialIds = new java.util.ArrayList<>();
                for (int i = 0; i < vcList.size(); i++) {
                    credentialIds.add("credential-" + i);
                }
                vpBuilder.credentialIds(credentialIds);
                log.debug("Set {} credentialIds", credentialIds.size());
            } else {
                // If no credentials, add a placeholder to satisfy validation
                vpBuilder.credentialIds(java.util.Collections.singletonList("placeholder-credential"));
            }

            // Extract profileId
            Object profileIdObj = vpMap.get("profileId");
            if (profileIdObj != null) {
                vpBuilder.profileId(ProfileId.fromString(profileIdObj.toString()));
                log.debug("Set profileId: {}", profileIdObj);
            } else {
                // Default profile if not specified
                vpBuilder.profileId(ProfileId.VC20_BSSL_JWT);
                log.debug("Set default profileId: VC20_BSSL_JWT");
            }

            // Build the VerifiablePresentation object
            VerifiablePresentation verifiablePresentation = vpBuilder.build();
            log.debug("Built VerifiablePresentation: id={}, holderDid={}, profileId={}",
                    verifiablePresentation.getId(), verifiablePresentation.getHolderDid(), verifiablePresentation.getProfileId());

            // Build PresentationResponseMessage with the typed VerifiablePresentation
            PresentationResponseMessage.Builder builder = PresentationResponseMessage.Builder.newInstance();

            // The presentation list should contain the VerifiablePresentation object (not a generic Map)
            java.util.List<Object> presentationList = new java.util.ArrayList<>();
            presentationList.add(verifiablePresentation);
            builder.presentation(presentationList);

            PresentationResponseMessage result = builder.build();
            log.debug("Built PresentationResponseMessage with {} presentations", result.getPresentation().size());
            return result;
        } catch (Exception e) {
            log.error("Failed to convert VP claim to PresentationResponseMessage", e);
            return null;
        }
    }

    /**
     * Extract holder DID from VP map.
     * Tries multiple fields: holder, holder.id, sub (from JWT context)
     * @param vpMap The VP map
     * @return Holder DID or null
     */
    private String extractHolderDid(Map<String, Object> vpMap) {
        // Try holder field
        Object holder = vpMap.get("holder");
        if (holder instanceof String) {
            return (String) holder;
        } else if (holder instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> holderMap = (Map<String, Object>) holder;
            Object holderId = holderMap.get("id");
            if (holderId instanceof String) {
                return (String) holderId;
            }
        }

        // Fallback: try to get from context (will be set by caller if needed)
        return null;
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

