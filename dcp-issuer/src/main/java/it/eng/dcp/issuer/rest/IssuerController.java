package it.eng.dcp.issuer.rest;

import it.eng.dcp.issuer.service.IssuerService;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.CredentialRequestMessage;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for issuer-side credential operations.
 */
@RestController
@RequestMapping(path = "/issuer", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class IssuerController {

    private final IssuerService issuerService;

    @Autowired
    public IssuerController(IssuerService issuerService) {
        this.issuerService = issuerService;
    }

    /**
     * Get Issuer Metadata endpoint.
     *
     * @param authorization HTTP Authorization header with Bearer token
     * @return ResponseEntity with issuer metadata or error
     */
    @GetMapping(path = "/metadata")
    public ResponseEntity<?> getMetadata(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }
        String token = authorization.substring("Bearer ".length());

        try {
            issuerService.authorizeRequest(token, null);
            IssuerMetadata metadata = issuerService.getMetadata();
            return ResponseEntity.ok(metadata);
        } catch (SecurityException e) {
            log.warn("Authorization failed for metadata request: {}", e.getMessage());
            return ResponseEntity.status(401).body("Unauthorized: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error retrieving metadata: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal error: " + e.getMessage());
        }
    }

    /**
     * Create credential request endpoint.
     *
     * @param authorization HTTP Authorization header with Bearer token
     * @param requestMessage The credential request message
     * @return ResponseEntity with created request or error
     */
    @PostMapping(path = "/credentials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCredentialRequest(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody CredentialRequestMessage requestMessage) {

        log.info("Received credential request creation");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }
        String token = authorization.substring("Bearer ".length());

        try {
            issuerService.authorizeRequest(token, requestMessage.getHolderPid());
            CredentialRequest request = issuerService.createCredentialRequest(requestMessage);

            log.info("Created credential request: issuerPid={}, holderPid={}, credentials={}",
                    request.getIssuerPid(), request.getHolderPid(), request.getCredentialIds());

            String location = "/issuer/requests/" + request.getIssuerPid();
            return ResponseEntity.created(URI.create(location)).header(HttpHeaders.LOCATION, location).build();
        } catch (SecurityException e) {
            log.warn("Authorization failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(GenericApiResponse.error("Unauthorized: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(GenericApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating credential request: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(GenericApiResponse.error("Internal error: " + e.getMessage()));
        }
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

            IssuerService.ApprovalResult result = issuerService.approveAndDeliverCredentials(
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
            boolean success = issuerService.rejectCredentialRequest(requestId, rejectionReason);
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

    /**
     * Get credential request by ID endpoint.
     *
     * @param requestId The credential request ID
     * @return ResponseEntity with the request or error
     */
    @GetMapping(path = "/requests/{requestId}")
    public ResponseEntity<?> getRequest(@PathVariable String requestId) {
        try {
            return issuerService.getRequestByIssuerPid(requestId)
                    .map(r -> {
                        Map<String, Object> body = new HashMap<>();
                        body.put("type", "CredentialStatus");
                        body.put("@context", List.of(DSpaceConstants.DCP_CONTEXT));
                        body.put("issuerPid", r.getIssuerPid());
                        body.put("holderPid", r.getHolderPid());
                        body.put("status", r.getStatus() != null ? r.getStatus().toString() : null);
                        body.put("rejectionReason", r.getRejectionReason());
//                        body.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
                        return ResponseEntity.ok(body);
                    })
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error retrieving request: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(GenericApiResponse.error("Internal error: " + e.getMessage()));
        }
    }
}
