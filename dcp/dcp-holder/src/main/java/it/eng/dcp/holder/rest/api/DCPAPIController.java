package it.eng.dcp.holder.rest.api;

import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.holder.service.CredentialIssuanceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for fetching and processing issuer metadata.
 *
 * <p>This controller provides endpoints to retrieve credential information
 * from external issuer services, including supported credential IDs.
 */
@RestController
@RequestMapping(path = "/api/v1/dcp", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class DCPAPIController {

    private final CredentialIssuanceClient credentialIssuanceClient;

    @Autowired
    public DCPAPIController(CredentialIssuanceClient credentialIssuanceClient) {
        this.credentialIssuanceClient = credentialIssuanceClient;
    }

    /**
     * Fetches issuer metadata.
     *
     * @return ResponseEntity containing the issuer metadata or an error message
     */
    @GetMapping("/issuer-metadata")
    public ResponseEntity<?> getIssuerMetadata() {
        try {
            IssuerMetadata issuerMetadata = credentialIssuanceClient.getPersonalIssuerMetadata();

            log.info("Successfully retrieved issuer metadata");

            return ResponseEntity.ok(issuerMetadata);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch issuer metadata: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "Failed to fetch issuer metadata: " + e.getMessage()));
        }
    }

    /**
     * Requests credentials from the issuer using the provided credential IDs.
     *
     * @param credentialIds List of credential IDs to request
     * @return ResponseEntity with the status URL location or error message
     */
    @PostMapping(value = "/credentials/request")
    public ResponseEntity<?> requestCredentials(@RequestBody List<String> credentialIds) {
        log.info("Received request to request credentials from issuer");

        if (credentialIds == null || credentialIds.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "credentialIds list is required and must not be empty"));
        }

        try {
            // Request all credentials in a single request
            log.debug("Requesting {} credentials", credentialIds.size());
            String statusUrl = credentialIssuanceClient.requestCredential(credentialIds);
            log.info("Credential request successful for {} credentials, status URL: {}", credentialIds.size(), statusUrl);

            return ResponseEntity.status(201)
                .header(HttpHeaders.LOCATION, statusUrl)
                .body(Map.of(
                    "message", "Credential request created successfully",
                    "statusUrl", statusUrl,
                    "credentialCount", credentialIds.size()
                ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to request credentials: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "Failed to request credentials: " + e.getMessage()));
        }
    }


}

