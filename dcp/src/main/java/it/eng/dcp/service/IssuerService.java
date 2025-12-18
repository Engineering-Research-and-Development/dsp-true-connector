package it.eng.dcp.service;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.model.CredentialMessage;
import it.eng.dcp.model.CredentialRequest;
import it.eng.dcp.model.CredentialRequestMessage;
import it.eng.dcp.model.IssuerMetadata;
import it.eng.dcp.repository.CredentialRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service layer for issuer-side credential request processing.
 * Handles authorization, request creation, approval, and rejection logic.
 */
@Service
@Slf4j
public class IssuerService {

    private final SelfIssuedIdTokenService tokenService;
    private final CredentialRequestRepository requestRepository;
    private final CredentialDeliveryService deliveryService;
    private final CredentialIssuanceService issuanceService;
    private final CredentialMetadataService credentialMetadataService;

    @Autowired
    public IssuerService(SelfIssuedIdTokenService tokenService,
                        CredentialRequestRepository requestRepository,
                        CredentialDeliveryService deliveryService,
                        CredentialIssuanceService issuanceService,
                        CredentialMetadataService credentialMetadataService) {
        this.tokenService = tokenService;
        this.requestRepository = requestRepository;
        this.deliveryService = deliveryService;
        this.issuanceService = issuanceService;
        this.credentialMetadataService = credentialMetadataService;
    }

    /**
     * Authorize a credential request by validating the bearer token and matching holderPid.
     *
     * @param bearerToken The bearer token from Authorization header (without "Bearer " prefix)
     * @param holderPid The holder PID from the request message (can be null for endpoints that don't require matching)
     * @return JWTClaimsSet if valid
     * @throws SecurityException if token is invalid or holderPid doesn't match
     */
    public JWTClaimsSet authorizeRequest(String bearerToken, String holderPid) throws SecurityException {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new SecurityException("Bearer token is required");
        }

        JWTClaimsSet claims = tokenService.validateToken(bearerToken);

        // If holderPid is provided, validate that it matches the token subject
        if (holderPid != null) {
            String subject = claims.getSubject();
            if (subject == null || !subject.equals(holderPid)) {
                log.warn("Holder PID in message does not match token subject: msg={}, sub={}", holderPid, subject);
                throw new SecurityException("Token subject does not match holderPid");
            }
        }

        return claims;
    }

    /**
     * Create and persist a credential request from the incoming message.
     * Validates that all requested credentials are supported by the issuer.
     *
     * @param msg The CredentialRequestMessage
     * @return The persisted CredentialRequest
     * @throws IllegalArgumentException if any requested credential type is not supported
     */
    public CredentialRequest createCredentialRequest(CredentialRequestMessage msg) {
        // Validate that all requested credentials are supported
        validateRequestedCredentials(msg);

        CredentialRequest req = CredentialRequest.fromMessage(msg);
        return requestRepository.save(req);
    }

    /**
     * Validate that all requested credentials in the message are supported by the issuer.
     *
     * @param msg The CredentialRequestMessage to validate
     * @throws IllegalArgumentException if any requested credential is not supported
     */
    private void validateRequestedCredentials(CredentialRequestMessage msg) {
        // Get supported credential types from issuer metadata
        IssuerMetadata metadata;
        try {
            metadata = credentialMetadataService.buildIssuerMetadata();
        } catch (IllegalStateException e) {
            log.error("Cannot validate credential request - issuer metadata not available: {}", e.getMessage());
            throw new IllegalStateException("Issuer metadata not configured. Cannot process credential requests.", e);
        }

        List<String> supportedCredentialTypes = metadata.getCredentialsSupported().stream()
                .map(IssuerMetadata.CredentialObject::getCredentialType)
                .toList();

        // Validate each requested credential
        for (var credentialRef : msg.getCredentials()) {
            String requestedId = credentialRef.getId();

            // Check if the requested credential ID matches any supported credential type
            if (!supportedCredentialTypes.contains(requestedId)) {
                log.warn("Unsupported credential type requested: {}. Supported types: {}",
                        requestedId, supportedCredentialTypes);
                throw new IllegalArgumentException(
                        String.format("Credential type '%s' is not supported by this issuer. Supported types: %s",
                                requestedId, supportedCredentialTypes));
            }
        }

        log.debug("All requested credentials are supported: {}",
                msg.getCredentials().stream().map(c -> c.getId()).toList());
    }

    /**
     * Retrieve a credential request by issuerPid.
     *
     * @param requestId The issuerPid identifier
     * @return Optional containing the request if found
     */
    public Optional<CredentialRequest> getRequestByIssuerPid(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return requestRepository.findByIssuerPid(requestId);
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
    public ApprovalResult approveAndDeliverCredentials(String requestId,
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

        return new ApprovalResult(
                true,
                credentials.size(),
                credentials.stream()
                        .map(CredentialMessage.CredentialContainer::getCredentialType)
                        .toList()
        );
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

        // Create a metadata lookup map for quick access
        Map<String, Map<String, Object>> metadataByType = new java.util.HashMap<>();

        for (IssuerMetadata.CredentialObject credObj : metadata.getCredentialsSupported()) {
            Map<String, Object> credMetadata = new java.util.HashMap<>();
            credMetadata.put("profile", credObj.getProfile());
            credMetadata.put("bindingMethods", credObj.getBindingMethods());
            credMetadata.put("schema", credObj.getCredentialSchema());
            metadataByType.put(credObj.getCredentialType(), credMetadata);
        }

        // Add metadata lookup to claims so credential generation can access it
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

        // Create lookup map for credential metadata
        Map<String, IssuerMetadata.CredentialObject> metadataMap = new java.util.HashMap<>();
        for (IssuerMetadata.CredentialObject credObj : metadata.getCredentialsSupported()) {
            metadataMap.put(credObj.getCredentialType(), credObj);
        }

        List<CredentialMessage.CredentialContainer> updatedCredentials = new java.util.ArrayList<>();

        for (CredentialMessage.CredentialContainer credential : credentials) {
            IssuerMetadata.CredentialObject credentialMetadata = metadataMap.get(credential.getCredentialType());

            if (credentialMetadata != null && credentialMetadata.getProfile() != null) {
                // Extract format from profile (e.g., "vc11-sl2021/jwt" -> "jwt")
                String format = extractFormatFromProfile(credentialMetadata.getProfile());

                log.debug("Applying metadata to credential type '{}': profile='{}', format='{}'",
                        credential.getCredentialType(), credentialMetadata.getProfile(), format);

                // Rebuild credential with correct format
                CredentialMessage.CredentialContainer updated =
                        CredentialMessage.CredentialContainer.Builder.newInstance()
                                .credentialType(credential.getCredentialType())
                                .payload(credential.getPayload())
                                .format(format)
                                .build();
                updatedCredentials.add(updated);
            } else {
                // No metadata found, use credential as-is
                log.debug("No metadata found for credential type '{}', using default format",
                        credential.getCredentialType());
                updatedCredentials.add(credential);
            }
        }

        return updatedCredentials;
    }

    /**
     * Extract format from profile string.
     * Profile format: "vcVersion-suite/format" (e.g., "vc11-sl2021/jwt", "vc11-sl2021/jsonld")
     *
     * @param profile Profile string
     * @return Format extracted from profile (e.g., "jwt", "jsonld"), defaults to "jwt" if not parseable
     */
    private String extractFormatFromProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return "jwt";
        }

        // Profile format: "vc11-sl2021/jwt" or "vc10-sl2021/jsonld"
        int slashIndex = profile.lastIndexOf('/');
        if (slashIndex > 0 && slashIndex < profile.length() - 1) {
            String format = profile.substring(slashIndex + 1);
            log.debug("Extracted format '{}' from profile '{}'", format, profile);
            return format;
        }

        // Fallback to jwt if profile doesn't contain format
        log.debug("Could not extract format from profile '{}', using default 'jwt'", profile);
        return "jwt";
    }

    /**
     * Reject a credential request with a reason.
     *
     * @param requestId The issuerPid identifier
     * @param rejectionReason The reason for rejection
     * @return true if successful
     * @throws IllegalArgumentException if request not found
     */
    public boolean rejectCredentialRequest(String requestId, String rejectionReason) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }

        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason is required");
        }

        // Verify request exists before attempting rejection
        requestRepository.findByIssuerPid(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Credential request not found: " + requestId));

        return deliveryService.rejectCredentialRequest(requestId, rejectionReason);
    }

    /**
     * Convert provided credential data maps to CredentialContainer objects.
     *
     * @param credentialsData List of maps containing credential data (credentialType, payload, format)
     * @return List of CredentialContainer objects
     * @throws IllegalArgumentException if any credential data is invalid
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
     * Get issuer metadata based on configuration.
     * This endpoint returns the issuer's metadata including supported credentials and issuance policies.
     * Credentials are loaded from configuration (dcp.credentials.supported) or defaults are used.
     *
     * @return IssuerMetadata object with configured or default credential data
     */
    public IssuerMetadata getMetadata() {
        return credentialMetadataService.buildIssuerMetadata();
    }

    /**
     * Result object for approval operations.
     */
    public static class ApprovalResult {
        private final boolean success;
        private final int credentialsCount;
        private final List<String> credentialTypes;

        public ApprovalResult(boolean success, int credentialsCount, List<String> credentialTypes) {
            this.success = success;
            this.credentialsCount = credentialsCount;
            this.credentialTypes = credentialTypes;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getCredentialsCount() {
            return credentialsCount;
        }

        public List<String> getCredentialTypes() {
            return credentialTypes;
        }
    }
}

