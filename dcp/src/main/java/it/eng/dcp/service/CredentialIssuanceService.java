package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.config.DcpProperties;
import it.eng.dcp.model.CredentialMessage;
import it.eng.dcp.model.CredentialRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Service responsible for generating/issuing verifiable credentials based on credential requests.
 * This is a stub implementation that can be extended with actual credential generation logic.
 */
@Service
public class CredentialIssuanceService {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialIssuanceService.class);

    private final DcpProperties dcpProperties;
    private final ObjectMapper mapper;

    @Autowired
    public CredentialIssuanceService(DcpProperties dcpProperties, ObjectMapper mapper) {
        this.dcpProperties = dcpProperties;
        this.mapper = mapper;
    }

    /**
     * Generate credentials for an approved credential request.
     *
     * This is a STUB implementation that demonstrates the pattern.
     * In a real implementation, this would:
     * - Look up credential templates or definitions
     * - Generate credential subjects with real data
     * - Sign credentials with issuer's private key
     * - Apply proper credential formats (JWT/JSON-LD)
     *
     * @param request The credential request containing requested credential IDs
     * @return List of credential containers ready for delivery
     */
    public List<CredentialMessage.CredentialContainer> generateCredentials(CredentialRequest request) {
        if (request == null || request.getCredentialIds() == null || request.getCredentialIds().isEmpty()) {
            throw new IllegalArgumentException("Credential request must contain at least one credential ID");
        }

        LOG.info("Generating {} credentials for request {} (holder: {})",
                request.getCredentialIds().size(), request.getIssuerPid(), request.getHolderPid());

        List<CredentialMessage.CredentialContainer> credentials = new ArrayList<>();

        for (String credentialId : request.getCredentialIds()) {
            try {
                CredentialMessage.CredentialContainer credential = generateCredentialForType(credentialId, request);
                credentials.add(credential);
                LOG.debug("Generated credential type '{}' for holder {}", credentialId, request.getHolderPid());
            } catch (Exception e) {
                LOG.error("Failed to generate credential '{}' for request {}: {}",
                        credentialId, request.getIssuerPid(), e.getMessage(), e);
                // Continue with other credentials - partial issuance is acceptable
            }
        }

        if (credentials.isEmpty()) {
            throw new IllegalStateException("Failed to generate any credentials for request: " + request.getIssuerPid());
        }

        return credentials;
    }

    /**
     * Generate a single credential based on the credential type/ID.
     *
     * STUB IMPLEMENTATION - Replace with real credential generation logic.
     *
     * @param credentialId The credential type identifier (e.g., "MembershipCredential")
     * @param request The credential request
     * @return A credential container with the generated credential
     */
    private CredentialMessage.CredentialContainer generateCredentialForType(String credentialId, CredentialRequest request) {
        // Determine credential type from the credential ID
        String credentialType = extractCredentialType(credentialId);

        // Generate credential based on type
        // This is where you'd implement actual business logic for each credential type
        switch (credentialType) {
            case "MembershipCredential":
                return generateMembershipCredential(request);
            case "OrganizationCredential":
                return generateOrganizationCredential(request);
            default:
                LOG.warn("Unknown credential type '{}', generating generic credential", credentialType);
                return generateGenericCredential(credentialType, request);
        }
    }

    /**
     * Extract credential type from credential ID.
     * In a real implementation, you'd look this up from a credential catalog.
     * @param credentialId The credential ID to extract type from
     * @return The credential type string
     */
    private String extractCredentialType(String credentialId) {
        // Simple heuristic: if the ID looks like a type name, use it
        if (credentialId.endsWith("Credential")) {
            return credentialId;
        }
        // Otherwise, might be an ID referencing a catalog entry
        // For stub purposes, default to MembershipCredential
        return "MembershipCredential";
    }

    /**
     * STUB: Generate a MembershipCredential.
     *
     * In production, this would:
     * - Query database for organization details
     * - Validate membership status
     * - Create proper JWT with all required claims
     * - Sign with issuer's private key
     * @param request The credential request
     * @return A credential container with the membership credential
     */
    private CredentialMessage.CredentialContainer generateMembershipCredential(CredentialRequest request) {
        // This is a STUB JWT - in production, use proper JWT library to sign
        String stubJwt = generateStubJWT(request.getHolderPid(), "MembershipCredential", Map.of(
                "membershipType", "Premium",
                "status", "Active",
                "membershipId", "MEMBER-" + UUID.randomUUID().toString().substring(0, 8)
        ));

        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType("MembershipCredential")
                .format("jwt")
                .payload(stubJwt)
                .build();
    }

    /**
     * STUB: Generate an OrganizationCredential.
     * @param request The credential request
     * @return A credential container with the organization credential
     */
    private CredentialMessage.CredentialContainer generateOrganizationCredential(CredentialRequest request) {
        String stubJwt = generateStubJWT(request.getHolderPid(), "OrganizationCredential", Map.of(
                "organizationName", "Example Organization",
                "organizationType", "Corporation",
                "status", "Verified"
        ));

        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType("OrganizationCredential")
                .format("jwt")
                .payload(stubJwt)
                .build();
    }

    /**
     * STUB: Generate a generic credential.
     * @param credentialType The type of credential to generate
     * @param request The credential request
     * @return A credential container with the generic credential
     */
    private CredentialMessage.CredentialContainer generateGenericCredential(String credentialType, CredentialRequest request) {
        String stubJwt = generateStubJWT(request.getHolderPid(), credentialType, Map.of(
                "status", "Active",
                "issuedBy", dcpProperties.getConnectorDid()
        ));

        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType(credentialType)
                .format("jwt")
                .payload(stubJwt)
                .build();
    }

    /**
     * STUB: Generate a JWT string.
     *
     * WARNING: This is NOT a real JWT! It's a placeholder.
     * In production, use com.auth0.jwt or similar to properly sign JWTs.
     *
     * TODO: Replace with real JWT signing using issuer's private key
     * @param holderDid The holder's DID
     * @param credentialType The type of credential
     * @param claims Additional claims to include in the credential subject
     * @return A stub JWT string (NOT properly signed)
     */
    private String generateStubJWT(String holderDid, String credentialType, Map<String, String> claims) {
        // Create JWT payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", dcpProperties.getConnectorDid()); // Issuer
        payload.put("sub", holderDid); // Subject (holder)
        payload.put("iat", Instant.now().getEpochSecond());
        payload.put("exp", Instant.now().plusSeconds(365 * 24 * 60 * 60).getEpochSecond()); // 1 year
        payload.put("jti", "urn:uuid:" + UUID.randomUUID());

        // Add credential subject
        Map<String, Object> vc = new HashMap<>();
        vc.put("@context", List.of(
                "https://www.w3.org/2018/credentials/v1",
                "https://example.org/credentials/v1"
        ));
        vc.put("type", List.of("VerifiableCredential", credentialType));

        Map<String, Object> credentialSubject = new HashMap<>(claims);
        credentialSubject.put("id", holderDid);
        vc.put("credentialSubject", credentialSubject);

        payload.put("vc", vc);

        // STUB: Return base64-encoded JSON as placeholder
        // Real implementation must use proper JWT signing
        try {
            String payloadJson = mapper.writeValueAsString(payload);
            String header = "{\"kid\":\"" + dcpProperties.getConnectorDid() + "#key-1\",\"alg\":\"ES256\"}";
            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes());
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes());
            String stubSignature = Base64.getUrlEncoder().withoutPadding().encodeToString("STUB_SIGNATURE_REPLACE_WITH_REAL_SIGNATURE".getBytes());

            return encodedHeader + "." + encodedPayload + "." + stubSignature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate stub JWT", e);
        }
    }
}

