package it.eng.dcp.issuer.service.api;

import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.issuer.repository.CredentialRequestRepository;
import it.eng.dcp.issuer.service.CredentialDeliveryService;
import it.eng.dcp.issuer.service.CredentialIssuanceService;
import it.eng.dcp.issuer.service.CredentialMetadataService;
import it.eng.dcp.issuer.service.IssuerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class IssuerAPIService {

    private final CredentialRequestRepository requestRepository;
    private final CredentialMetadataService credentialMetadataService;
    private final CredentialIssuanceService issuanceService;
    private final CredentialDeliveryService deliveryService;

    public IssuerAPIService(CredentialRequestRepository requestRepository, CredentialMetadataService credentialMetadataService, CredentialIssuanceService issuanceService, CredentialDeliveryService deliveryService) {
        this.requestRepository = requestRepository;
        this.credentialMetadataService = credentialMetadataService;
        this.issuanceService = issuanceService;
        this.deliveryService = deliveryService;
    }

    /**
     * Approve and deliver credentials for a pending credential request.
     *
     * @param requestId The issuerPid identifier of the credential request
     * @param customClaims Optional custom claims to include in generated credentials (e.g., country_code, role)
     * @param constraintsData Optional constraints to verify before issuance
     * @param providedCredentials Optional list of manually provided credentials (can be null for auto-generation)
     * @return ApprovalResult containing success status and metadata
     * @throws IllegalArgumentException if request not found or constraint verification fails
     */
    public IssuerService.ApprovalResult approveAndDeliverCredentials(String requestId,
                                                                     Map<String, Object> customClaims,
                                                                     List<Map<String, Object>> constraintsData,
                                                                     List<Map<String, Object>> providedCredentials) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }

        CredentialRequest credentialRequest = requestRepository.findByIssuerPid(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Credential request not found: " + requestId));

        List<CredentialMessage.CredentialContainer> credentials;

        if (providedCredentials != null && !providedCredentials.isEmpty()) {
            // Manual credential provision
            log.info("Using manually provided credentials for request {}", requestId);
            credentials = convertToCredentialContainers(providedCredentials);
        } else {
            // Auto-generate credentials with custom claims, constraints, and metadata
            log.info("Auto-generating credentials for request {} based on requested credential IDs: {}",
                    requestId, credentialRequest.getCredentialIds());

            if (customClaims != null && !customClaims.isEmpty()) {
                log.info("Including custom claims in credentials: {}", customClaims.keySet());
            }

            if (constraintsData != null && !constraintsData.isEmpty()) {
                log.info("Applying {} constraints to credential generation", constraintsData.size());
            }

            // Retrieve issuer metadata to use profile/format information
            IssuerMetadata metadata = credentialMetadataService.buildIssuerMetadata();

            // Enrich custom claims with metadata-based profile information
            Map<String, Object> enrichedClaims = enrichClaimsWithMetadata(
                    credentialRequest.getCredentialIds(),
                    metadata,
                    customClaims);

            credentials = issuanceService.generateCredentials(
                    credentialRequest,
                    enrichedClaims,
                    constraintsData
            );

            if (credentials.isEmpty()) {
                throw new IllegalStateException("Failed to generate credentials for the requested types");
            }

            // Apply metadata profile/format to generated credentials
            credentials = applyMetadataToCredentials(credentials, metadata);
        }

        boolean success = deliveryService.deliverCredentials(requestId, credentials);

        if (!success) {
            throw new IllegalStateException("Failed to deliver credentials to holder");
        }

        return new IssuerService.ApprovalResult(
                true,
                credentials.size(),
                credentials.stream()
                        .map(CredentialMessage.CredentialContainer::getCredentialType)
                        .toList()
        );
    }

    /**
     * Reject a credential request with a reason.
     *
     * @param requestId The issuer PID identifier
     * @param rejectionReason The reason for rejection
     * @return true if successful
     */
    public boolean rejectCredentialRequest(String requestId, String rejectionReason) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }

        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason is required");
        }

        requestRepository.findByIssuerPid(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Credential request not found: " + requestId));

        return deliveryService.rejectCredentialRequest(requestId, rejectionReason);
    }

    /**
     * Convert provided credential data maps to CredentialContainer objects.
     *
     * @param credentialsData List of maps containing credential data
     * @return List of CredentialContainer objects
     */
    private List<CredentialMessage.CredentialContainer> convertToCredentialContainers(
            List<Map<String, Object>> credentialsData) {

        List<CredentialMessage.CredentialContainer> credentials = new java.util.ArrayList<>();

        for (Map<String, Object> credData : credentialsData) {
            String credentialType = (String) credData.get("credentialType");
            Object payload = credData.get("payload");
            String format = (String) credData.get("format");

            if (credentialType == null || payload == null || format == null) {
                throw new IllegalArgumentException(
                        "Each credential must have credentialType, payload, and format");
            }

            CredentialMessage.CredentialContainer container =
                    CredentialMessage.CredentialContainer.Builder.newInstance()
                            .credentialType(credentialType)
                            .payload(payload)
                            .format(format)
                            .build();
            credentials.add(container);
        }

        return credentials;
    }

    /**
     * Enrich custom claims with metadata information for credential types.
     *
     * @param credentialIds List of credential IDs being generated
     * @param metadata Issuer metadata containing credential configurations
     * @param customClaims Existing custom claims (can be null)
     * @return Enriched claims map with metadata information
     */
    private Map<String, Object> enrichClaimsWithMetadata(List<String> credentialIds,
                                                         IssuerMetadata metadata,
                                                         Map<String, Object> customClaims) {
        Map<String, Object> enriched = new java.util.HashMap<>(customClaims != null ? customClaims : Map.of());

        Map<String, Map<String, Object>> metadataByType = new java.util.HashMap<>();

        for (IssuerMetadata.CredentialObject credObj : metadata.getCredentialsSupported()) {
            Map<String, Object> credMetadata = new java.util.HashMap<>();
            credMetadata.put("profile", credObj.getProfile());
            credMetadata.put("bindingMethods", credObj.getBindingMethods());
            credMetadata.put("schema", credObj.getCredentialSchema());
            metadataByType.put(credObj.getCredentialType(), credMetadata);
        }

        enriched.put("__credentialMetadata", metadataByType);

        log.debug("Enriched claims with metadata for {} credential types", metadataByType.size());
        return enriched;
    }

    /**
     * Apply metadata profile and format information to generated credentials.
     *
     * @param credentials List of generated credentials
     * @param metadata Issuer metadata containing credential configurations
     * @return Updated credentials with proper format based on metadata
     */
    private List<CredentialMessage.CredentialContainer> applyMetadataToCredentials(
            List<CredentialMessage.CredentialContainer> credentials,
            IssuerMetadata metadata) {

        Map<String, IssuerMetadata.CredentialObject> metadataMap = new java.util.HashMap<>();
        for (IssuerMetadata.CredentialObject credObj : metadata.getCredentialsSupported()) {
            metadataMap.put(credObj.getCredentialType(), credObj);
        }

        List<CredentialMessage.CredentialContainer> updatedCredentials = new java.util.ArrayList<>();

        for (CredentialMessage.CredentialContainer credential : credentials) {
            IssuerMetadata.CredentialObject credentialMetadata = metadataMap.get(credential.getCredentialType());

            if (credentialMetadata != null && credentialMetadata.getProfile() != null) {
                String format = extractFormatFromProfile(credentialMetadata.getProfile());

                log.debug("Applying metadata to credential type '{}': profile='{}', format='{}'",
                        credential.getCredentialType(), credentialMetadata.getProfile(), format);

                CredentialMessage.CredentialContainer updated =
                        CredentialMessage.CredentialContainer.Builder.newInstance()
                                .credentialType(credential.getCredentialType())
                                .payload(credential.getPayload())
                                .format(format)
                                .build();
                updatedCredentials.add(updated);
            } else {
                log.debug("No metadata found for credential type '{}', using default format",
                        credential.getCredentialType());
                updatedCredentials.add(credential);
            }
        }

        return updatedCredentials;
    }

    /**
     * Extract format from profile string.
     *
     * @param profile The profile string
     * @return Format extracted from profile (e.g., "jwt", "jsonld")
     */
    private String extractFormatFromProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return "jwt";
        }

        int slashIndex = profile.lastIndexOf('/');
        if (slashIndex > 0 && slashIndex < profile.length() - 1) {
            String format = profile.substring(slashIndex + 1);
            log.debug("Extracted format '{}' from profile '{}'", format, profile);
            return format;
        }

        log.debug("Could not extract format from profile '{}', using default 'jwt'", profile);
        return "jwt";
    }
}
