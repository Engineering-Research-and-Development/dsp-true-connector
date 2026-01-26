package it.eng.dcp.rest.api;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Development/Testing utility controller for generating valid Self-Issued ID Tokens.
 * This endpoint should be disabled or secured in production environments.
 */
@RestController
@RequestMapping(path = "/api/dev/token", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class TokenGeneratorAPIController {

    private final SelfIssuedIdTokenService tokenService;
    private final BaseDidDocumentConfiguration config;

    @Autowired
    public TokenGeneratorAPIController(
            @Qualifier("selfIssuedIdTokenService") SelfIssuedIdTokenService tokenService,
            @Qualifier("holder") BaseDidDocumentConfiguration config) {
        this.tokenService = tokenService;
        this.config = config;
    }

    /**
     * Generate a valid Self-Issued ID Token for testing purposes.
     *
     * @param request Request body containing audienceDid and optional accessToken
     * @return JSON with the generated token
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateToken(@RequestBody Map<String, String> request) {
        log.info("Received request to generate Self-Issued ID Token");
        String audienceDid = request.get("audienceDid");
        String accessToken = request.get("accessToken");

        if (audienceDid == null || audienceDid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "audienceDid is required"));
        }

        try {
            String token = tokenService.createAndSignToken(audienceDid, accessToken, config.getDidDocumentConfig());
            return ResponseEntity.ok(Map.of(
                "token", token,
                "authorization", "Bearer " + token,
                "audienceDid", audienceDid
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Generate a valid Self-Issued ID Token using form-urlencoded parameters.
     * This endpoint mimics STS token endpoint for testing purposes.
     * The returned access_token is a JWT that contains a nested token claim.
     *
     * @param grantType OAuth2 grant type (e.g., "client_credentials")
     * @param clientId Client identifier
     * @param clientSecret Client secret
     * @param audience Target audience DID
     * @param bearerAccessScope Requested scopes
     * @return JSON with the generated token
     */
    @PostMapping(value = "/generate/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> generateTokenFromForm(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "audience", required = false) String audience,
            @RequestParam(value = "bearer_access_scope", required = false) String bearerAccessScope) {

        log.info("Received form-urlencoded request to generate Self-Issued ID Token");
        log.info("grant_type: {}, client_id: {}, audience: {}, bearer_access_scope: {}",
                grantType, clientId, audience, bearerAccessScope);

        try {
//            audience = "did:web:localhost:8080:holder";  // Hardcoded for dummy testing
            // Generate STS-compatible token with nested token claim for dummy testing
            String token = tokenService.createStsCompatibleToken(audience, config.getDidDocumentConfig(), bearerAccessScope);
            return ResponseEntity.ok(Map.of(
                "access_token", token,
                "token_type", "Bearer",
                "expires_in", 3600
            ));
        } catch (Exception e) {
            log.error("Failed to generate token", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}

