package it.eng.dcp.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Represents metadata for a Presentation Access Token.
 * This is used internally to track and validate tokens issued to verifiers.
 *
 * <p>According to DCP Protocol, the token in the "token" claim can be:
 * <ul>
 *   <li>An opaque access token</li>
 *   <li>A presentation ID reference</li>
 *   <li>A structured token with multiple fields</li>
 * </ul>
 *
 * <p>This class provides a unified model for all variants.
 */
@Data
@Builder
public class PresentationAccessToken {

    /**
     * The unique token identifier (UUID or JTI).
     */
    private String tokenId;

    /**
     * The DID of the holder who issued this token.
     */
    private String holderDid;

    /**
     * The DID of the verifier who can use this token.
     */
    private String verifierDid;

    /**
     * The scopes (credential types or presentation definitions) this token grants access to.
     * Examples: ["MembershipCredential", "OrganizationCredential"]
     */
    private List<String> scopes;

    /**
     * Optional: The presentation ID if this token references a specific pre-created VP.
     */
    private String presentationId;

    /**
     * When the token was issued.
     */
    private Instant issuedAt;

    /**
     * When the token expires.
     */
    private Instant expiresAt;

    /**
     * The token purpose (e.g., "presentation_query").
     */
    private String purpose;

    /**
     * The actual token string (JWT or opaque).
     */
    private String tokenValue;

    /**
     * Whether this token has been used (for one-time tokens).
     */
    private boolean used;

    /**
     * Check if the token is expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if the token is valid (not expired and not used).
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return !isExpired() && !used;
    }

    /**
     * Check if the token grants access to a specific scope.
     *
     * @param scope The scope to check
     * @return true if the scope is granted, false otherwise
     */
    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }

    /**
     * Check if the token is for a specific verifier.
     *
     * @param verifierDid The verifier DID to check
     * @return true if the token is for this verifier
     */
    public boolean isForVerifier(String verifierDid) {
        return this.verifierDid != null && this.verifierDid.equals(verifierDid);
    }
}

