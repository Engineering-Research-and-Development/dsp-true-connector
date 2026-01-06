package it.eng.dcp.issuer.rest.api;

import it.eng.dcp.issuer.service.IssuerService;
import it.eng.dcp.issuer.service.api.IssuerAPIService;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/issuer", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class IssuerAPIController {

    private final IssuerAPIService issuerAPIService;

    public IssuerAPIController(IssuerAPIService issuerAPIService) {
        this.issuerAPIService = issuerAPIService;
    }

    /**
     * Approve and issue credentials endpoint.
     *
     * @param requestId The credential request ID
     * @param requestBody Optional request body with customClaims, constraints, or credentials
     * @return ResponseEntity with approval result or error
     */
    @PostMapping(path = "/requests/{requestId}/approve")
    public ResponseEntity<?> approveRequest(
            @PathVariable String requestId,
            @RequestBody(required = false) Map<String, Object> requestBody) {

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
                    requestId, customClaims, constraints, providedCredentials);

            log.info("Approved and delivered {} credentials for request {}: types={}",
                    result.getCredentialsCount(), requestId, result.getCredentialTypes());

            return ResponseEntity.ok(GenericApiResponse.success(result, "Credentials issued and delivered successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid approval request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(GenericApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error approving request: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(GenericApiResponse.error("Internal error: " + e.getMessage()));
        }
    }


    /**
     * Reject credential request endpoint.
     *
     * @param requestId The credential request ID
     * @param requestBody Request body containing rejection reason
     * @return ResponseEntity with rejection result or error
     */
    @PostMapping(path = "/requests/{requestId}/reject")
    public ResponseEntity<?> rejectRequest(
            @PathVariable String requestId,
            @RequestBody Map<String, String> requestBody) {

        String rejectionReason = requestBody.get("reason");
        if (rejectionReason == null || rejectionReason.isBlank()) {
            return ResponseEntity.badRequest().body(GenericApiResponse.error("Rejection reason is required"));
        }

        try {
            boolean success = issuerAPIService.rejectCredentialRequest(requestId, rejectionReason);
            if (success) {
                log.info("Rejected credential request {}: {}", requestId, rejectionReason);
                return ResponseEntity.ok(GenericApiResponse.success(null, "Credential request rejected successfully"));
            } else {
                return ResponseEntity.status(500).body(GenericApiResponse.error("Failed to reject request"));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid rejection request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(GenericApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error rejecting request: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(GenericApiResponse.error("Internal error: " + e.getMessage()));
        }
    }
}
