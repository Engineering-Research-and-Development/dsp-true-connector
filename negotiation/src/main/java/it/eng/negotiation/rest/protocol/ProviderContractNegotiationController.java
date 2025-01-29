package it.eng.negotiation.rest.protocol;

import java.net.URI;
import java.util.Arrays;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.Description;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationProviderService;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/negotiations")
@Slf4j
public class ProviderContractNegotiationController {

    private ContractNegotiationProviderService providerService;

    public ProviderContractNegotiationController(ContractNegotiationProviderService providerService) {
        super();
        this.providerService = providerService;
    }

    // 2.1 The negotiation endpoint
    // GET
    // Provider must return an HTTP 200 (OK) response and a body containing the Contract Negotiation
    @GetMapping(path = "/{providerPid}")
    public ResponseEntity<JsonNode>  getNegotiationByProviderPid(@PathVariable String providerPid) {
        log.info("Get negotiation by provider pid");
		
        ContractNegotiation contractNegotiation = providerService.getNegotiationByProviderPid(providerPid);
        
		return ResponseEntity.ok()
        		.body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    // 2.2 The provider negotiations/request resource
    // POST
    // The Provider must return an HTTP 201 (Created); "dspace:state" :"REQUESTED"
    @PostMapping(path = "/request")
    public ResponseEntity<JsonNode> createNegotiation(@RequestBody JsonNode contractRequestMessageJsonNode) throws InterruptedException {
        log.info("Creating negotiation");
        ContractRequestMessage crm = NegotiationSerializer.deserializeProtocol(contractRequestMessageJsonNode, ContractRequestMessage.class);
        ContractNegotiation cn = providerService.startContractNegotiation(crm);
        
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(cn.getProviderPid()).toUri();

        return ResponseEntity.created(location)
        		.body(NegotiationSerializer.serializeProtocolJsonNode(cn));
    }

    // 2.3 The provider negotiations/:providerPid/request resource
    // POST
    // Provider must return an HTTP 200 (OK) response. The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/request")
    public ResponseEntity<JsonNode> handleConsumerMakesOffer(@PathVariable String providerPid,
                                                                         @RequestBody JsonNode contractRequestMessageJsonNode) {
        ContractRequestMessage crm = NegotiationSerializer.deserializeProtocol(contractRequestMessageJsonNode, ContractRequestMessage.class);

        ContractNegotiationErrorMessage error = methodNotYetImplemented();
        
        return ResponseEntity.internalServerError()
        		.body(NegotiationSerializer.serializeProtocolJsonNode(error));
    }

    // 2.4 The provider negotiations/:providerPid/events
    // POST
    // provider must return an HTTP code 200 (OK). The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/events")
    public ResponseEntity<JsonNode> handleNegotiationEventMessage(@PathVariable String providerPid, 
    		@RequestBody JsonNode contractNegotiationEventMessageJsonNode) {
    	 ContractNegotiationEventMessage contractNegotiationEventMessage = NegotiationSerializer.deserializeProtocol(contractNegotiationEventMessageJsonNode, ContractNegotiationEventMessage.class);
         log.info(contractNegotiationEventMessage.toString());
         
         ContractNegotiation contractNegotiation = providerService.handleContractNegotationEventMessage(contractNegotiationEventMessage);
       
         return ResponseEntity.ok()
        		 .body(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation));
    }

    // 2.5 The provider negotiations/:providerPid/agreement/verification resource
    // POST
	// Provider must return an HTTP code 200 (OK). The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/agreement/verification")
    public ResponseEntity<Void> handleVerifyAgreement(@PathVariable String providerPid,
                                                               @RequestBody JsonNode contractAgreementVerificationMessageJsonNode) {
        ContractAgreementVerificationMessage cavm = 
        		NegotiationSerializer.deserializeProtocol(contractAgreementVerificationMessageJsonNode, ContractAgreementVerificationMessage.class);
        log.info("Verification message received");
        
        providerService.verifyNegotiation(cavm);
        
        return ResponseEntity.ok()
//        		.contentType(MediaType.APPLICATION_JSON)
        		.build();
    }

    // 2.6 The provider negotiations/:id/termination resource
    // POST
    // Provider must return HTTP code 200 (OK). The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/termination")
    public ResponseEntity<Void> handleTerminationMessage(@PathVariable String providerPid, 
    		@RequestBody JsonNode contractNegotiationTerminationMessageJsonNode) {
        ContractNegotiationTerminationMessage contractNegotiationTerminationMessage = 
        		NegotiationSerializer.deserializeProtocol(contractNegotiationTerminationMessageJsonNode, ContractNegotiationTerminationMessage.class);

        providerService.handleTerminationRequest(providerPid, contractNegotiationTerminationMessage);
        
        return ResponseEntity.ok()
//        		.contentType(MediaType.APPLICATION_JSON)
        		.build();
    }

	private ContractNegotiationErrorMessage methodNotYetImplemented() {
		ContractNegotiationErrorMessage cnem = ContractNegotiationErrorMessage.Builder.newInstance()
        		.code("1")
        		.consumerPid("NOT IMPLEMENTED")
        		.providerPid("NOT IMPLEMENTED")
        		.description(Arrays.asList(Description.Builder.newInstance().language("en").value("Not implemented").build()))
        		.build();
		return cnem;
	}

}
