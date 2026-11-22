package it.eng.dcp.issuer.service.api;

import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.repository.CredentialRequestRepository;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.issuer.service.CredentialDeliveryService;
import it.eng.dcp.issuer.service.CredentialIssuanceService;
import it.eng.dcp.issuer.service.IssuerService;
import it.eng.dcp.issuer.service.StatusListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class IssuerAPIService {

    private static final String ISSUER_SOURCE = "issuer";

    private final CredentialRequestRepository requestRepository;
    private final CredentialIssuanceService issuanceService;
    private final CredentialDeliveryService deliveryService;
    private final DcpAuditEventPublisher auditPublisher;
    private final StatusListService statusListService;

    public IssuerAPIService(CredentialRequestRepository requestRepository,
                            CredentialIssuanceService issuanceService,
                            CredentialDeliveryService deliveryService,
                            DcpAuditEventPublisher auditPublisher,
                            StatusListService statusListService) {
        this.requestRepository = requestRepository;
        this.issuanceService = issuanceService;
        this.deliveryService = deliveryService;
        this.auditPublisher = auditPublisher;
        this.statusListService = statusListService;
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

            // The issuance service resolves each requested ID to its credentialsSupported entry and sets
            // that entry's type, profile and format on the generated credential
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

        List<String> credentialTypes = credentials.stream()
                .map(CredentialMessage.CredentialContainer::getCredentialType)
                .toList();
        auditPublisher.publishEvent(
                DcpAuditEventType.CREDENTIAL_APPROVED,
                "Credential request approved and delivered: " + requestId,
                ISSUER_SOURCE,
                credentialRequest.getHolderDid(), null,
                credentialTypes,
                requestId,
                Map.of("requestId", requestId, "credentialsCount", credentials.size(), "credentialTypes", credentialTypes));

        return new IssuerService.ApprovalResult(
                true,
                credentials.size(),
                credentialTypes
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
     * Revoke the credential that holds a status list entry: sets the entry's status bit and re-signs the list.
     * Revocation is not reversible; revoking an entry twice changes nothing and emits no second audit event.
     *
     * @param statusListId the status list ID, the last path segment of the credential's {@code statusListCredential}
     * @param index the credential's {@code statusListIndex}
     * @return true when the entry was revoked by this call, false when it was already revoked
     * @throws java.util.NoSuchElementException if the status list does not exist
     * @throws IllegalArgumentException if the index has not been allocated
     */
    public boolean revokeStatusEntry(String statusListId, int index) {
        boolean revoked = statusListService.revoke(statusListId, index);
        if (revoked) {
            auditPublisher.publishEvent(
                    DcpAuditEventType.CREDENTIAL_REVOKED,
                    "Credential revoked: status list " + statusListId + ", index " + index,
                    ISSUER_SOURCE,
                    null, null, null, null,
                    Map.of("statusListId", statusListId, "statusListIndex", index));
        }
        return revoked;
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

}
