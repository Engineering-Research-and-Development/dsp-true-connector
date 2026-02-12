package it.eng.dcp.holder.rest.api;

import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.service.did.DidDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final DidDocumentConfig didDocumentConfig;

    /**
     * Constructor.
     *
     * @param didDocumentService Service for DID document operations
     * @param didDocumentConfig Configuration for DID document
     */
    @Autowired
    public DidDocumentAPIController(DidDocumentService didDocumentService,
                                    DidDocumentConfig didDocumentConfig) {
        this.didDocumentService = didDocumentService;
        this.didDocumentConfig = didDocumentConfig;
    }

    /**
     * Get the holder DID document.
     *
     * @return DID document for the holder
     */
    @GetMapping
    public ResponseEntity<DidDocument> getDidDocument() {
        DidDocument didDocument = didDocumentService.provideDidDocument(didDocumentConfig);
        return ResponseEntity.ok(didDocument);
    }
}
