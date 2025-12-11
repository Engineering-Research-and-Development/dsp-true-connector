package it.eng.dcp.service;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.model.CredentialMessage;
import it.eng.dcp.model.CredentialRequest;
import it.eng.dcp.model.CredentialRequestMessage;
import it.eng.dcp.repository.CredentialRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Autowired
    public IssuerService(SelfIssuedIdTokenService tokenService,
                        CredentialRequestRepository requestRepository,
                        CredentialDeliveryService deliveryService,
                        CredentialIssuanceService issuanceService) {
        this.tokenService = tokenService;
        this.requestRepository = requestRepository;
        this.deliveryService = deliveryService;
        this.issuanceService = issuanceService;
    }

    /**
     * Authorize a credential request by validating the bearer token and matching holderPid.
     *
     * @param bearerToken The bearer token from Authorization header (without "Bearer " prefix)
     * @param holderPid The holder PID from the request message
     * @return JWTClaimsSet if valid
     * @throws SecurityException if token is invalid or holderPid doesn't match
     */
    public JWTClaimsSet authorizeRequest(String bearerToken, String holderPid) throws SecurityException {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new SecurityException("Bearer token is required");
        }

        JWTClaimsSet claims = tokenService.validateToken(bearerToken);
        String subject = claims.getSubject();

        if (subject == null || !subject.equals(holderPid)) {
            log.warn("Holder PID in message does not match token subject: msg={}, sub={}", holderPid, subject);
            throw new SecurityException("Token subject does not match holderPid");
        }

        return claims;
    }

    /**
     * Create and persist a credential request from the incoming message.
     *
     * @param msg The CredentialRequestMessage
     * @return The persisted CredentialRequest
     */
    public CredentialRequest createCredentialRequest(CredentialRequestMessage msg) {
        CredentialRequest req = CredentialRequest.fromMessage(msg);
        return requestRepository.save(req);
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
            // Auto-generate credentials with custom claims and constraints
            log.info("Auto-generating credentials for request {} based on requested credential IDs: {}",
                    requestId, credentialRequest.getCredentialIds());

            if (customClaims != null && !customClaims.isEmpty()) {
                log.info("Including custom claims in credentials: {}", customClaims.keySet());
            }

            if (constraintsData != null && !constraintsData.isEmpty()) {
                log.info("Applying {} constraints to credential generation", constraintsData.size());
            }

            credentials = issuanceService.generateCredentials(
                credentialRequest,
                customClaims,
                constraintsData
            );

            if (credentials.isEmpty()) {
                throw new IllegalStateException("Failed to generate credentials for the requested types");
            }
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

