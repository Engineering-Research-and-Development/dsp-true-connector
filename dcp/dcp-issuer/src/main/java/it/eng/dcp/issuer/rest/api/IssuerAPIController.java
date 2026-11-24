package it.eng.dcp.issuer.rest.api;

import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.RequestParticipantContext;
import it.eng.dcp.issuer.service.IssuerService;
import it.eng.dcp.issuer.service.api.IssuerAPIService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping(path = "/api/issuer", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class IssuerAPIController {

    private final IssuerAPIService issuerAPIService;
    private final RequestParticipantContext requestParticipantContext;

    public IssuerAPIController(IssuerAPIService issuerAPIService, RequestParticipantContext requestParticipantContext) {
        this.issuerAPIService = issuerAPIService;
        this.requestParticipantContext = requestParticipantContext;
    }

    /**
     * Approve and issue credentials endpoint.
     *
     * @param requestId The credential request ID
     * @param requestBody Optional request body with customClaims, constraints, or credentials
     * @param request The inbound request, which addresses the participant
     * @param principal The authenticated administration principal
     * @return ResponseEntity with approval result or error
     */
    @PostMapping(path = "/requests/{requestId}/approve")
    public ResponseEntity<?> approveRequest(
            @PathVariable String requestId,
            @RequestBody(required = false) Map<String, Object> requestBody,
            HttpServletRequest request, Principal principal) {
        ParticipantContext participant = requestParticipantContext.requireForAdmin(request, principal);

        try {
            Map<String, Object> customClaims = null;
            List<Map<String, Object>> constraints = null;
            List<Map<String, Object>> providedCredentials = null;

            if (requestBody != null) {
                customClaims = (Map<String, Object>) requestBody.get("customClaims");
                constraints = (List<Map<String, Object>>) requestBody.get("constraints");
                providedCredentials = (List<Map<String, Object>>) requestBody.get("credentials");
            }

            IssuerService.ApprovalResult result = issuerAPIService.approveAndDeliverCredentials(
                    requestId, customClaims, constraints, providedCredentials, participant);

            log.info("Approved and delivered {} credentials for request {}: types={}",
                    result.getCredentialsCount(), requestId, result.getCredentialTypes());

            return ResponseEntity.ok("Credentials issued and delivered successfully");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid approval request: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid approval request: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error approving request: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal error: " + e.getMessage());
        }
    }


    /**
     * Reject credential request endpoint.
     *
     * @param requestId The credential request ID
     * @param requestBody Request body containing rejection reason
     * @param request The inbound request, which addresses the participant
     * @param principal The authenticated administration principal
     * @return ResponseEntity with rejection result or error
     */
    @PostMapping(path = "/requests/{requestId}/reject")
    public ResponseEntity<?> rejectRequest(
            @PathVariable String requestId,
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request, Principal principal) {
        ParticipantContext participant = requestParticipantContext.requireForAdmin(request, principal);

        String rejectionReason = requestBody.get("reason");
        if (rejectionReason == null || rejectionReason.isBlank()) {
            return ResponseEntity.badRequest().body("Rejection reason is required");
        }

        try {
            boolean success = issuerAPIService.rejectCredentialRequest(requestId, rejectionReason, participant);
            if (success) {
                log.info("Rejected credential request {}: {}", requestId, rejectionReason);
                return ResponseEntity.ok("Credential request rejected successfully");
            } else {
                return ResponseEntity.status(500).body("Failed to reject request");
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid rejection request: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid rejection request: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error rejecting request: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal error: " + e.getMessage());
        }
    }

    /**
     * Revoke a credential by its status list entry endpoint.
     * Sets the entry's status bit to 1 and re-signs the published status list. Revocation is not reversible.
     *
     * @param listId the status list ID, the last path segment of the credential's {@code statusListCredential}
     * @param index the credential's {@code statusListIndex}
     * @param request The inbound request, which addresses the participant
     * @param principal The authenticated administration principal
     * @return 200 when the entry is revoked (also when it already was), 404 when the list does not exist,
     *         400 when the index has not been allocated, 500 on any other failure
     */
    @PostMapping(path = "/status-lists/{listId}/entries/{index}/revoke")
    public ResponseEntity<String> revokeStatusEntry(@PathVariable String listId, @PathVariable int index,
                                                     HttpServletRequest request, Principal principal) {
        ParticipantContext participant = requestParticipantContext.requireForAdmin(request, principal);
        try {
            boolean revoked = issuerAPIService.revokeStatusEntry(listId, index, participant);
            return ResponseEntity.ok(revoked ? "Credential revoked" : "Credential already revoked");
        } catch (NoSuchElementException e) {
            log.warn("Revocation for unknown status list {}", listId);
            return ResponseEntity.status(404).body("Status list not found");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid revocation request: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid revocation request: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error revoking status list entry: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal error: " + e.getMessage());
        }
    }
}
