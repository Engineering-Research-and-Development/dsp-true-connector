package it.eng.dcp.rest;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.service.did.DidDocumentService;
import it.eng.dcp.config.HolderDidDocumentConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for holder DID document at well-known location.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
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
     * Get the holder DID document at well-known location.
     *
     * @return DID document for the holder
     */
    @GetMapping("/.well-known/did.json")
    public ResponseEntity<DidDocument> getDidDocument() {
        DidDocument didDocument = didDocumentService.provideDidDocument(holderDidDocumentConfig);
        return ResponseEntity.ok(didDocument);
    }
}
