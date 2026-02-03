package it.eng.dcp.issuer.rest;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.service.did.DidDocumentService;
import it.eng.dcp.issuer.config.IssuerDidDocumentConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for serving the issuer DID document at role-specific location.
 *
 * <p>The well-known endpoint (/.well-known/did.json) is handled by
 * GenericDidDocumentController in dcp-common to avoid conflicts in multi-module scenarios.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class IssuerDidDocumentController {

    private final DidDocumentService didDocumentService;
    private final BaseDidDocumentConfiguration issuerDidDocumentConfig;

    /**
     * Constructor.
     *
     * @param didDocumentService Service for DID document operations
     * @param issuerDidDocumentConfig Configuration for issuer DID document
     */
    @Autowired
    public IssuerDidDocumentController(DidDocumentService didDocumentService,
                                      IssuerDidDocumentConfiguration issuerDidDocumentConfig) {
        this.didDocumentService = didDocumentService;
        this.issuerDidDocumentConfig = issuerDidDocumentConfig;
    }

    /**
     * Get the issuer DID document at role-specific endpoint.
     *
     * @return The DID document as JSON
     */
    @GetMapping(path = "/issuer/did.json")
    public ResponseEntity<String> getIssuerDidDocument() {
        try {
            String didDocument = didDocumentService.getDidDocument(issuerDidDocumentConfig);
            log.debug("Serving issuer DID document");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(didDocument);
        } catch (Exception e) {
            log.error("Failed to retrieve issuer DID document: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("{\"error\": \"Failed to retrieve DID document\"}");
        }
    }
}
