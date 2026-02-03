package it.eng.dcp.holder.rest;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.service.did.DidDocumentService;
import it.eng.dcp.holder.config.HolderDidDocumentConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for holder DID document at role-specific location.
 *
 * <p>The well-known endpoint (/.well-known/did.json) is handled by
 * GenericDidDocumentController in dcp-common to avoid conflicts in multi-module scenarios.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class DidDocumentController {

    private final DidDocumentService didDocumentService;
    private final BaseDidDocumentConfiguration holderDidDocumentConfig;

    /**
     * Constructor.
     *
     * @param didDocumentService Service for DID document operations
     * @param holderDidDocumentConfig Configuration for holder DID document
     */
    @Autowired
    public DidDocumentController(DidDocumentService didDocumentService,
                                HolderDidDocumentConfiguration holderDidDocumentConfig) {
        this.didDocumentService = didDocumentService;
        this.holderDidDocumentConfig = holderDidDocumentConfig;
    }

    /**
     * Get the holder DID document at role-specific endpoint.
     *
     * @return DID document for the holder
     */

    @GetMapping(value = "/holder/did.json")
    public ResponseEntity<DidDocument> getHolderDidDocument() {
        log.info("Received request for holder DID document");
        DidDocument didDocument = didDocumentService.provideDidDocument(holderDidDocumentConfig);
        return ResponseEntity.ok(didDocument);
    }
}
