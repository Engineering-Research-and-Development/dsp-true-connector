package it.eng.dcp.rest.api;

import it.eng.dcp.service.SelfIssuedIdTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Development/Testing utility controller for generating valid Self-Issued ID Tokens.
 * This endpoint should be disabled or secured in production environments.
 */
@RestController
@RequestMapping(path = "/api/dev/token", produces = MediaType.APPLICATION_JSON_VALUE)
public class TokenGeneratorAPIController {

    private final SelfIssuedIdTokenService tokenService;

    @Autowired
    public TokenGeneratorAPIController(SelfIssuedIdTokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Generate a valid Self-Issued ID Token for testing purposes.
     *
     * @param request Request body containing audienceDid and optional accessToken
     * @return JSON with the generated token
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateToken(@RequestBody Map<String, String> request) {
        String audienceDid = request.get("audienceDid");
        String accessToken = request.get("accessToken");

        if (audienceDid == null || audienceDid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "audienceDid is required"));
        }

        try {
            String token = tokenService.createAndSignToken(audienceDid, accessToken);
            return ResponseEntity.ok(Map.of(
                "token", token,
                "authorization", "Bearer " + token,
                "audienceDid", audienceDid
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}

