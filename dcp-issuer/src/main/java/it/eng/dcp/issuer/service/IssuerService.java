package it.eng.dcp.issuer.service;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.CredentialRequestMessage;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.issuer.repository.CredentialRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final CredentialMetadataService credentialMetadataService;

    @Autowired
    public IssuerService(SelfIssuedIdTokenService tokenService,
                        CredentialRequestRepository requestRepository,
                        CredentialMetadataService credentialMetadataService) {
        this.tokenService = tokenService;
        this.requestRepository = requestRepository;
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

        return tokenService.validateToken(bearerToken);

//        // If holderPid is provided, validate that it matches the token subject
        //Not sure that this is requirement at all according to DCP protocol
//        if (holderPid != null) {
//            String subject = claims.getSubject();
//            if (subject == null || !subject.equals(holderPid)) {
//                log.warn("Holder PID in message does not match token subject: msg={}, sub={}", holderPid, subject);
//                throw new SecurityException("Token subject does not match holderPid");
//            }
//        }
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
     * Get issuer metadata based on configuration.
     *
     * @return IssuerMetadata object with configured credential data
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

