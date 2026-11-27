package it.eng.dcp.issuer.service;

import it.eng.dcp.common.model.*;
import it.eng.dcp.issuer.config.CredentialMetadataConfig;
import it.eng.dcp.issuer.config.CredentialMetadataConfigLoader;
import it.eng.dcp.issuer.config.IssuerProperties;
import it.eng.dcp.issuer.service.credential.CredentialGeneratorFactory;
import it.eng.dcp.issuer.service.credential.ProfileExtractor;
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
    private final StatusListService statusListService;
    private final ProfileExtractor profileExtractor = new ProfileExtractor();
    private final CredentialMetadataConfigLoader credentialMetadataConfigLoader;

    @Autowired
    public CredentialIssuanceService(IssuerProperties issuerProperties, IssuerIdentity issuerIdentity,
                                     StatusListService statusListService,
                                     CredentialMetadataConfigLoader credentialMetadataConfigLoader) {
        this.issuerProperties = issuerProperties;
        this.statusListService = statusListService;
        this.credentialMetadataConfigLoader = credentialMetadataConfigLoader;

        // Create JWT generator factory
        VcJwtGeneratorFactory jwtGeneratorFactory = new VcJwtGeneratorFactory(issuerIdentity);

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
     * @return List of credential containers ready for delivery, one for each requested credential
     * @throws CredentialIssuanceException if any requested credential could not be issued; nothing is returned
     *         for such a request, so it can be rejected as a whole
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

        // Resolve every requested ID to its credentialsSupported entry (DCP §6.4.1) before generating anything
        CredentialMetadataConfig metadataConfig = credentialMetadataConfigLoader.credentialMetadataConfig();
        List<CredentialMetadataConfig.CredentialConfig> requestedConfigs = new ArrayList<>();
        for (String credentialId : request.getCredentialIds()) {
            CredentialMetadataConfig.CredentialConfig config = metadataConfig.findById(credentialId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Credential '" + credentialId + "' is not supported by this issuer"));
            if (config.getCredentialType() == null || config.getCredentialType().isBlank()) {
                throw new IllegalStateException("Supported credential '" + credentialId + "' has no credentialType");
            }
            requestedConfigs.add(config);
        }

        List<CredentialMessage.CredentialContainer> credentials = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        log.info("Generating {} credentials for request {} (holder: {}) with {} custom claims and {} constraints",
                requestedConfigs.size(), request.getIssuerPid(), request.getHolderPid(),
                customClaims != null ? customClaims.size() : 0,
                constraints.size());
        for (CredentialMetadataConfig.CredentialConfig config : requestedConfigs) {
            try {
                // The profile is carried per credential, keyed by its own type, so requested IDs that share
                // a credential type cannot overwrite each other's profile
                Map<String, Object> claimsWithMetadata = customClaims != null ? new HashMap<>(customClaims) : new HashMap<>();
                Map<String, Map<String, Object>> credentialMetadata = new HashMap<>();
                if (config.getProfile() != null) {
                    credentialMetadata.put(config.getCredentialType(), Map.of("profile", config.getProfile()));
                }
                claimsWithMetadata.put("__credentialMetadata", credentialMetadata);

                CredentialGenerationContext context = CredentialGenerationContext.withConstraints(
                    request,
                    claimsWithMetadata,
                    constraints
                );
                CredentialMessage.CredentialContainer generated =
                        generateCredentialForType(config.getCredentialType(), context);
                credentials.add(withFormatOfProfile(generated, config.getProfile()));
                log.debug("Generated credential '{}' of type '{}' for holder {}",
                        config.getId(), config.getCredentialType(), request.getHolderPid());
            } catch (Exception e) {
                log.error("Failed to generate credential '{}' for request {}: {}",
                        config.getId(), request.getIssuerPid(), e.getMessage(), e);
                failedIds.add(config.getId());
            }
        }

        if (!failedIds.isEmpty()) {
            // A request is all or nothing (DCP 1.0.1 §6.5.1): drop the credentials that were generated
            throw new CredentialIssuanceException(
                    "Failed to issue credential(s) " + failedIds + " for request " + request.getIssuerPid(),
                    failedIds);
        }

        return credentials;
    }

    /**
     * Sets the container format from the profile of the credential's own credentialsSupported entry (for
     * example {@code vc11-sl2021/jwt} gives {@code jwt}), so credentials sharing a type keep their own format.
     *
     * @param container the generated credential container
     * @param profile the configured profile, or null to keep the generator's format
     * @return the container with the profile's format
     */
    private static CredentialMessage.CredentialContainer withFormatOfProfile(
            CredentialMessage.CredentialContainer container, String profile) {
        if (profile == null) {
            return container;
        }
        int slash = profile.lastIndexOf('/');
        if (slash <= 0 || slash == profile.length() - 1) {
            return container;
        }
        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType(container.getCredentialType())
                .payload(container.getPayload())
                .format(profile.substring(slash + 1))
                .build();
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
     * Generate a single credential of the given type.
     *
     * @param credentialType The credential type of the resolved credentialsSupported entry
     * @param context The generation context with request, claims, and constraints
     * @return A credential container with the generated credential
     */
    private CredentialMessage.CredentialContainer generateCredentialForType(
            String credentialType,
            CredentialGenerationContext context) {
        // Use ProfileExtractor to get the profile
        var profile = profileExtractor.extractProfile(credentialType, context);
//        var profile = context.getProfileId();
        if (profile == ProfileId.VC11_SL2021_JWT || profile == ProfileId.VC20_BSSL_JWT) {
            StatusListService.StatusListEntry entry = statusListService.allocateStatusEntry(
                    issuerProperties.getConnectorDid(), StatusListService.REVOCATION_PURPOSE, profile);
            return credentialGeneratorFactory
                    .createGeneratorWithStatus(credentialType)
                    .generateCredential(context, entry.statusListCredential(), entry.index());
        }
        // Always use fallback generator if profile is null or not a status list type
        return credentialGeneratorFactory
                .createGenerator(credentialType)
                .generateCredential(context);
    }
}
