package it.eng.dcp.issuer.rest;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.CredentialRequestMessage;
import it.eng.dcp.common.model.DCPConstants;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.RequestParticipantContext;
import it.eng.dcp.issuer.service.IssuerService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Optional;

/**
 * REST controller for issuer-side credential operations.
 */
@RestController
@RequestMapping(path = "/${dcp.base-path.issuer:issuer}", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class IssuerController {

    private final IssuerService issuerService;
    private final RequestParticipantContext requestParticipantContext;

    @Autowired
    public IssuerController(IssuerService issuerService, RequestParticipantContext requestParticipantContext) {
        this.issuerService = issuerService;
        this.requestParticipantContext = requestParticipantContext;
    }

    /**
     * Get Issuer Metadata endpoint.
     * This is a public discovery endpoint per DCP spec 6.7 - no authentication required.
     *
     * @param authorization HTTP Authorization header with Bearer token
     * @param request The inbound request, which addresses the participant
     * @return ResponseEntity with issuer metadata or error
     */
    @GetMapping(path = "/metadata")
    public ResponseEntity<?> getMetadata(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                         HttpServletRequest request) {
        // Unknown or disabled participant gets 404 before any token validation
        ParticipantContext participant = requestParticipantContext.require(request);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }
        String token = authorization.substring("Bearer ".length());

        try {
            issuerService.authorizeRequest(token, null, participant);
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
     * @param request The inbound request, which addresses the participant
     * @return ResponseEntity with created request or error
     */
    @PostMapping(path = "/credentials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCredentialRequest(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody CredentialRequestMessage requestMessage,
            HttpServletRequest request) {
        ParticipantContext participant = requestParticipantContext.require(request);

        log.info("Received credential request creation");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }
        String token = authorization.substring("Bearer ".length());

        try {
            JWTClaimsSet jwtClaimsSet = issuerService.authorizeRequest(token, requestMessage.getHolderPid(), participant);
            CredentialRequest credentialRequest = issuerService.createCredentialRequest(requestMessage, jwtClaimsSet.getSubject());

            log.info("Created credential request: issuerPid={}, holderPid={}, credentials={}",
                    credentialRequest.getIssuerPid(), credentialRequest.getHolderPid(), credentialRequest.getCredentialIds());

            // The request status lives next to the credentials endpoint, under the same base path and participant prefix
            String location = request.getRequestURI().replaceAll("/credentials/?$", "")
                    + "/requests/" + credentialRequest.getIssuerPid();
            return ResponseEntity.created(URI.create(location)).header(HttpHeaders.LOCATION, location).build();
        } catch (SecurityException e) {
            log.warn("Authorization failed: {}", e.getMessage());
            return ResponseEntity.status(401).body("Unauthorized: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid request: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error creating credential request: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal error: " + e.getMessage());
        }
    }

    /**
     * Get credential request by ID endpoint.
     * Requires authentication per DCP spec 6.8 - only the client that made the request may access.
     *
     * @param authorization HTTP Authorization header with Bearer token
     * @param requestId The credential request ID
     * @param request The inbound request, which addresses the participant
     * @return ResponseEntity with the request or error
     */
    @GetMapping(path = "/requests/{requestId}")
    public ResponseEntity<?> getRequest(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String requestId,
            HttpServletRequest request) {
        ParticipantContext participant = requestParticipantContext.require(request);

        // Validate authorization header
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }
        String token = authorization.substring("Bearer ".length());

        try {
            // Validate the token; its subject identifies the calling client
            JWTClaimsSet claims = issuerService.authorizeRequest(token, null, participant);

            // A request owned by another client is reported as not found so its existence is not revealed
            Optional<CredentialRequest> requestOpt = issuerService.getRequestByIssuerPid(requestId, claims.getSubject());
            if (requestOpt.isEmpty()) {
                return ResponseEntity.status(404).body("Request not found");
            }

            CredentialRequest r = requestOpt.get();
            Map<String, Object> body = new HashMap<>();
            body.put("type", "CredentialStatus");
            body.put("@context", List.of(DCPConstants.DCP_CONTEXT));
            body.put("issuerPid", r.getIssuerPid());
            body.put("holderPid", r.getHolderPid());
            body.put("status", r.getStatus() != null ? r.getStatus().toString() : null);
            body.put("rejectionReason", r.getRejectionReason());
//          body.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            return ResponseEntity.ok(body);
        } catch (SecurityException e) {
            log.warn("Authorization failed for status request: {}", e.getMessage());
            return ResponseEntity.status(401).body("Unauthorized: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error retrieving request: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal error: " + e.getMessage());
        }
    }
}
