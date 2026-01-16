package it.eng.dcp.rest.api;

import it.eng.dcp.service.HolderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE,
        path = "/tck/protocol/2025/1/catalog/request")
@Slf4j
public class TCKCatalogController {

    private final HolderService holderService;

    public TCKCatalogController(HolderService holderService) {
        this.holderService = holderService;
    }

    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, "application/json;charset=utf-8"})
    public ResponseEntity<String> initiateTCKCatalogRequest(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        log.info("Authorization header: {}", authorization);

        // Authenticate issuer: require Bearer token
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.warn("Credential reception rejected: Missing or invalid Authorization header");
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }
        String token = authorization.substring("Bearer ".length());

        try {
            // Authorize issuer
            String issuerDid = holderService.authorizeIssuer(token);

        } catch (SecurityException se) {
            log.error("Security exception during presentation query - invalid token: {}", se.getMessage());
            return ResponseEntity.status(401).build();
        }

        log.info("Received TCK Catalog Request");
        String response = """
                {
                    "@type":  "https://w3id.org/dspace/2024/1/CatalogRequestMessage",
                    "https://w3id.org/dspace/v0.8/filter": {}
                }
                """;
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }
}
