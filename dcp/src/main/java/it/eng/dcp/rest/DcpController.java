package it.eng.dcp.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.model.CredentialMessage;
import it.eng.dcp.model.CredentialOfferMessage;
import it.eng.dcp.model.VerifiableCredential;
import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.service.ConsentService;
import it.eng.dcp.service.PresentationService;
import it.eng.dcp.service.SelfIssuedIdTokenService;
import it.eng.dcp.service.PresentationRateLimiter;
import it.eng.dcp.repository.VerifiableCredentialRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal DCP REST controller implementing presentation query endpoint and reception endpoints for credentials/offers.
 */
@RestController
@RequestMapping(path = "/dcp")
public class DcpController {

    private static final Logger LOG = LoggerFactory.getLogger(DcpController.class);

    private final SelfIssuedIdTokenService tokenService;
    private final PresentationService presentationService;
    private final ConsentService consentService;
    private final PresentationRateLimiter rateLimiter;
    private final VerifiableCredentialRepository credentialRepository;
    private final ObjectMapper mapper;

    @Autowired
    public DcpController(SelfIssuedIdTokenService tokenService, PresentationService presentationService, ConsentService consentService, PresentationRateLimiter rateLimiter, VerifiableCredentialRepository credentialRepository, ObjectMapper mapper) {
        this.tokenService = tokenService;
        this.presentationService = presentationService;
        this.consentService = consentService;
        this.rateLimiter = rateLimiter;
        this.credentialRepository = credentialRepository;
        this.mapper = mapper;
    }

    @PostMapping(path = "/presentations/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> queryPresentations(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                @RequestBody PresentationQueryMessage query) {
        // Validate Bearer token: expected format 'Bearer <jwt>'
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authorization.substring("Bearer ".length());

        try {
            var claims = tokenService.validateToken(token);
            // Extract subject (holder DID) from claims - here we assume 'sub' is holder DID
            String holderDid = claims.getSubject();

            // Rate limiting per holder DID
            if (!rateLimiter.tryConsume(holderDid)) {
                return ResponseEntity.status(429).build();
            }

            // Enforce consent
            if (!consentService.isConsentValidFor(holderDid)) {
                return ResponseEntity.status(403).build();
            }

            PresentationResponseMessage resp = presentationService.createPresentation(query);
            return ResponseEntity.ok(resp);
        } catch (SecurityException se) {
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping(path = "/credentials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receiveCredentials(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                @RequestBody CredentialMessage msg) {
        // Authenticate issuer: require Bearer token
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }
        String token = authorization.substring("Bearer ".length());

        String issuerDid;
        try {
            var claims = tokenService.validateToken(token);
            // Token is valid; capture issuer DID from token. Per the DCP spec the token issuer (a DID) identifies the
            // Issuer Service. The credential message's `issuerPid` is an issuance identifier on the Issuer side and is
            // not the same as the issuer DID. Therefore we don't compare them here. If necessary, implementers SHOULD
            // include an explicit issuer DID field in the message to allow equality checks; for now, rely on the token
            // validation to authenticate the caller.
            issuerDid = claims.getIssuer();
            if (issuerDid == null || issuerDid.isBlank()) {
                return ResponseEntity.status(401).body("Invalid token: missing issuer claim");
            }
        } catch (SecurityException se) {
            return ResponseEntity.status(401).body("Invalid token");
        }

        if (msg == null || msg.getStatus() == null) {
            return ResponseEntity.badRequest().body("Missing credential message or status");
        }

        String status = msg.getStatus();
        if ("ISSUED".equalsIgnoreCase(status)) {
            if (msg.getCredentials() == null || msg.getCredentials().isEmpty()) {
                return ResponseEntity.badRequest().body("ISSUED status requires non-empty credentials array");
            }
            List<VerifiableCredential> saved = new ArrayList<>();
            for (CredentialMessage.CredentialContainer c : msg.getCredentials()) {
                try {
                    JsonNode payloadNode = null;
                    Object payload = c.getPayload();
                    if (payload instanceof JsonNode) {
                        payloadNode = (JsonNode) payload;
                    } else if (payload instanceof Map) {
                        payloadNode = mapper.convertValue(payload, JsonNode.class);
                    } else if (payload instanceof String) {
                        payloadNode = mapper.readTree((String) payload);
                    }

                    // payload must be a JSON object containing the VC
                    if (payloadNode == null || !payloadNode.isObject()) {
                        return ResponseEntity.badRequest().body("Each credential payload must be a JSON object (Verifiable Credential)");
                    }

                    VerifiableCredential.Builder cb = VerifiableCredential.Builder.newInstance();
                    if (payloadNode.has("id")) cb.id(payloadNode.get("id").asText());
                    // set holderDid from message holderPid only if looks like a DID
                    if (msg.getHolderPid() != null && msg.getHolderPid().startsWith("did:")) cb.holderDid(msg.getHolderPid());
                    // credentialType from container
                    if (c.getCredentialType() != null) cb.credentialType(c.getCredentialType());
                    // set payload as credential JSON
                    cb.credential(payloadNode);
                    // record the asserting issuer DID (from validated token) on the stored VC for audit/trust checks
                    cb.issuerDid(issuerDid);
                    // attempt to set issuance/expiration if present
                    try {
                        if (payloadNode.has("issuanceDate")) cb.issuanceDate(Instant.parse(payloadNode.get("issuanceDate").asText()));
                        if (payloadNode.has("expirationDate")) cb.expirationDate(Instant.parse(payloadNode.get("expirationDate").asText()));
                    } catch (Exception ignored) {}

                    VerifiableCredential vc = cb.build();
                    saved.add(credentialRepository.save(vc));
                } catch (Exception e) {
                    LOG.warn("Failed to persist credential container: {}", e.getMessage());
                }
            }
            return ResponseEntity.ok(Map.of("saved", saved.size()));
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            LOG.info("Received rejected credentials message from issuer {}: reason={}", msg.getIssuerPid(), msg.getRejectionReason());
            return ResponseEntity.ok(Map.of("status", "rejected"));
        } else {
            return ResponseEntity.badRequest().body("Unknown status: " + status);
        }
    }

    @PostMapping(path = "/offers", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receiveOffer(@RequestBody CredentialOfferMessage offer) {
        if (offer == null || offer.getOfferedCredentials() == null || offer.getOfferedCredentials().isEmpty()) {
            return ResponseEntity.badRequest().body("offeredCredentials must be provided and non-empty");
        }
        LOG.info("Received credential offer with {} offered credentials", offer.getOfferedCredentials().size());
        // for now simply log and accept
        return ResponseEntity.ok(Map.of("accepted", true));
    }
}
