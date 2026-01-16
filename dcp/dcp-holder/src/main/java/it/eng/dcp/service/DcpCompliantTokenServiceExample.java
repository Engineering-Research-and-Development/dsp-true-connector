package it.eng.dcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Example usage of the DCP-compliant token service.
 * This class demonstrates the different ways to create Self-Issued ID Tokens
 * according to the DCP Protocol v1.0 specification.
 *
 * <p>Usage scenarios:
 * <ol>
 *   <li><b>Access Token Only:</b> Most common pattern - verifier fetches VP using access token</li>
 *   <li><b>Presentation ID Only:</b> Direct reference to pre-created VP</li>
 *   <li><b>Both:</b> Maximum flexibility for verifier</li>
 * </ol>
 */
@Component
@Slf4j
public class DcpCompliantTokenServiceExample {

    private final DcpCompliantTokenService tokenService;
    private final PresentationAccessTokenGenerator tokenGenerator;

    @Autowired
    public DcpCompliantTokenServiceExample(DcpCompliantTokenService tokenService,
                                          PresentationAccessTokenGenerator tokenGenerator) {
        this.tokenService = tokenService;
        this.tokenGenerator = tokenGenerator;
    }

    /**
     * Example 1: Create token with access token (RECOMMENDED DCP PATTERN).
     *
     * Use case: Holder is sending a DSP request to a verifier and wants to grant
     * the verifier access to fetch specific VCs/VPs.
     *
     * Flow:
     * 1. Holder creates Self-Issued ID Token with "token" claim containing access token
     * 2. Holder sends DSP request with token in Authorization header
     * 3. Verifier validates the token
     * 4. Verifier resolves holder's DID → gets Credential Service URL
     * 5. Verifier calls POST /presentations/query with the token
     * 6. Credential Service validates access token and returns VP
     *
     * @param verifierDid The DID of the verifier
     * @return Self-Issued ID Token with access token in "token" claim
     */
    public String createTokenForDspRequest(String verifierDid) {
        log.info("Creating DCP-compliant token for DSP request to verifier: {}", verifierDid);

        // Specify which credential types the verifier can access
        String[] scopes = {
            "MembershipCredential",
            "OrganizationCredential"
        };

        // Create Self-Issued ID Token with access token
        String token = tokenService.createTokenWithAccessToken(verifierDid, scopes);

        log.info("Created Self-Issued ID Token (size: {} bytes)", token.length());
        log.debug("Token: {}", token);

        return token;
    }

    /**
     * Example 2: Create token with presentation ID.
     *
     * Use case: Holder has pre-created a VP and wants to give verifier a direct
     * reference to fetch it.
     *
     * Flow:
     * 1. Holder creates VP ahead of time
     * 2. Holder stores VP with unique ID
     * 3. Holder creates Self-Issued ID Token with presentationId in "token" claim
     * 4. Verifier extracts presentationId and fetches specific VP
     *
     * @param verifierDid The DID of the verifier
     * @return Self-Issued ID Token with presentation ID
     */
    public String createTokenWithPreCreatedPresentation(String verifierDid) {
        log.info("Creating DCP-compliant token with presentation ID for verifier: {}", verifierDid);

        // Generate a presentation ID (this would typically come from VP storage)
        String presentationId = tokenGenerator.generatePresentationId();

        // Create Self-Issued ID Token with presentationId
        String token = tokenService.createTokenWithPresentationId(verifierDid, presentationId);

        log.info("Created Self-Issued ID Token with presentationId: {}", presentationId);

        return token;
    }

    /**
     * Example 3: Create token with both access token and presentation ID.
     *
     * Use case: Maximum flexibility - verifier can either fetch the specific
     * presentation or use the access token for broader queries.
     *
     * @param verifierDid The DID of the verifier
     * @param presentationId The ID of a pre-created presentation
     * @return Self-Issued ID Token with both access token and presentation ID
     */
    public String createTokenWithBoth(String verifierDid, String presentationId) {
        log.info("Creating DCP-compliant token with both access token and presentationId for verifier: {}",
                 verifierDid);

        String[] scopes = {"MembershipCredential"};

        // Create token with both
        String token = tokenService.createTokenWithBoth(verifierDid, presentationId, scopes);

        log.info("Created Self-Issued ID Token with both access token and presentationId: {}",
                 presentationId);

        return token;
    }

    /**
     * Example 4: Comparison with existing implementation.
     *
     * Shows the difference between embedding VP (old) vs using token claim (new DCP-compliant)
     */
    public void compareImplementations() {
        String verifierDid = "did:web:verifier.example";

        log.info("=== Comparing Token Implementations ===");

        // NEW: DCP-compliant with token claim
        String dcpToken = tokenService.createTokenWithAccessToken(verifierDid, "MembershipCredential");
        log.info("DCP-compliant token size: {} bytes", dcpToken.length());

        // The old implementation would embed the entire VP in the token, making it much larger
        // This new approach is:
        // - Smaller (no embedded VP)
        // - More private (VP only sent when authorized)
        // - More flexible (fresh credentials fetched on-demand)
        // - DCP specification compliant

        log.info("=== Comparison Complete ===");
    }

    /**
     * Example 5: Creating access token with specific credential IDs.
     *
     * Use case: Grant access to specific credentials by ID rather than type
     * @param verifierDid The DID of the verifier
     * @return Self-Issued ID Token with access token for specific credential IDs
     */
    public String createTokenForSpecificCredentials(String verifierDid) {
        log.info("Creating access token for specific credential IDs");

        // Use DCP scope format: org.eclipse.dspace.dcp.vc.id:<credential-id>
        String[] scopes = {
            "org.eclipse.dspace.dcp.vc.id:8247b87d-8d72-47e1-8128-9ce47e3d829d",
            "org.eclipse.dspace.dcp.vc.id:f3b5c2a1-9876-4321-abcd-1234567890ab"
        };

        String token = tokenService.createTokenWithAccessToken(verifierDid, scopes);

        log.info("Created token with specific credential IDs");

        return token;
    }

    /**
     * Example 6: Creating access token with credential types.
     *
     * Use case: Grant access to all credentials of specific types
     * @param verifierDid The DID of the verifier
     * @return Self-Issued ID Token with access token for credential types
     */
    public String createTokenForCredentialTypes(String verifierDid) {
        log.info("Creating access token for credential types");

        // Use DCP scope format: org.eclipse.dspace.dcp.vc.type:<credential-type>
        String[] scopes = {
            "org.eclipse.dspace.dcp.vc.type:MembershipCredential",
            "org.eclipse.dspace.dcp.vc.type:OrganizationCredential"
        };

        String token = tokenService.createTokenWithAccessToken(verifierDid, scopes);

        log.info("Created token with credential types");

        return token;
    }
}

