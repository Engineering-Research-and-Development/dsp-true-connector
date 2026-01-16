package it.eng.dcp.service;

import it.eng.dcp.common.util.DidUrlConverter;
import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Service to generate Verifiable Presentation JWT for connector authentication.
 *
 * This service supports two modes:
 * 1. Legacy mode: Embeds full VP in JWT (existing implementation)
 * 2. DCP-compliant mode: Uses "token" claim with access token (DCP Protocol v1.0)
 *
 * The feature is controlled by the dcp.vp.enabled property.
 * The mode is controlled by the dcp.vp.use-dcp-compliant property.
 */
@Service
public class DcpCredentialService { //implements VpCredentialService {

    private static final Logger logger = LoggerFactory.getLogger(DcpCredentialService.class);

    private final PresentationService presentationService;
    private final DcpCompliantTokenService dcpCompliantTokenService;

    @Value("${dcp.vp.enabled:false}")
    private boolean vpEnabled;

    @Value("${dcp.vp.scope:}")
    private String vpScope;

    @Value("${dcp.vp.use-dcp-compliant:false}")
    private boolean useDcpCompliant;

    @Value("${dcp.vp.target-verifier-did:}")
    private String targetVerifierDid;

    /**
     * Constructor with DCP PresentationService and DcpCompliantTokenService dependencies.
     *
     * @param presentationService DCP service for creating verifiable presentations (legacy mode)
     * @param dcpCompliantTokenService DCP-compliant token service (new mode)
     */
    @Autowired
    public DcpCredentialService(PresentationService presentationService,
                               DcpCompliantTokenService dcpCompliantTokenService) {
        this.presentationService = presentationService;
        this.dcpCompliantTokenService = dcpCompliantTokenService;
        if (vpEnabled) {
            if (useDcpCompliant) {
                logger.info("VP JWT authentication is enabled - using DCP-COMPLIANT mode (token claim)");
            } else {
                logger.info("VP JWT authentication is enabled - using LEGACY mode (embedded VP)");
            }
        } else {
            logger.debug("VP JWT authentication is disabled (dcp.vp.enabled=false)");
        }
    }

    /**
     * Check if VP JWT authentication is enabled.
     * @return true if VP is enabled via property
     */
//    @Override
    public boolean isVpJwtEnabled() {
        return vpEnabled;
    }

    /**
     * Check if DCP-compliant mode is enabled.
     * @return true if using DCP-compliant mode with "token" claim
     */
    public boolean isDcpCompliantMode() {
        return useDcpCompliant;
    }

    /**
     * Get the current authentication mode as a string for logging.
     * @return "DCP-COMPLIANT" or "LEGACY"
     */
    public String getAuthenticationMode() {
        return useDcpCompliant ? "DCP-COMPLIANT" : "LEGACY";
    }

    /**
     * Generate a Verifiable Presentation JWT for connector authentication.
     *
     * @return VP JWT string (without "Bearer " prefix) or null if generation fails
     */
    public String getVerifiablePresentationJwt() {
        if (!vpEnabled) {
            logger.debug("VP JWT generation skipped - not enabled (dcp.vp.enabled=false)");
            return null;
        }

        try {
            logger.debug("Generating Verifiable Presentation JWT for connector authentication");

            // Create PresentationQueryMessage using builder
            PresentationQueryMessage.Builder builder = PresentationQueryMessage.Builder.newInstance();

            // Set scope if configured
            if (vpScope != null && !vpScope.trim().isEmpty()) {
                List<String> scopeList = new ArrayList<>();
                for (String type : vpScope.split(",")) {
                    scopeList.add(type.trim());
                }
                builder.scope(scopeList);
                logger.debug("VP scope set to: {}", scopeList);
            }

            // Set empty presentation definition (use default)
            builder.presentationDefinition(new HashMap<>());

            PresentationQueryMessage queryMessage = builder.build();

            // Call PresentationService to create VP
            PresentationResponseMessage responseMessage = presentationService.createPresentation(queryMessage);

            // Extract presentations
            List<Object> presentations = responseMessage.getPresentation();

            if (presentations == null || presentations.isEmpty()) {
                logger.warn("No Verifiable Presentation generated - no credentials found in repository");
                return null;
            }

            // Get the first presentation (should be a JWT string)
            Object firstPresentation = presentations.get(0);
            if (firstPresentation instanceof String) {
                String vpJwt = (String) firstPresentation;
                logger.info("Successfully generated VP JWT for connector authentication (length: {})", vpJwt.length());
                logger.debug("VP JWT: {}", vpJwt);
                return vpJwt;
            } else {
                logger.warn("Presentation is not a JWT string: {}", firstPresentation.getClass().getName());
                return null;
            }

        } catch (Exception e) {
            logger.error("Failed to generate Verifiable Presentation JWT", e);
            return null;
        }
    }

    /**
     * Generate a Bearer token with VP JWT.
     * Automatically chooses between legacy mode (embedded VP) and DCP-compliant mode (token claim).
     *
     * @return "Bearer {JWT}" or null if generation fails
     */
//    @Override
    public String getBearerToken() {
        return getBearerToken(null);
    }

    /**
     * Generate a Bearer token with VP JWT for a specific target URL.
     * Automatically extracts the verifier DID from the target URL and chooses between
     * legacy mode (embedded VP) and DCP-compliant mode (token claim).
     *
     * <p>This method enables dynamic verifier DID extraction, allowing the connector to
     * communicate with multiple verifiers without hardcoding DIDs in configuration.
     *
     * <p>The target URL is converted to a DID as follows:
     * <ul>
     *   <li>https://verifier.com/catalog/request → did:web:verifier.com</li>
     *   <li>https://localhost:8080/dsp/catalog → did:web:localhost%3A8080</li>
     * </ul>
     *
     * @param targetUrl The full URL where the request will be sent (can be null to use configured DID)
     * @return "Bearer {JWT}" or null if generation fails
     */
//    @Override
    public String getBearerToken(String targetUrl) {
        if (useDcpCompliant) {
            // NEW: DCP-compliant mode - use token claim
            return getBearerTokenDcpCompliant(targetUrl);
        } else {
            // LEGACY: Embed full VP in JWT (targetUrl not used in legacy mode)
            return getBearerTokenLegacy();
        }
    }

    /**
     * Generate a Bearer token using LEGACY mode (embedded VP).
     * This is the existing implementation.
     *
     * @return "Bearer {VP_JWT}" or null if generation fails
     */
    private String getBearerTokenLegacy() {
        String vpJwt = getVerifiablePresentationJwt();
        if (vpJwt != null) {
            logger.debug("Generated LEGACY bearer token with embedded VP (size: {} bytes)", vpJwt.length());
            return "Bearer " + vpJwt;
        }
        return null;
    }

    /**
     * Generate a Bearer token using DCP-COMPLIANT mode (token claim).
     * Creates a Self-Issued ID Token with "token" claim containing an access token.
     *
     * According to DCP Protocol v1.0 Section 4.3.1, the verifier will use this token
     * to fetch the VP from the holder's Credential Service via /presentations/query.
     *
     * @param targetUrl The target URL to extract verifier DID from (can be null to use configured DID)
     * @return "Bearer {Self-Issued-ID-Token}" or null if generation fails
     */
    private String getBearerTokenDcpCompliant(String targetUrl) {
        if (!vpEnabled) {
            logger.debug("DCP-compliant token generation skipped - not enabled (dcp.vp.enabled=false)");
            return null;
        }

        try {
            // Get verifier DID - either from URL or from configuration
            String verifierDid = getVerifierDidFromUrl(targetUrl);

            if (verifierDid == null || verifierDid.isBlank()) {
                logger.error("Cannot generate DCP-compliant token - verifier DID not available. " +
                           "Either provide targetUrl parameter or set dcp.vp.target-verifier-did property");
                return null;
            }

            logger.debug("Generating DCP-compliant Self-Issued ID Token for verifier: {}", verifierDid);

            // Parse scopes
            String[] scopes = parseScopesFromConfig();

            // Create Self-Issued ID Token with access token in "token" claim
            String selfIssuedIdToken = dcpCompliantTokenService.createTokenWithAccessToken(
                verifierDid,
                scopes
            );

            logger.info("Successfully generated DCP-compliant Self-Issued ID Token (size: {} bytes) for verifier: {}",
                       selfIssuedIdToken.length(), verifierDid);
            logger.debug("Self-Issued ID Token structure: iss=holder, aud={}, token=<access-token>", verifierDid);

            return "Bearer " + selfIssuedIdToken;

        } catch (Exception e) {
            logger.error("Failed to generate DCP-compliant Self-Issued ID Token", e);
            return null;
        }
    }

    /**
     * Get the verifier DID either from the target URL or from configuration.
     * Priority: targetUrl → configured DID
     *
     * @param targetUrl The target URL to extract DID from (can be null)
     * @return The verifier DID or null if not available
     */
    private String getVerifierDidFromUrl(String targetUrl) {
        // First priority: Extract from target URL if provided
        if (targetUrl != null && !targetUrl.isBlank()) {
            try {
                String extractedDid = DidUrlConverter.convertUrlToDid(targetUrl);
                logger.debug("Extracted verifier DID from URL '{}': {}", targetUrl, extractedDid);
                return extractedDid;
            } catch (Exception e) {
                logger.warn("Failed to extract DID from URL '{}': {}. Falling back to configured DID.",
                           targetUrl, e.getMessage());
            }
        }

        // Second priority: Use configured verifier DID
        return getTargetVerifierDid();
    }

    /**
     * Get the target verifier DID from configuration.
     * This is required for DCP-compliant mode to know who will receive the token.
     *
     * @return The verifier DID or null if not configured
     */
    private String getTargetVerifierDid() {
        if (targetVerifierDid != null && !targetVerifierDid.isBlank()) {
            return targetVerifierDid.trim();
        }

        // Could also be determined dynamically based on the request context
        // For now, require explicit configuration
        return null;
    }

    /**
     * Parse scopes from configuration.
     *
     * @return Array of scopes or empty array if none configured
     */
    private String[] parseScopesFromConfig() {
        if (vpScope == null || vpScope.trim().isEmpty()) {
            logger.debug("No scopes configured - token will grant access to all presentations");
            return new String[0];
        }

        String[] scopes = vpScope.split(",");
        for (int i = 0; i < scopes.length; i++) {
            scopes[i] = scopes[i].trim();
        }

        logger.debug("Configured scopes for DCP-compliant token: {}", String.join(", ", scopes));
        return scopes;
    }
}

