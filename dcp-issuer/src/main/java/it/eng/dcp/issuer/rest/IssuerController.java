package it.eng.dcp.issuer.rest;

import it.eng.dcp.issuer.service.IssuerService;
import it.eng.dcp.model.CredentialRequest;
import it.eng.dcp.model.CredentialRequestMessage;
import it.eng.dcp.model.IssuerMetadata;
import it.eng.tools.response.GenericApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for issuer-side credential operations.
 */
@RestController
@RequestMapping(path = "/issuer", produces = MediaType.APPLICATION_JSON_VALUE)
public class IssuerController {

    private static final Logger LOG = LoggerFactory.getLogger(IssuerController.class);

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
    public ResponseEntity<?> getMetadata(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }
        String token = authorization.substring("Bearer ".length());

        try {
            issuerService.authorizeRequest(token, null);
            IssuerMetadata metadata = issuerService.getMetadata();
            return ResponseEntity.ok(metadata);
        } catch (SecurityException e) {
            LOG.warn("Authorization failed for metadata request: {}", e.getMessage());
            return ResponseEntity.status(401).body("Unauthorized: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error retrieving metadata: {}", e.getMessage(), e);
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
    @PostMapping(path = "/requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCredentialRequest(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody CredentialRequestMessage requestMessage) {

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }
        String token = authorization.substring("Bearer ".length());

        try {
            issuerService.authorizeRequest(token, requestMessage.getHolderPid());
            CredentialRequest request = issuerService.createCredentialRequest(requestMessage);

            LOG.info("Created credential request: issuerPid={}, holderPid={}, credentials={}",
                    request.getIssuerPid(), request.getHolderPid(), request.getCredentialIds());

            return ResponseEntity.ok(GenericApiResponse.success(request, "Credential request created successfully"));
        } catch (SecurityException e) {
            LOG.warn("Authorization failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(GenericApiResponse.error("Unauthorized: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(GenericApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error creating credential request: {}", e.getMessage(), e);
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

            LOG.info("Approved and delivered {} credentials for request {}: types={}",
                    result.getCredentialsCount(), requestId, result.getCredentialTypes());

            return ResponseEntity.ok(GenericApiResponse.success(result, "Credentials issued and delivered successfully"));
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid approval request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(GenericApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error approving request: {}", e.getMessage(), e);
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
                LOG.info("Rejected credential request {}: {}", requestId, rejectionReason);
                return ResponseEntity.ok(GenericApiResponse.success(null, "Credential request rejected successfully"));
            } else {
                return ResponseEntity.status(500).body(GenericApiResponse.error("Failed to reject request"));
            }
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid rejection request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(GenericApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error rejecting request: {}", e.getMessage(), e);
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
                    .map(request -> ResponseEntity.ok(GenericApiResponse.success(request, "Request found")))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            LOG.error("Error retrieving request: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(GenericApiResponse.error("Internal error: " + e.getMessage()));
        }
    }
}

