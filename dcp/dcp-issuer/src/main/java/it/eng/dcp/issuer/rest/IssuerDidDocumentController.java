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
 * REST controller for serving the issuer DID document.
 * This exposes the issuer's DID document at /.well-known/did.json
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
     * Expose the issuer DID document at /.well-known/did.json.
     *
     * @return The DID document as JSON
     */
    @GetMapping(path = "/.well-known/did.json")
    public ResponseEntity<String> getDidDocument() {
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

    /**
     * Alternative endpoint for issuer DID document.
     *
     * @return The DID document as JSON
     */
    @GetMapping(path = "/issuer/did.json")
    public ResponseEntity<String> getIssuerDidDocument() {
        return getDidDocument();
    }
}
