package it.eng.negotiation.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.service.AgreementAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.NEGOTIATION_AGREEMENTS_V1)
@Slf4j
public class AgreementAPIController {

    private final AgreementAPIService agreementAPIService;

    public AgreementAPIController(AgreementAPIService agreementAPIService) {
        super();
        this.agreementAPIService = agreementAPIService;
    }

    /**
     * Enforce an agreement by its ID.
     *
     * @param agreementId the ID of the agreement to enforce
     * @return ResponseEntity with a status message
     */
    @PostMapping(path = "/{agreementId}/enforce")
    public ResponseEntity<GenericApiResponse<String>> enforceAgreement(@PathVariable("agreementId") String agreementId) {
        log.info("Enforcing agreement");
        agreementAPIService.enforceAgreement(agreementId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success("Agreement enforcement is valid", "Agreement enforcement is ok"));
    }

    /**
     * Returns a single agreement by its ID.
     *
     * @param agreementId the ID of the agreement to retrieve
     * @return ResponseEntity containing the agreement or an error response if not found
     */
    @GetMapping(path = "/{agreementId}")
    public ResponseEntity<GenericApiResponse<JsonNode>> getAgreementById(
            @PathVariable("agreementId") String agreementId) {
        log.info("Fetching agreement with id {}", agreementId);
        JsonNode agreementEnriched = agreementAPIService.findAgreementByIdEnriched(agreementId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(agreementEnriched,
                        String.format("Agreement with id %s found", agreementId)));
    }


}