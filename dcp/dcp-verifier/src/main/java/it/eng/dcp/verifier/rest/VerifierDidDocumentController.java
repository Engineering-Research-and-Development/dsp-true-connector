package it.eng.dcp.verifier.rest;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.service.did.DidDocumentService;
import it.eng.dcp.verifier.config.VerifierDidDocumentConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class VerifierDidDocumentController {

    private final DidDocumentService didDocumentService;
    private final BaseDidDocumentConfiguration verifierDidDocumentConfig;

    public VerifierDidDocumentController(DidDocumentService didDocumentService,
                                         VerifierDidDocumentConfiguration verifierDidDocumentConfig) {
        this.didDocumentService = didDocumentService;
        this.verifierDidDocumentConfig = verifierDidDocumentConfig;
    }

    @GetMapping(value = "/verifier/did.json")
    public ResponseEntity<DidDocument> getHolderDidDocument() {
        log.info("Received request for holder DID document");
        DidDocument didDocument = didDocumentService.provideDidDocument(verifierDidDocumentConfig);
        return ResponseEntity.ok(didDocument);
    }
}
