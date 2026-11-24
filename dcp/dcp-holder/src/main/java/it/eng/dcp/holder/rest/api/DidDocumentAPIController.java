package it.eng.dcp.holder.rest.api;

import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.participant.RequestParticipantContext;
import it.eng.dcp.common.service.did.DidDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * REST API controller for holder DID document.
 */
@RestController
@RequestMapping("/api/did")
public class DidDocumentAPIController {
    private final DidDocumentService didDocumentService;
    private final RequestParticipantContext requestParticipantContext;

    /**
     * Constructor.
     *
     * @param didDocumentService Service for DID document operations
     * @param requestParticipantContext Resolves the participant an administration request acts on
     */
    @Autowired
    public DidDocumentAPIController(DidDocumentService didDocumentService,
                                    RequestParticipantContext requestParticipantContext) {
        this.didDocumentService = didDocumentService;
        this.requestParticipantContext = requestParticipantContext;
    }

    /**
     * Get the holder DID document.
     *
     * @param request   the inbound request
     * @param principal the authenticated administration principal
     * @return DID document for the holder
     */
    @GetMapping
    public ResponseEntity<DidDocument> getDidDocument(HttpServletRequest request, Principal principal) {
        DidDocument didDocument = didDocumentService.provideDidDocument(
                requestParticipantContext.requireForAdmin(request, principal));
        return ResponseEntity.ok(didDocument);
    }
}
