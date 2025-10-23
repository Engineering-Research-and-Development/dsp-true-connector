package it.eng.negotiation.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.model.*;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationProviderStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/negotiations")
@Slf4j
public class ContractNegotiationProviderController {

    private final ContractNegotiationProviderStrategy providerService;

    public ContractNegotiationProviderController(ContractNegotiationProviderStrategy providerService) {
        super();
        this.providerService = providerService;
    }

    // 2.1 The negotiation endpoint
    // GET
    // Provider must return an HTTP 200 (OK) response and a body containing the Contract Negotiation
    @GetMapping(path = "/{providerPid}")
    public ResponseEntity<JsonNode> getNegotiationByProviderPid(@PathVariable String providerPid) {
        log.info("Get negotiation by provider pid");

        ContractNegotiation contractNegotiation = providerService.getNegotiationByProviderPid(providerPid);

        return ResponseEntity.ok()
                .body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    // 2.2 The provider negotiations/request resource
    // POST
    // The Provider must return an HTTP 201 (Created); "dspace:state" :"REQUESTED"
    @PostMapping(path = "/request")
    public ResponseEntity<JsonNode> handleContractRequestMessage(@RequestBody JsonNode contractRequestMessageJsonNode) {
        log.info("Creating negotiation");
        ContractRequestMessage crm = NegotiationSerializer.deserializeProtocol(contractRequestMessageJsonNode, ContractRequestMessage.class);
        ContractNegotiation cn = providerService.handleContractRequestMessage(crm);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(cn.getProviderPid()).toUri();

        log.info("Initial contract request message successfully processed, contract negotiation with id {} created, sending response 201 Created", cn.getId());

        return ResponseEntity.created(location)
                .body(NegotiationSerializer.serializeProtocolJsonNode(cn));
    }

    // 2.3 The provider negotiations/:providerPid/request resource
    // POST
    // Provider must return an HTTP 200 (OK) response. The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/request")
    public ResponseEntity<JsonNode> handleContractRequestMessageAsCounterOffer(@PathVariable String providerPid,
                                                                               @RequestBody JsonNode contractRequestMessageJsonNode) {
        log.info("Processing consumer counter-offer");
        ContractRequestMessage crm = NegotiationSerializer.deserializeProtocol(contractRequestMessageJsonNode, ContractRequestMessage.class);
        ContractNegotiation cn = providerService.handleContractRequestMessageAsCounteroffer(providerPid, crm);

        log.info("Contract request message as counteroffer successfully processed for providerPid {}, sending response 200", providerPid);

        return ResponseEntity.ok()
                .body(NegotiationSerializer.serializeProtocolJsonNode(cn));
    }

    // 2.4 The provider negotiations/:providerPid/events
    // POST
    // provider must return an HTTP code 200 (OK). The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/events")
    public ResponseEntity<JsonNode> handleContractNegotiationEventMessageAccepted(@PathVariable String providerPid,
                                                                                  @RequestBody JsonNode contractNegotiationEventMessageJsonNode) {
        ContractNegotiationEventMessage contractNegotiationEventMessage = NegotiationSerializer.deserializeProtocol(contractNegotiationEventMessageJsonNode, ContractNegotiationEventMessage.class);
        log.info(contractNegotiationEventMessage.toString());

        ContractNegotiation contractNegotiation = providerService.handleContractNegotiationEventMessageAccepted(providerPid, contractNegotiationEventMessage);

        log.info("Contract negotiation event message accepted successfully processed for providerPid {}, sending response 200", providerPid);

        return ResponseEntity.ok()
                .body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    // 2.5 The provider negotiations/:providerPid/agreement/verification resource
    // POST
    // Provider must return an HTTP code 200 (OK). The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/agreement/verification")
    public ResponseEntity<Void> handleContractAgreementVerificationMessage(@PathVariable String providerPid,
                                                                           @RequestBody JsonNode contractAgreementVerificationMessageJsonNode) {
        ContractAgreementVerificationMessage cavm =
                NegotiationSerializer.deserializeProtocol(contractAgreementVerificationMessageJsonNode, ContractAgreementVerificationMessage.class);
        log.info("Verification message received");

        providerService.handleContractAgreementVerificationMessage(providerPid, cavm);

        log.info("Contract agreement verification message successfully processed for providerPid {}", providerPid);

        return ResponseEntity.ok()
                .build();
    }

    // 2.6 The provider negotiations/:id/termination resource
    // POST
    // Provider must return HTTP code 200 (OK). The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/termination")
    public ResponseEntity<Void> handleContractNegotiationTerminationMessage(@PathVariable String providerPid,
                                                                            @RequestBody JsonNode contractNegotiationTerminationMessageJsonNode) {
        ContractNegotiationTerminationMessage contractNegotiationTerminationMessage =
                NegotiationSerializer.deserializeProtocol(contractNegotiationTerminationMessageJsonNode, ContractNegotiationTerminationMessage.class);

        providerService.handleContractNegotiationTerminationMessage(providerPid, contractNegotiationTerminationMessage);

        log.info("Contract negotiation termination message successfully processed for providerPid {}, sending response 200", providerPid);

        return ResponseEntity.ok()
                .build();
    }

}
