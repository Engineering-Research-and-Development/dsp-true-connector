package it.eng.negotiation.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractRequestMessageRequest;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.rest.api.PagedAPIResponse;
import it.eng.tools.service.GenericFilterBuilder;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.NEGOTIATION_V1)
@Slf4j
public class ContractNegotiationAPIController {

    private final ContractNegotiationAPIService apiService;
    private final GenericFilterBuilder filterBuilder;
    private final PagedResourcesAssembler<ContractNegotiation> pagedResourcesAssembler;
    private final PlainContractNegotiationAssembler plainAssembler;

    public ContractNegotiationAPIController(ContractNegotiationAPIService apiService, GenericFilterBuilder filterBuilder,
                                            PagedResourcesAssembler<ContractNegotiation> pagedResourcesAssembler,
                                            PlainContractNegotiationAssembler plainAssembler) {
        this.apiService = apiService;
        this.filterBuilder = filterBuilder;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
        this.plainAssembler = plainAssembler;
    }

    /**
     * Returns a single Contract Negotiation by its ID.
     *
     * @param contractNegotiationId the ID of the contract negotiation to retrieve
     * @return ResponseEntity containing the Contract Negotiation or an error response if not found
     */
    @GetMapping(path = "/{contractNegotiationId}")
    public ResponseEntity<GenericApiResponse<JsonNode>> getContractNegotiationById(@PathVariable String contractNegotiationId) {
        ContractNegotiation contractNegotiation = apiService.findContractNegotiationById(contractNegotiationId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(NegotiationSerializer.serializePlainJsonNode(contractNegotiation),
                        String.format("Contract negotiation with id %s found", contractNegotiationId)));
    }

    /**
     * Returns only one Contract Negotiation by it's ID or a collection by their state.<br>
     * If none are present then all Contract Negotiations will be returned.
     *
     * @param request the HTTP request containing additional parameters for filtering
     * @param page    the page number for pagination (default is 0)
     * @param size    the size of each page for pagination (default is 20)
     * @param sort    the sorting criteria in the format "field,direction" (default is "timestamp,desc")
     * @return ResponseEntity
     */
    @GetMapping()
    public ResponseEntity<PagedAPIResponse> getContractNegotiations(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp,desc") String[] sort) {

        Sort.Direction direction = sort[1].equalsIgnoreCase("desc") ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sorting = Sort.by(direction, sort[0]);
        Pageable pageable = PageRequest.of(page, size, sorting);
        // Build filter map automatically from ALL request parameters
        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        log.debug("Generated filters: {}", filters);

        Page<ContractNegotiation> contractNegotiations = apiService.findContractNegotiations(filters, pageable);
        PagedModel<EntityModel<Object>> pagedModel = pagedResourcesAssembler.toModel(contractNegotiations, plainAssembler);

        String filterString = filters.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(PagedAPIResponse.of(pagedModel,
                        "Contract negotiation - Page " + page + " of " + contractNegotiations.getTotalPages() + ", Size: " + size +
                                ", Sort: " + sorting + ", Filters: [" + filterString + "]"));
    }

    /**
     * Consumer starts contract negotiation.
     *
     * @param contractRequestMessageRequest the request containing the target connector and offer details
     * @return ResponseEntity
     */
    @PostMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> sendContractRequestMessage(@RequestBody ContractRequestMessageRequest contractRequestMessageRequest) {
        log.info("Sending contract request message");
        ContractNegotiation response = apiService.sendContractRequestMessage(contractRequestMessageRequest);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(NegotiationSerializer.serializePlainJsonNode(response), "Contract negotiation initiated"));
    }

    /**
     * Accepts contract negotiation.
     *
     * @param contractNegotiationId the ID of the contract negotiation to accept
     * @return ResponseEntity
     */
    @PutMapping(path = "/{contractNegotiationId}/accept")
    public ResponseEntity<GenericApiResponse<JsonNode>> acceptContractNegotiation(@PathVariable String contractNegotiationId) {
        log.info("Handling contract negotiation accepted by consumer");
        ContractNegotiation contractNegotiationApproved = apiService.handleContractNegotiationAccepted(contractNegotiationId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationApproved),
                        "Contract negotiation approved"));
    }

    /**
     * Terminate contract negotiation.
     *
     * @param contractNegotiationId the ID of the contract negotiation to terminate
     * @return ResponseEntity
     */
    @PutMapping(path = "/{contractNegotiationId}/terminate")
    public ResponseEntity<GenericApiResponse<JsonNode>> terminateContractNegotiation(@PathVariable String contractNegotiationId) {
        log.info("Handling contract negotiation approved");
        ContractNegotiation contractNegotiationTerminated = apiService.terminateContractNegotiation(contractNegotiationId);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationTerminated),
                        "Contract negotiation terminated"));
    }

    /**
     * Verify contract negotiation.
     *
     * @param contractNegotiationId the ID of the contract negotiation to verify
     * @return ResponseEntity
     */
    @PutMapping(path = "/{contractNegotiationId}/verify")
    public ResponseEntity<GenericApiResponse<Void>> verifyContractNegotiation(@PathVariable String contractNegotiationId) {
        log.info("Manual handling for verification message");

        apiService.verifyNegotiation(contractNegotiationId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, "Verified negotiation"));
    }

    /********* PROVIDER ***********/
    /**
     * Provider sends offer.
     *
     * @param contractRequestMessageRequest the request containing the target connector and offer details
     * @return ResponseEntity
     */
    @PostMapping(path = "/offers")
    public ResponseEntity<GenericApiResponse<JsonNode>> sendContractOffer(@RequestBody ContractRequestMessageRequest contractRequestMessageRequest) {
        ContractNegotiation response = apiService.sendContractOfferMessage(contractRequestMessageRequest);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(NegotiationSerializer.serializeProtocolJsonNode(response), "Contract negotiation posted"));
    }

    @Deprecated
    @PostMapping(path = "/agreements")
    public ResponseEntity<GenericApiResponse<Void>> sendAgreement(@RequestBody JsonNode contractAgreementRequest) {
        JsonNode agreementNode = contractAgreementRequest.get(DSpaceConstants.AGREEMENT);
        String consumerPid = contractAgreementRequest.get(DSpaceConstants.CONSUMER_PID).asText();
        String providerPid = contractAgreementRequest.get(DSpaceConstants.PROVIDER_PID).asText();
        apiService.sendAgreement(consumerPid, providerPid, agreementNode);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, "Contract agreement sent"));
    }

    /**
     * Provider approve contract negotiation.
     *
     * @param contractNegotiationId the ID of the contract negotiation to approve
     * @return ResponseEntity
     */
    @PutMapping(path = "/{contractNegotiationId}/approve")
    public ResponseEntity<GenericApiResponse<JsonNode>> approveContractNegotiation(@PathVariable String contractNegotiationId) {
        log.info("Handling contract negotiation approved");
        ContractNegotiation contractNegotiationApproved = apiService.approveContractNegotiation(contractNegotiationId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationApproved),
                        "Contract negotiation approved"));
    }

    /**
     * Provider finalize contract negotiation.
     *
     * @param contractNegotiationId the ID of the contract negotiation to finalize
     * @return ResponseEntity
     */
    @PutMapping(path = "/{contractNegotiationId}/finalize")
    public ResponseEntity<GenericApiResponse<Void>> finalizeNegotiation(@PathVariable String contractNegotiationId) {
        apiService.finalizeNegotiation(contractNegotiationId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, "Contract negotiation finalized"));
    }

}
