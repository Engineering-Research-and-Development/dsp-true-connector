package it.eng.dcp.rest;

import it.eng.dcp.model.DidDocument;
import it.eng.dcp.service.DidDocumentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class DidDocumentController {

    private final DidDocumentService didDocumentService;

    public DidDocumentController(DidDocumentService didDocumentService) {
        this.didDocumentService = didDocumentService;
    }

    @GetMapping("/.well-known/did.json")
    public ResponseEntity<DidDocument> getDidDocument() {
        DidDocument didDocument = didDocumentService.provideDidDocument();
        return ResponseEntity.ok(didDocument);
    }
}
