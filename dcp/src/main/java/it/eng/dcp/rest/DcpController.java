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
import it.eng.dcp.repository.CredentialStatusRepository;
import it.eng.dcp.model.CredentialStatusRecord;
import it.eng.dcp.model.CredentialStatus;
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
import java.util.UUID;

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
    private final CredentialStatusRepository credentialStatusRepository;
    private final ObjectMapper mapper;

    @Autowired
    public DcpController(SelfIssuedIdTokenService tokenService, PresentationService presentationService, ConsentService consentService, PresentationRateLimiter rateLimiter, VerifiableCredentialRepository credentialRepository, CredentialStatusRepository credentialStatusRepository, ObjectMapper mapper) {
        this.tokenService = tokenService;
        this.presentationService = presentationService;
        this.consentService = consentService;
        this.rateLimiter = rateLimiter;
        this.credentialRepository = credentialRepository;
        this.credentialStatusRepository = credentialStatusRepository;
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
            int skipped = 0;
            for (CredentialMessage.CredentialContainer c : msg.getCredentials()) {
                try {
                    Object payload = c.getPayload();

                    // Validate that payload is not empty/null
                    if (payload == null || (payload instanceof String && ((String) payload).trim().isEmpty())) {
                        LOG.warn("Skipping credential with type '{}' - payload is empty or null. Per DCP spec 6.5.2, payload must contain a Verifiable Credential.",
                                 c.getCredentialType());
                        skipped++;
                        continue;
                    }

                    String format = c.getFormat();
                    if (format == null) {
                        LOG.warn("Skipping credential with type '{}' - format is missing", c.getCredentialType());
                        skipped++;
                        continue;
                    }

                    VerifiableCredential.Builder cb = VerifiableCredential.Builder.newInstance();

                    // Handle JWT format credentials
                    if ("jwt".equalsIgnoreCase(format)) {
                        if (!(payload instanceof String jwtString)) {
                            LOG.warn("Skipping JWT credential - payload must be a JWT string, got: {}", payload.getClass().getSimpleName());
                            skipped++;
                            continue;
                        }
                        // For JWT format, store the JWT string directly and decode header/payload for metadata
                        // Create a minimal JSON representation for storage
                        JsonNode jwtNode = mapper.createObjectNode()
                                .put("type", c.getCredentialType())
                                .put("format", "jwt")
                                .put("jwt", jwtString);
                        cb.credential(jwtNode);
                        // Extract metadata from JWT if needed (decode without verification for metadata extraction)
                        // For now, use credentialType from container
                    } else if ("json-ld".equalsIgnoreCase(format) || "ldp_vc".equalsIgnoreCase(format)) {
                        // Handle JSON-LD format credentials
                        JsonNode payloadNode = null;
                        if (payload instanceof JsonNode) {
                            payloadNode = (JsonNode) payload;
                        } else if (payload instanceof Map) {
                            payloadNode = mapper.convertValue(payload, JsonNode.class);
                        } else if (payload instanceof String) {
                            try {
                                payloadNode = mapper.readTree((String) payload);
                            } catch (Exception e) {
                                LOG.warn("Skipping credential - failed to parse JSON-LD payload: {}", e.getMessage());
                                skipped++;
                                continue;
                            }
                        }

                        // payload must be a JSON object containing the VC
                        if (payloadNode == null || !payloadNode.isObject()) {
                            LOG.warn("Skipping credential - JSON-LD payload must be a JSON object (Verifiable Credential)");
                            skipped++;
                            continue;
                        }

                        if (payloadNode.has("id")) cb.id(payloadNode.get("id").asText());
                        // set payload as credential JSON
                        cb.credential(payloadNode);
                        // attempt to set issuance/expiration if present
                        try {
                            if (payloadNode.has("issuanceDate")) cb.issuanceDate(Instant.parse(payloadNode.get("issuanceDate").asText()));
                            if (payloadNode.has("expirationDate")) cb.expirationDate(Instant.parse(payloadNode.get("expirationDate").asText()));
                        } catch (Exception ignored) {}
                    } else {
                        LOG.warn("Skipping credential with type '{}' - unsupported format: {}", c.getCredentialType(), format);
                        skipped++;
                        continue;
                    }

                    // Common properties for both formats
                    // set holderDid from message holderPid only if looks like a DID
                    if (msg.getHolderPid() != null && msg.getHolderPid().startsWith("did:")) cb.holderDid(msg.getHolderPid());
                    // credentialType from container
                    if (c.getCredentialType() != null) cb.credentialType(c.getCredentialType());
                    // record the asserting issuer DID (from validated token) on the stored VC for audit/trust checks
                    cb.issuerDid(issuerDid);

                    VerifiableCredential vc = cb.build();
                    saved.add(credentialRepository.save(vc));
                } catch (Exception e) {
                    LOG.warn("Failed to persist credential container with type '{}': {}", c.getCredentialType(), e.getMessage());
                    skipped++;
                }
            }
            // Persist issuance status record keyed by issuer's request id (issuerPid). If not provided, generate one.
            try {
                // Prefer explicit requestId provided by issuer; fall back to issuerPid then generate one
                String requestId = msg.getRequestId();
                if (requestId == null || requestId.isBlank()) requestId = msg.getIssuerPid();
                if (requestId == null || requestId.isBlank()) requestId = "req-" + UUID.randomUUID();
                CredentialStatusRecord rec = CredentialStatusRecord.Builder.newInstance()
                        .requestId(requestId)
                        .issuerPid(msg.getIssuerPid())
                        .holderPid(msg.getHolderPid())
                        .status(CredentialStatus.ISSUED)
                        .savedCount(saved.size())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                credentialStatusRepository.save(rec);
            } catch (Exception e) {
                LOG.warn("Failed to persist credential status record: {}", e.getMessage());
            }

            if (saved.isEmpty() && skipped > 0) {
                LOG.warn("No credentials were saved. All {} credentials were skipped due to validation errors. Check logs for details.", skipped);
                return ResponseEntity.status(400).body(Map.of(
                    "saved", 0,
                    "skipped", skipped,
                    "error", "All credentials had empty or invalid payloads. Per DCP spec 6.5.2, payload must contain a Verifiable Credential."
                ));
            }

            if (skipped > 0) {
                LOG.info("Saved {} credentials, skipped {} credentials", saved.size(), skipped);
                return ResponseEntity.ok(Map.of("saved", saved.size(), "skipped", skipped));
            }
            return ResponseEntity.ok(Map.of("saved", saved.size()));
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            LOG.info("Received rejected credentials message from issuer {}: reason={}", msg.getIssuerPid(), msg.getRejectionReason());
            // Persist rejected status
            try {
                // Prefer explicit requestId provided by issuer; fall back to issuerPid then generate one
                String requestId = msg.getRequestId();
                if (requestId == null || requestId.isBlank()) requestId = msg.getIssuerPid();
                if (requestId == null || requestId.isBlank()) requestId = "req-" + UUID.randomUUID();
                CredentialStatusRecord rec = CredentialStatusRecord.Builder.newInstance()
                        .requestId(requestId)
                        .issuerPid(msg.getIssuerPid())
                        .holderPid(msg.getHolderPid())
                        .status(CredentialStatus.REJECTED)
                        .rejectionReason(msg.getRejectionReason())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                credentialStatusRepository.save(rec);
            } catch (Exception e) {
                LOG.warn("Failed to persist rejected credential status record: {}", e.getMessage());
            }
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
