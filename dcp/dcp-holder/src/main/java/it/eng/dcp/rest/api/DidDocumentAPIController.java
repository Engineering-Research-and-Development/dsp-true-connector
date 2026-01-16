package it.eng.dcp.rest.api;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.service.did.DidDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for holder DID document.
 */
@RestController
@RequestMapping("/api/did")
public class DidDocumentAPIController {
    private final DidDocumentService didDocumentService;
    private final DidDocumentConfig holderDidDocumentConfig;

    /**
     * Constructor.
     *
     * @param didDocumentService Service for DID document operations
     * @param holderDidDocumentConfig Configuration for holder DID document
     */
    @Autowired
    public DidDocumentAPIController(DidDocumentService didDocumentService,
                                    @Qualifier("holderDidDocumentConfig") DidDocumentConfig holderDidDocumentConfig) {
        this.didDocumentService = didDocumentService;
        this.holderDidDocumentConfig = holderDidDocumentConfig;
    }

    /**
     * Get the holder DID document.
     *
     * @return DID document for the holder
     */
    @GetMapping
    public ResponseEntity<DidDocument> getDidDocument() {
        DidDocument didDocument = didDocumentService.provideDidDocument((BaseDidDocumentConfiguration) holderDidDocumentConfig);
        return ResponseEntity.ok(didDocument);
    }
}
