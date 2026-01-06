package it.eng.dcp.issuer.service;

import it.eng.dcp.common.model.ConstraintRule;
import it.eng.dcp.common.model.CredentialGenerationContext;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.issuer.config.IssuerDidDocumentConfiguration;
import it.eng.dcp.issuer.config.IssuerProperties;
import it.eng.dcp.issuer.service.credential.CredentialGeneratorFactory;
import it.eng.dcp.issuer.service.jwt.VcJwtGeneratorFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for generating/issuing verifiable credentials based on credential requests.
 */
@Service
@Slf4j
public class CredentialIssuanceService {

    private final IssuerProperties issuerProperties;
    private final CredentialGeneratorFactory credentialGeneratorFactory;

    @Autowired
    public CredentialIssuanceService(IssuerProperties issuerProperties, KeyService keyService,
                                     IssuerDidDocumentConfiguration didDocumentConfig) {
        this.issuerProperties = issuerProperties;

        // Create JWT generator factory
        VcJwtGeneratorFactory jwtGeneratorFactory = new VcJwtGeneratorFactory(
                issuerProperties.getConnectorDid(),
                keyService,
                didDocumentConfig
        );

        // Create credential generator factory
        this.credentialGeneratorFactory = new CredentialGeneratorFactory(
                jwtGeneratorFactory,
                issuerProperties.getConnectorDid()
        );
    }

    /**
     * Generate credentials for an approved credential request (backward compatible).
     *
     * @param request The credential request
     * @return List of credential containers ready for delivery
     */
    public List<CredentialMessage.CredentialContainer> generateCredentials(CredentialRequest request) {
        return generateCredentials(request, null, null);
    }

    /**
     * Generate credentials with custom claims and constraints.
     *
     * @param request The credential request containing requested credential IDs
     * @param customClaims Optional custom claims to include
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
                    log.warn("Failed to parse constraint: {}", e.getMessage());
                }
            }
        }

        // Create generation context
        CredentialGenerationContext context = CredentialGenerationContext.withConstraints(
            request,
            customClaims != null ? customClaims : new HashMap<>(),
            constraints
        );

        log.info("Generating {} credentials for request {} (holder: {}) with {} custom claims and {} constraints",
                request.getCredentialIds().size(), request.getIssuerPid(), request.getHolderPid(),
                customClaims != null ? customClaims.size() : 0,
                constraints.size());

        List<CredentialMessage.CredentialContainer> credentials = new ArrayList<>();

        for (String credentialId : request.getCredentialIds()) {
            try {
                CredentialMessage.CredentialContainer credential = generateCredentialForType(credentialId, context);
                credentials.add(credential);
                log.debug("Generated credential type '{}' for holder {}", credentialId, request.getHolderPid());
            } catch (Exception e) {
                log.error("Failed to generate credential '{}' for request {}: {}",
                        credentialId, request.getIssuerPid(), e.getMessage(), e);
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
        String credentialType = extractCredentialType(credentialId);

        return credentialGeneratorFactory
                .createGenerator(credentialType)
                .generateCredential(context);
    }

    /**
     * Extract credential type from credential ID.
     *
     * @param credentialId The credential ID to extract type from
     * @return The credential type string
     */
    private String extractCredentialType(String credentialId) {
        if (credentialId.endsWith("Credential")) {
            return credentialId;
        }
        return "MembershipCredential";
    }
}
