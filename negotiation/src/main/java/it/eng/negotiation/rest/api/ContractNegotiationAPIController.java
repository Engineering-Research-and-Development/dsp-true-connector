package it.eng.negotiation.rest.api;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.serializer.Serializer;
import it.eng.negotiation.service.ContractNegotiationAPIService;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/v1/negotiations")
@Slf4j
public class ContractNegotiationAPIController {
	
	private ContractNegotiationAPIService apiService;

    public ContractNegotiationAPIController(ContractNegotiationAPIService apiService) {
		this.apiService = apiService;
	}
    
    /**
     * Returns only one Contract Negotiation by it's ID or a collection by their state.
     * If none are present then all Contract Negotiations will be returned.
     * @param contractNegotiationId
     * @param state
     * @return
     */
    @GetMapping(path = {"", "/{contractNegotiationId}"})
    public ResponseEntity<GenericApiResponse<Collection<JsonNode>>> getContractNegotiations(@PathVariable(required = false) String contractNegotiationId,
    		@RequestParam(required = false) String state){
    	Collection<JsonNode> contractNegotiations = apiService.findContractNegotiations(contractNegotiationId, state);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(contractNegotiations, "Fething contract negotiations", HttpStatus.OK.value()));
    } 

    //consumer starts contract negotiation
	@PostMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> startNegotiation(@RequestBody JsonNode startNegotiationRequest) {
    	String targetConnector = startNegotiationRequest.get("Forward-To").asText();
    	JsonNode offerNode = startNegotiationRequest.get(DSpaceConstants.OFFER);
    	log.info("Consumer starts negotaition with {}", targetConnector);
    	JsonNode response = apiService.startNegotiation(targetConnector, offerNode);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Contract negotiation initiated", HttpStatus.OK.value()));
    }
	
	@PutMapping(path = "/{contractNegotiationId}/accept")
    public ResponseEntity<GenericApiResponse<JsonNode>> acceptContractNegotiation(@PathVariable String contractNegotiationId) {
        log.info("Handling contract negotiation accepted by consumer");
        ContractNegotiation contractNegotiationApproved = apiService.handleContractNegotiationAccepted(contractNegotiationId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        		.body(GenericApiResponse.success(Serializer.serializeProtocolJsonNode(contractNegotiationApproved),
        				"Contract negotiation approved", HttpStatus.OK.value()));
    }
    
	@PutMapping(path = "/{contractNegotiationId}/terminate")
    public ResponseEntity<GenericApiResponse<JsonNode>> terminateContractNegotiation(@PathVariable String contractNegotiationId) {
        log.info("Handling contract negotiation approved");
        ContractNegotiation contractNegotiationTerminated = apiService.handleContractNegotiationTerminated(contractNegotiationId);
        
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        		.body(GenericApiResponse.success(Serializer.serializeProtocolJsonNode(contractNegotiationTerminated),
        				"Contract negotiation terminated", HttpStatus.OK.value()));
    }
    
	@PutMapping(path = "/{contractNegotiationId}/verify")
    public ResponseEntity<GenericApiResponse<JsonNode>> verifyContractNegotiation(@PathVariable String contractNegotiationId) {
    	log.info("Manual handling for verification message");
    	
        apiService.verifyNegotiation(contractNegotiationId);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(null, "Verified negotiation", HttpStatus.OK.value()));
    }
    
    /********* PROVIDER ***********/
	@PostMapping(path = "/offers")
    public ResponseEntity<GenericApiResponse<JsonNode>> sendContractOffer(@RequestBody JsonNode contractOfferRequest) {
    	String targetConnector = contractOfferRequest.get("Forward-To").asText();
    	JsonNode offerNode = contractOfferRequest.get(DSpaceConstants.OFFER);
    	log.info("Provider posts offer - starts negotaition with {}", targetConnector);
    	JsonNode response = apiService.sendContractOffer(targetConnector, offerNode);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Contract negotiation posted", HttpStatus.OK.value()));
    }
    
	@Deprecated
	@PostMapping(path = "/agreements")
    public ResponseEntity<GenericApiResponse<JsonNode>> sendAgreement(@RequestBody JsonNode contractAgreementRequest) {
    	JsonNode agreementNode = contractAgreementRequest.get(DSpaceConstants.AGREEMENT);
    	String consumerPid = contractAgreementRequest.get(DSpaceConstants.CONSUMER_PID).asText();
        String providerPid = contractAgreementRequest.get(DSpaceConstants.PROVIDER_PID).asText();
    	apiService.sendAgreement(consumerPid, providerPid, agreementNode);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(null, "Contract agreement sent", HttpStatus.OK.value()));
    }
	
	@PutMapping(path = "/{contractNegotiationId}/approve")
    public ResponseEntity<GenericApiResponse<JsonNode>> approveContractNegotiation(@PathVariable String contractNegotiationId) {
        log.info("Handling contract negotiation approved");
        ContractNegotiation contractNegotiationApproved = apiService.handleContractNegotiationAgreed(contractNegotiationId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        		.body(GenericApiResponse.success(Serializer.serializeProtocolJsonNode(contractNegotiationApproved),
        				"Contract negotiation approved", HttpStatus.OK.value()));
    }
    
	@PutMapping(path = "/{contractNegotiationId}/finalize")
    public ResponseEntity<GenericApiResponse<JsonNode>> finalizeNegotiation(@RequestBody JsonNode finalizeNegotiationRequest) {
    	String consumerPid = finalizeNegotiationRequest.get(DSpaceConstants.CONSUMER_PID).asText();
        String providerPid = finalizeNegotiationRequest.get(DSpaceConstants.PROVIDER_PID).asText();
    	apiService.finalizeNegotiation(consumerPid, providerPid);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(null, "Contract negotiation finalized", HttpStatus.OK.value()));
    }

}
