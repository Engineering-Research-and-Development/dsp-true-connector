package it.eng.negotiation.rest.protocol;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.Description;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.service.CallbackHandler;
import it.eng.negotiation.service.ContractNegotiationService;
import it.eng.tools.model.DSpaceConstants;
import lombok.extern.java.Log;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/negotiations")
@Log
public class ProviderContractNegotiationController {

    private ContractNegotiationService contractNegotiationService;

    public ProviderContractNegotiationController(ContractNegotiationService contractNegotiationService,
                                         CallbackHandler callbackHandler) {
        super();
        this.contractNegotiationService = contractNegotiationService;
    }

    // 2.1 The negotiation endpoint
    // GET
    // Provider must return an HTTP 200 (OK) response and a body containing the Contract Negotiation
    @GetMapping(path = "/{providerPid}")
    public ResponseEntity<JsonNode>  getNegotiationByProviderPid(@PathVariable String providerPid) throws InterruptedException, ExecutionException {
        log.info("Get negotiation by provider pid");
		ContractNegotiation contractNegotiation = contractNegotiationService.getNegotiationByProviderPid(providerPid).get();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializeProtocolJsonNode(contractNegotiation));
    }

    // 2.2 The provider negotiations/request resource
    // POST
    // The Provider must return an HTTP 201 (Created); "dspace:state" :"REQUESTED"
    @PostMapping(path = "/request")
    public ResponseEntity<JsonNode> createNegotiation(@RequestBody JsonNode contractRequestMessageJsonNode) 
    		throws InterruptedException, ExecutionException {
        log.info("Creating negotiation");
        ContractRequestMessage crm = Serializer.deserializeProtocol(contractRequestMessageJsonNode, ContractRequestMessage.class);
        CompletableFuture<JsonNode> responseNode = contractNegotiationService.startContractNegotiation(crm);
       
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand("123").toUri();

        return ResponseEntity.created(location).contentType(MediaType.APPLICATION_JSON).body(responseNode.get());
    }

    // 2.3 The provider negotiations/:providerPid/request resource
    // POST
    // Provider must return an HTTP 200 (OK) response. The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/request")
    public ResponseEntity<JsonNode> handleConsumerMakesOffer(@PathVariable String providerPid,
                                                                         @RequestBody JsonNode contractRequestMessageJsonNode) {
        ContractRequestMessage crm = Serializer.deserializeProtocol(contractRequestMessageJsonNode, ContractRequestMessage.class);

        ContractNegotiationErrorMessage error = methodNotYetImplemented();
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializeProtocolJsonNode(error));
    }

    // 2.4 The provider negotiations/:providerPid/events
    // POST
    // provider must return an HTTP code 200 (OK). The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/events")
    public ResponseEntity<JsonNode> handleNegotiationEventMessage(@PathVariable String providerPid, 
    		@RequestBody JsonNode contractNegotiationEventMessageJsonNode) {
    	 ContractNegotiationEventMessage cnem = Serializer.deserializeProtocol(contractNegotiationEventMessageJsonNode, ContractNegotiationEventMessage.class);
         log.info(cnem.toString());
         
         ContractNegotiationErrorMessage error = methodNotYetImplemented();
         return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializeProtocolJsonNode(error));

    }

    // 2.5 The provider negotiations/:id/agreement/verification resource
    // POST
	// Provider must return an HTTP code 200 (OK). The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/agreement/verification")
    public ResponseEntity<JsonNode> handleVerifyAgreement(@PathVariable String providerPid,
                                                               @RequestBody JsonNode contractAgreementVerificationMessageJsonNode) {
        ContractAgreementVerificationMessage cavm = 
        		Serializer.deserializeProtocol(contractAgreementVerificationMessageJsonNode, ContractAgreementVerificationMessage.class);

        ContractNegotiationErrorMessage error = methodNotYetImplemented();
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializeProtocolJsonNode(error));
    }

    // 2.6 The provider negotiations/:id/termination resource
    // POST
    // Provider must return HTTP code 200 (OK). The response body is not specified and clients are not required to process it.
    @PostMapping(path = "/{providerPid}/termination")
    public ResponseEntity<JsonNode> handleTerminationMessage(@PathVariable String providerPid, 
    		@RequestBody JsonNode contractNegotiationTerminationMessageJsonNode) {
        ContractNegotiationTerminationMessage cntm = 
        		Serializer.deserializeProtocol(contractNegotiationTerminationMessageJsonNode, ContractNegotiationTerminationMessage.class);

        ContractNegotiationErrorMessage error = methodNotYetImplemented();
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializeProtocolJsonNode(error));
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
