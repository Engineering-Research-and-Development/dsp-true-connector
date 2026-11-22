package it.eng.dcp.issuer.rest;

import it.eng.dcp.issuer.service.StatusListService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the Issuer's signed status list credentials (DCP v1.0.1 §6.10).
 *
 * <p>The endpoint is public by design: Verifiers fetch the list the {@code statusListCredential} of a credential
 * points at without authentication. The list carries only status bits, no holder data.
 */
@RestController
@RequestMapping(path = "/issuer/status")
public class StatusListController {

    private final StatusListService statusListService;

    /**
     * Creates the controller.
     *
     * @param statusListService the status list service
     */
    public StatusListController(StatusListService statusListService) {
        this.statusListService = statusListService;
    }

    /**
     * Returns the signed status list credential of a list as a compact JWT.
     *
     * @param listId the list ID, the last path segment of the {@code statusListCredential} URL
     * @return 200 with the JWT ({@code application/vc+ld+jwt} for BitstringStatusList, {@code application/jwt} for
     *         StatusList2021), or 404 when the list does not exist
     */
    @GetMapping(path = "/{listId}")
    public ResponseEntity<String> getStatusList(@PathVariable String listId) {
        return statusListService.publishStatusList(listId)
                .map(published -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(published.mediaType()))
                        .body(published.credential()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
