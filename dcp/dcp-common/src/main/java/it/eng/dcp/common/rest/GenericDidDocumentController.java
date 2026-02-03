package it.eng.dcp.common.rest;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.service.did.DidDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic DID Document controller for the well-known endpoint.
 *
 * <p>This controller serves the DID document at /.well-known/did.json using the
 * primary BaseDidDocumentConfiguration bean available in the application context.
 *
 * <p>In single-module deployments (holder-only, issuer-only, verifier-only), the
 * module's configuration will be used. In multi-module test scenarios, the @Primary
 * configuration (typically holder) will be used.
 *
 * <p>Each module can still expose role-specific endpoints (e.g., /holder/did.json,
 * /issuer/did.json, /verifier/did.json) in their own controllers.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class GenericDidDocumentController {

    private final DidDocumentService didDocumentService;
    private final BaseDidDocumentConfiguration didDocumentConfig;

    /**
     * Constructor.
     *
     * @param didDocumentService Service for DID document operations
     * @param didDocumentConfig Configuration for DID document (uses @Primary if multiple exist)
     */
    @Autowired
    public GenericDidDocumentController(DidDocumentService didDocumentService,
                                       BaseDidDocumentConfiguration didDocumentConfig) {
        this.didDocumentService = didDocumentService;
        this.didDocumentConfig = didDocumentConfig;
        log.info("GenericDidDocumentController initialized with configuration: {}",
                didDocumentConfig.getClass().getSimpleName());
    }

    /**
     * Serve the DID document at the well-known location.
     *
     * <p>This endpoint is the standard location for DID documents according to the
     * did:web method specification.
     *
     * @return DID document in JSON format
     */
    @GetMapping(value = "/.well-known/did.json")
    public ResponseEntity<DidDocument> getDidDocument() {
        log.debug("Serving DID document from well-known endpoint");
        DidDocument didDocument = didDocumentService.provideDidDocument(didDocumentConfig);
        return ResponseEntity.ok(didDocument);
    }
}
