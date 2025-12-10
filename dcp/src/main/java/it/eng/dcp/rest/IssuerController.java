package it.eng.dcp.rest;

import it.eng.dcp.model.CredentialRequest;
import it.eng.dcp.model.CredentialRequestMessage;
import it.eng.dcp.model.CredentialMessage;
import it.eng.dcp.repository.CredentialRequestRepository;
import it.eng.dcp.service.CredentialDeliveryService;
import it.eng.dcp.service.CredentialIssuanceService;
import it.eng.dcp.service.SelfIssuedIdTokenService;
import it.eng.tools.response.GenericApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping(path = "/issuer", produces = MediaType.APPLICATION_JSON_VALUE)
public class IssuerController {

    private static final Logger LOG = LoggerFactory.getLogger(IssuerController.class);

    private final SelfIssuedIdTokenService tokenService;
    private final CredentialRequestRepository requestRepository;
    private final CredentialDeliveryService deliveryService;
    private final CredentialIssuanceService issuanceService;

    @Autowired
    public IssuerController(SelfIssuedIdTokenService tokenService,
                           CredentialRequestRepository requestRepository,
                           CredentialDeliveryService deliveryService,
                           CredentialIssuanceService issuanceService) {
        this.tokenService = tokenService;
        this.requestRepository = requestRepository;
        this.deliveryService = deliveryService;
        this.issuanceService = issuanceService;
    }

    /**
     * Credential Request API: clients (potential Holders) POST a CredentialRequestMessage to the Issuer's /credentials
     * endpoint to request issuance. The Issuer must authenticate the client via a Self-Issued ID Token in the
     * Authorization header and return 201 Created with a Location header pointing to the request status resource.
     *
     * @param authorization HTTP Authorization header with a Bearer Self-Issued ID Token.
     * @param msg The CredentialRequestMessage containing holderPid and requested credential references.
     * @return HTTP 201 Created on success with Location header pointing to the request status resource.
     */
    @PostMapping(path = "/credentials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> requestCredentials(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                @RequestBody CredentialRequestMessage msg) {
        // Validate Authorization header
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }
        String token = authorization.substring("Bearer ".length());

        try {
            var claims = tokenService.validateToken(token);
            // we trust the validated token; the holderPid provided in the message should match sub claim
            String subject = claims.getSubject();
            if (subject == null || !subject.equals(msg.getHolderPid())) {
                LOG.warn("Holder PID in message does not match token subject: msg={}, sub={}", msg.getHolderPid(), subject);
                return ResponseEntity.status(401).body(GenericApiResponse.error("Token subject does not match holderPid"));
            }

            // persist request and return Location header
            CredentialRequest req = CredentialRequest.fromMessage(msg);
            requestRepository.save(req);
            String location = "/issuer/requests/" + req.getIssuerPid();
            return ResponseEntity.created(URI.create(location)).header(HttpHeaders.LOCATION, location).build();

        } catch (SecurityException se) {
            return ResponseEntity.status(401).body(GenericApiResponse.error("Invalid token"));
        } catch (Exception e) {
            LOG.error("Failed to create credential request: {}", e.getMessage());
            return ResponseEntity.status(500).body(GenericApiResponse.error("Internal error"));
        }
    }

    /**
     * Return the status of a previously created credential request.
     *
     * @param requestId The issuerPid identifier of the credential request.
     * @return 200 with request status JSON or 404 if not found.
     */
    @org.springframework.web.bind.annotation.GetMapping(path = "/requests/{requestId}")
    public ResponseEntity<?> getRequestStatus(@org.springframework.web.bind.annotation.PathVariable String requestId) {
        if (requestId == null || requestId.isBlank()) return ResponseEntity.badRequest().body("requestId required");
        return requestRepository.findByIssuerPid(requestId)
                .map(r -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("issuerPid", r.getIssuerPid());
                    body.put("holderPid", r.getHolderPid());
                    body.put("status", r.getStatus() != null ? r.getStatus().toString() : null);
                    body.put("rejectionReason", r.getRejectionReason());
                    body.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Approve and deliver credentials for a pending credential request.
     * This endpoint triggers Step 3 of the DCP credential issuance flow:
     * - Retrieves the credential request from the database
     * - Generates credentials based on the requested credential types (or uses provided credentials)
     * - Resolves the holder's DID to find their Credential Service endpoint
     * - Sends a CredentialMessage with the issued credentials to the holder
     * - Updates the request status to ISSUED
     *
     * @param requestId The issuerPid identifier of the credential request
     * @param request Optional request body containing custom credentials to deliver (if not provided, credentials are auto-generated)
     * @return 200 on success, 404 if request not found, 400 if already processed
     */
    @PostMapping(path = "/requests/{requestId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> approveAndDeliverCredentials(
            @org.springframework.web.bind.annotation.PathVariable String requestId,
            @RequestBody(required = false) Map<String, Object> request) {

        if (requestId == null || requestId.isBlank()) {
            return ResponseEntity.badRequest().body(GenericApiResponse.error("requestId required"));
        }

        try {
            // Retrieve the credential request from database
            CredentialRequest credentialRequest = requestRepository.findByIssuerPid(requestId)
                    .orElseThrow(() -> new IllegalArgumentException("Credential request not found: " + requestId));

            List<CredentialMessage.CredentialContainer> credentials;

            // Option 1: Use provided credentials from request body (for custom/manual issuance)
            if (request != null && request.containsKey("credentials")) {
                LOG.info("Using manually provided credentials for request {}", requestId);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> credentialsData = (List<Map<String, Object>>) request.get("credentials");

                if (credentialsData == null || credentialsData.isEmpty()) {
                    return ResponseEntity.badRequest().body(GenericApiResponse.error("credentials array must not be empty when provided"));
                }

                // Convert to CredentialContainer objects
                credentials = new java.util.ArrayList<>();
                for (Map<String, Object> credData : credentialsData) {
                    String credentialType = (String) credData.get("credentialType");
                    Object payload = credData.get("payload");
                    String format = (String) credData.get("format");

                    if (credentialType == null || payload == null || format == null) {
                        return ResponseEntity.badRequest().body(GenericApiResponse.error("Each credential must have credentialType, payload, and format"));
                    }

                    CredentialMessage.CredentialContainer container = CredentialMessage.CredentialContainer.Builder.newInstance()
                            .credentialType(credentialType)
                            .payload(payload)
                            .format(format)
                            .build();
                    credentials.add(container);
                }
            } else {
                // Option 2: Auto-generate credentials based on the credential request (recommended for UI)
                LOG.info("Auto-generating credentials for request {} based on requested credential IDs: {}",
                        requestId, credentialRequest.getCredentialIds());
                credentials = issuanceService.generateCredentials(credentialRequest);

                if (credentials.isEmpty()) {
                    return ResponseEntity.status(500).body(GenericApiResponse.error("Failed to generate credentials for the requested types"));
                }
            }

            // Deliver credentials to holder
            boolean success = deliveryService.deliverCredentials(requestId, credentials);

            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "delivered",
                    "message", "Credentials successfully delivered to holder",
                    "credentialsCount", credentials.size(),
                    "credentialTypes", credentials.stream()
                            .map(CredentialMessage.CredentialContainer::getCredentialType)
                            .toList()
                ));
            } else {
                return ResponseEntity.status(500).body(GenericApiResponse.error("Failed to deliver credentials to holder"));
            }

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            LOG.error("Error approving credential request {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.status(500).body(GenericApiResponse.error("Internal error: " + e.getMessage()));
        }
    }

    /**
     * Reject a credential request.
     * This endpoint allows rejecting a pending credential request and notifying the holder.
     *
     * @param requestId The issuerPid identifier of the credential request
     * @param request Request body containing the rejection reason
     * @return 200 on success, 404 if request not found, 400 if already processed
     */
    @PostMapping(path = "/requests/{requestId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rejectCredentialRequest(
            @org.springframework.web.bind.annotation.PathVariable String requestId,
            @RequestBody Map<String, String> request) {

        if (requestId == null || requestId.isBlank()) {
            return ResponseEntity.badRequest().body(GenericApiResponse.error("requestId required"));
        }

        String rejectionReason = request.get("rejectionReason");
        if (rejectionReason == null || rejectionReason.isBlank()) {
            return ResponseEntity.badRequest().body(GenericApiResponse.error("rejectionReason is required"));
        }

        try {
            boolean success = deliveryService.rejectCredentialRequest(requestId, rejectionReason);

            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "rejected",
                    "message", "Credential request rejected and holder notified"
                ));
            } else {
                return ResponseEntity.status(500).body(GenericApiResponse.error("Failed to process rejection"));
            }

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            LOG.error("Error rejecting credential request {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.status(500).body(GenericApiResponse.error("Internal error: " + e.getMessage()));
        }
    }
}
