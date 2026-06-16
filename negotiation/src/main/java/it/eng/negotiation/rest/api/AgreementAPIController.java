package it.eng.negotiation.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.AgreementAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.rest.api.PagedAPIResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.NEGOTIATION_AGREEMENTS_V1)
@Slf4j
public class AgreementAPIController {

    private final AgreementAPIService agreementAPIService;
    private final PagedResourcesAssembler<Agreement> pagedResourcesAssembler;
    private final PlainAgreementAssembler plainAssembler;

    public AgreementAPIController(AgreementAPIService agreementAPIService,
                                  PagedResourcesAssembler<Agreement> pagedResourcesAssembler,
                                  PlainAgreementAssembler plainAssembler) {
        super();
        this.agreementAPIService = agreementAPIService;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
        this.plainAssembler = plainAssembler;
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


//        agreementAPIService.getCurrentCount(agreementId).ifPresent(count -> agreementJson.put("currentCount", count));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(agreementEnriched,
                        String.format("Agreement with id %s found", agreementId)));
    }

    /**
     * Returns a paginated list of all agreements.
     *
     * @param page the page number, zero-based (default 0)
     * @param size the number of items per page (default 20)
     * @param sort the sort field and direction in the format "field,direction" (default "timestamp,desc")
     * @return ResponseEntity containing a paginated list of agreements
     */
    @GetMapping
    public ResponseEntity<PagedAPIResponse> getAgreements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp,desc") String[] sort) {
        Sort.Direction direction = sort[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sorting = Sort.by(direction, sort[0]);
        Pageable pageable = PageRequest.of(page, size, sorting);

        Page<Agreement> agreements = agreementAPIService.findAgreements(pageable);
        PagedModel<EntityModel<Object>> pagedModel = pagedResourcesAssembler.toModel(agreements, plainAssembler);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(PagedAPIResponse.of(pagedModel,
                        "Agreements - Page " + page + " of " + agreements.getTotalPages()
                                + ", Size: " + size + ", Sort: " + sorting));
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
}