package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.config.DcpProperties;
import it.eng.dcp.model.ConstraintRule;
import it.eng.dcp.model.CredentialGenerationContext;
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
    private final KeyService keyService;

    @Autowired
    public CredentialIssuanceService(DcpProperties dcpProperties, KeyService keyService) {
        this.dcpProperties = dcpProperties;
        this.keyService = keyService;
    }

    /**
     * Generate credentials for an approved credential request (backward compatible).
     *
     * @param request The credential request containing requested credential IDs
     * @return List of credential containers ready for delivery
     */
    public List<CredentialMessage.CredentialContainer> generateCredentials(CredentialRequest request) {
        return generateCredentials(request, null, null);
    }

    /**
     * Generate credentials with custom claims and constraints.
     *
     * @param request The credential request containing requested credential IDs
     * @param customClaims Optional custom claims to include (e.g., {"country_code": "IT", "role": "admin"})
     * @param constraintsData Optional constraints to verify before issuance
     * @return List of credential containers ready for delivery
     */
    public List<CredentialMessage.CredentialContainer> generateCredentials(
            CredentialRequest request,
            Map<String, Object> customClaims,
            List<Map<String, Object>> constraintsData) {

        if (request == null || request.getCredentialIds() == null || request.getCredentialIds().isEmpty()) {
            throw new IllegalArgumentException("Credential request must contain at least one credential ID");
        }

        // Convert constraints data to ConstraintRule objects
        List<ConstraintRule> constraints = new ArrayList<>();
        if (constraintsData != null) {
            for (Map<String, Object> constraintData : constraintsData) {
                try {
                    ConstraintRule constraint = parseConstraint(constraintData);
                    constraints.add(constraint);
                } catch (Exception e) {
                    LOG.warn("Failed to parse constraint: {}", e.getMessage());
                }
            }
        }

        // Create generation context
        CredentialGenerationContext context = CredentialGenerationContext.withConstraints(
            request,
            customClaims != null ? customClaims : new HashMap<>(),
            constraints
        );

        LOG.info("Generating {} credentials for request {} (holder: {}) with {} custom claims and {} constraints",
                request.getCredentialIds().size(), request.getIssuerPid(), request.getHolderPid(),
                customClaims != null ? customClaims.size() : 0,
                constraints.size());

        List<CredentialMessage.CredentialContainer> credentials = new ArrayList<>();

        for (String credentialId : request.getCredentialIds()) {
            try {
                CredentialMessage.CredentialContainer credential = generateCredentialForType(credentialId, context);
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
     * Parse a constraint from map data.
     *
     * @param data The constraint data map
     * @return A ConstraintRule object
     */
    private ConstraintRule parseConstraint(Map<String, Object> data) {
        String claimName = (String) data.get("claimName");
        String operatorStr = (String) data.get("operator");
        Object value = data.get("value");

        if (claimName == null || operatorStr == null) {
            throw new IllegalArgumentException("Constraint must have claimName and operator");
        }

        ConstraintRule.Operator operator;
        try {
            operator = ConstraintRule.Operator.valueOf(operatorStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid operator: " + operatorStr);
        }

        return ConstraintRule.Builder.newInstance()
            .claimName(claimName)
            .operator(operator)
            .value(value)
            .build();
    }

    /**
     * Generate a single credential based on the credential type/ID.
     *
     * @param credentialId The credential type identifier
     * @param context The generation context with request, claims, and constraints
     * @return A credential container with the generated credential
     */
    private CredentialMessage.CredentialContainer generateCredentialForType(
            String credentialId,
            CredentialGenerationContext context) {
        // Determine credential type from the credential ID
        String credentialType = extractCredentialType(credentialId);

        // Generate credential based on type
        // This is where you'd implement actual business logic for each credential type
        switch (credentialType) {
            case "MembershipCredential":
                return generateMembershipCredential(context);
            case "OrganizationCredential":
                return generateOrganizationCredential(context);
            default:
                LOG.warn("Unknown credential type '{}', generating generic credential", credentialType);
                return generateGenericCredential(credentialType, context);
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
     * Generate a MembershipCredential with proper JWT signing.
     *
     * In production, this would:
     * - Query database for organization details
     * - Validate membership status
     * - Create proper JWT with all required claims
     * - Sign with issuer's private key
     *
     * @param context The generation context
     * @return A credential container with the signed membership credential
     */
    private CredentialMessage.CredentialContainer generateMembershipCredential(CredentialGenerationContext context) {
        String signedJwt = generateSignedJWT(context.getRequest().getHolderPid(), "MembershipCredential", Map.of(
                "membershipType", "Premium",
                "status", "Active",
                "membershipId", "MEMBER-" + UUID.randomUUID().toString().substring(0, 8)
        ));

        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType("MembershipCredential")
                .format("jwt")
                .payload(signedJwt)
                .build();
    }

    /**
     * Generate an OrganizationCredential with proper JWT signing.
     *
     * @param context The generation context
     * @return A credential container with the signed organization credential
     */
    private CredentialMessage.CredentialContainer generateOrganizationCredential(CredentialGenerationContext context) {
        String signedJwt = generateSignedJWT(context.getRequest().getHolderPid(), "OrganizationCredential", Map.of(
                "organizationName", "Example Organization",
                "organizationType", "Corporation",
                "status", "Verified"
        ));

        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType("OrganizationCredential")
                .format("jwt")
                .payload(signedJwt)
                .build();
    }

    /**
     * Generate a generic credential with proper JWT signing.
     *
     * @param credentialType The type of credential to generate
     * @param context The generation context
     * @return A credential container with the signed generic credential
     */
    private CredentialMessage.CredentialContainer generateGenericCredential(String credentialType, CredentialGenerationContext context) {
        String signedJwt = generateSignedJWT(context.getRequest().getHolderPid(), credentialType, Map.of(
                "status", "Active",
                "issuedBy", dcpProperties.getConnectorDid()
        ));

        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType(credentialType)
                .format("jwt")
                .payload(signedJwt)
                .build();
    }

    /**
     * Generate a properly signed JWT Verifiable Credential using ES256.
     *
     * Per W3C VC Data Model, the JWT contains:
     * - Standard JWT claims (iss, sub, iat, exp, jti)
     * - vc claim containing the Verifiable Credential structure
     *
     * @param holderDid The holder's DID
     * @param credentialType The type of credential
     * @param claims Additional claims to include in the credential subject
     * @return A properly signed JWT VC string
     */
    private String generateSignedJWT(String holderDid, String credentialType, Map<String, String> claims) {
        try {
            // Get the signing key from KeyService
            ECKey signingKey = keyService.getSigningJwk();

            // Build the VC structure
            Map<String, Object> vc = new HashMap<>();
            vc.put("@context", List.of(
                    "https://www.w3.org/2018/credentials/v1",
                    "https://example.org/credentials/v1"
            ));
            vc.put("type", List.of("VerifiableCredential", credentialType));

            // Build credential subject
            Map<String, Object> credentialSubject = new HashMap<>(claims);
            credentialSubject.put("id", holderDid);
            vc.put("credentialSubject", credentialSubject);

            // Build JWT claims set
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(dcpProperties.getConnectorDid())
                    .subject(holderDid)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(365 * 24 * 60 * 60))) // 1 year
                    .jwtID("urn:uuid:" + UUID.randomUUID())
                    .claim("vc", vc)
                    .build();

            // Create JWS header with key ID
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(signingKey.getKeyID())
                    .build();

            // Create and sign the JWT
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            JWSSigner signer = new ECDSASigner(signingKey);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign JWT VC", e);
        }
    }
}

