package it.eng.dcp.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialOfferMessage;
import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.service.HolderService;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * DCP REST controller implementing presentation query endpoint and reception endpoints for credentials/offers.
 * Delegates business logic to HolderService.
 */
@RestController
@RequestMapping(path = "/dcp", produces =  MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class DcpController {

    private final HolderService holderService;

    @Autowired
    public DcpController(HolderService holderService, ObjectMapper objectMapper) {
        this.holderService = holderService;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new SecurityException("Missing or invalid Authorization header");
        }
        return authorizationHeader.substring("Bearer ".length());
    }

    /**
     * Presentation query endpoint.
     * Accepts presentation queries from verifiers and returns verifiable presentations.
     *
     * @param authorization HTTP Authorization header with Bearer token
     * @param query The presentation query message
     * @return PresentationResponseMessage or error response
     */
    @PostMapping(path = "/presentations/query")
    public ResponseEntity<?> queryPresentations(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                @RequestBody PresentationQueryMessage query) {
        log.info("Received presentation query request");
        log.debug("Query details: scope={}, presentationDefinition present={}",
                query != null ? query.getScope() : null,
                query != null && query.getPresentationDefinition() != null);

        String token = extractBearerToken(authorization);

        try {
            // Authorize request
            var claims = holderService.authorizePresentationQuery(token);
            String holderDid = claims.getSubject();
            log.info("Presentation query from holder: {}", holderDid);

            // Rate limiting per holder DID
            if (!holderService.checkRateLimit(holderDid)) {
                return ResponseEntity.status(429).build();
            }

            // Create presentation
            PresentationResponseMessage resp = holderService.createPresentation(query, holderDid);
            return ResponseEntity.ok(resp);
        } catch (SecurityException se) {
            log.error("Security exception during presentation query - invalid token: {}", se.getMessage());
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Error processing presentation query: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * Credential reception endpoint.
     * Receives credentials or rejection messages from issuers.
     *
     * @param authorization HTTP Authorization header with Bearer token from issuer
     * @param msg The credential message
     * @return Success response with saved/skipped counts or error response
     */
    @PostMapping(path = "/credentials")
    public ResponseEntity<?> receiveCredentials(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                @RequestBody CredentialMessage msg) {
        log.info("Received credential message - status: {}, issuerPid: {}, holderPid: {}",
                msg != null ? msg.getStatus() : "null",
                msg != null ? msg.getIssuerPid() : "null",
                msg != null ? msg.getHolderPid() : "null");

         String token = extractBearerToken(authorization);

        try {
            // Authorize issuer
            String issuerDid = holderService.authorizeIssuer(token);

            // Validate message
            if (msg == null || msg.getStatus() == null) {
                log.error("Invalid credential message: message or status is null");
                return ResponseEntity.badRequest().body("Missing credential message or status");
            }

            String status = msg.getStatus();

            if ("ISSUED".equalsIgnoreCase(status)) {
                // Process issued credentials
                HolderService.CredentialReceptionResult result = holderService.processIssuedCredentials(msg, issuerDid);

                if (result.isEmpty()) {
                    log.error("No credentials were saved. All {} credentials were skipped due to validation errors. Check logs for details.",
                            result.getSkippedCount());
                    return ResponseEntity.status(400).body(Map.of(
                            "saved", 0,
                            "skipped", result.getSkippedCount(),
                            "error", "All credentials had empty or invalid payloads. Per DCP spec 6.5.2, payload must contain a Verifiable Credential."
                    ));
                }

                if (result.hasSkipped()) {
                    log.warn("Partial success: saved {} credentials, skipped {} credentials",
                            result.getSavedCount(), result.getSkippedCount());
                    return ResponseEntity.ok(Map.of(
                            "saved", result.getSavedCount(),
                            "skipped", result.getSkippedCount()
                    ));
                }

                log.info("Successfully saved all {} credentials", result.getSavedCount());
                return ResponseEntity.ok(Map.of("saved", result.getSavedCount()));

            } else if ("REJECTED".equalsIgnoreCase(status)) {
                // Process rejection
                holderService.processRejectedCredentials(msg);
                log.info("Returning rejected status response");
                return ResponseEntity.ok(Map.of("status", "rejected"));

            } else {
                log.error("Unknown credential message status received: {}", status);
                return ResponseEntity.badRequest().body("Unknown status: " + status);
            }

        } catch (SecurityException se) {
            log.error("Security exception during credential reception - invalid token: {}", se.getMessage());
            return ResponseEntity.status(401).body("Invalid token");
        } catch (IllegalArgumentException e) {
            log.error("Invalid credential message: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error processing credential message: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(GenericApiResponse.error("Internal error: " + e.getMessage()));
        }
    }

    /**
     * Credential offer reception endpoint.
     * Receives credential offers from issuers.
     *
     * @param offer The credential offer message
//     * @param authorization HTTP Authorization header with Bearer token from issuer
     * @return Acceptance response
     */
    @PostMapping(path = "/offers")
    public ResponseEntity<?> receiveOffer(@RequestBody Map<String, Object> offer
//                                          @RequestHeader(value = HttpHeaders.AUTHORIZATION) String authorization
    )
            throws JsonProcessingException {
        // Authorization is optional for offers, but if present, validate it
//       String token =  extractBearerToken(authorization);
       ObjectMapper objectMapper = new ObjectMapper();
        log.info("Received credential offer {}", objectMapper.writeValueAsString(offer));
        try {
//            holderService.authorizeIssuer(token);
            CredentialOfferMessage com = objectMapper.convertValue(offer, CredentialOfferMessage.class);
            boolean accepted = holderService.processCredentialOffer(com);
            return ResponseEntity.ok(Map.of("accepted", accepted));
        } catch (IllegalArgumentException e) {
            log.error("Invalid credential offer: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error processing credential offer: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(GenericApiResponse.error("Internal error: " + e.getMessage()));
        }
    }
}
