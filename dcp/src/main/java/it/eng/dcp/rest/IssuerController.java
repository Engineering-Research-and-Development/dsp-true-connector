package it.eng.dcp.rest;

import it.eng.dcp.model.CredentialRequest;
import it.eng.dcp.model.CredentialRequestMessage;
import it.eng.dcp.repository.CredentialRequestRepository;
import it.eng.dcp.service.SelfIssuedIdTokenService;
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
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping(path = "/issuer", produces = MediaType.APPLICATION_JSON_VALUE)
public class IssuerController {

    private static final Logger LOG = LoggerFactory.getLogger(IssuerController.class);

    private final SelfIssuedIdTokenService tokenService;
    private final CredentialRequestRepository requestRepository;

    @Autowired
    public IssuerController(SelfIssuedIdTokenService tokenService, CredentialRequestRepository requestRepository) {
        this.tokenService = tokenService;
        this.requestRepository = requestRepository;
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
                return ResponseEntity.status(401).body("Token subject does not match holderPid");
            }

            // persist request and return Location header
            CredentialRequest req = CredentialRequest.fromMessage(msg);
            requestRepository.save(req);
            String location = "/issuer/requests/" + req.getIssuerPid();
            return ResponseEntity.created(URI.create(location)).header(HttpHeaders.LOCATION, location).build();

        } catch (SecurityException se) {
            return ResponseEntity.status(401).body("Invalid token");
        } catch (Exception e) {
            LOG.error("Failed to create credential request: {}", e.getMessage());
            return ResponseEntity.status(500).body("Internal error");
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
}
