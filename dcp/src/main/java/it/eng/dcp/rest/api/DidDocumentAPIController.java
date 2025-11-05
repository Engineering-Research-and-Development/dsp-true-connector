package it.eng.dcp.rest.api;

import it.eng.dcp.model.DidDocument;
import it.eng.dcp.service.DidDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/did")
public class DidDocumentAPIController {
    private final DidDocumentService didDocumentService;

    public DidDocumentAPIController(DidDocumentService didDocumentService) {
        this.didDocumentService = didDocumentService;
    }

    @GetMapping
    public ResponseEntity<DidDocument> getDidDocument() {
        DidDocument didDocument = didDocumentService.provideDidDocument();
        return ResponseEntity.ok(didDocument);
    }
}
