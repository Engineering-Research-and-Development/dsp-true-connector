package it.eng.negotiation.rest.api;

import java.util.Collection;

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
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = ApiEndpoints.NEGOTIATION_V1)
@Slf4j
public class ContractNegotiationAPIController {
	
	private ContractNegotiationAPIService apiService;

    public ContractNegotiationAPIController(ContractNegotiationAPIService apiService) {
		this.apiService = apiService;
	}
    
    /**
     * Returns only one Contract Negotiation by it's ID or a collection by their state.<br>
     * If none are present then all Contract Negotiations will be returned.
     * @param contractNegotiationId
     * @param state
     * @param role
     * @param consumerPid
     * @param providerPid
     * @return ResponseEntity
     */
    @GetMapping(path = {"", "/{contractNegotiationId}"})
    public ResponseEntity<GenericApiResponse<Collection<JsonNode>>> getContractNegotiations(@PathVariable(required = false) String contractNegotiationId,
    		@RequestParam(required = false) String state, 
    		@RequestParam(required = false) String role,
    		@RequestParam(required = false) String consumerPid,
    		@RequestParam(required = false) String providerPid){
    	Collection<JsonNode> contractNegotiations = apiService.findContractNegotiations(contractNegotiationId, state, role, consumerPid, providerPid);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(contractNegotiations, "Fetching contract negotiations"));
    } 
    
    /**
     * Consumer starts contract negotiation.
     * @param startNegotiationRequest
     * @return ResponseEntity
     */
	@PostMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> startNegotiation(@RequestBody JsonNode startNegotiationRequest) {
    	String targetConnector = startNegotiationRequest.get("Forward-To").asText();
    	JsonNode offerNode = startNegotiationRequest.get(DSpaceConstants.OFFER);
    	log.info("Consumer starts negotaition with {}", targetConnector);
    	JsonNode response = apiService.startNegotiation(targetConnector, offerNode);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Contract negotiation initiated"));
    }
	
	/**
	 * Accepts contract negotiation.
	 * @param contractNegotiationId
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
	 * @param contractNegotiationId
	 * @return ResponseEntity
	 */
	@PutMapping(path = "/{contractNegotiationId}/terminate")
    public ResponseEntity<GenericApiResponse<JsonNode>> terminateContractNegotiation(@PathVariable String contractNegotiationId) {
        log.info("Handling contract negotiation approved");
        ContractNegotiation contractNegotiationTerminated = apiService.handleContractNegotiationTerminated(contractNegotiationId);
        
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        		.body(GenericApiResponse.success(NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationTerminated),
        				"Contract negotiation terminated"));
    }
    
	/**
	 * Verify contract negotiation.
	 * @param contractNegotiationId
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
	 * @param contractOfferRequest
	 * @return ResponseEntity
	 */
	@PostMapping(path = "/offers")
    public ResponseEntity<GenericApiResponse<JsonNode>> sendContractOffer(@RequestBody JsonNode contractOfferRequest) {
    	String targetConnector = contractOfferRequest.get("Forward-To").asText();
    	JsonNode offerNode = contractOfferRequest.get(DSpaceConstants.OFFER);
    	log.info("Provider posts offer - starts negotaition with {}", targetConnector);
    	JsonNode response = apiService.sendContractOffer(targetConnector, offerNode);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Contract negotiation posted"));
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
	 * @param contractNegotiationId
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
	 * @param contractNegotiationId
	 * @return ResponseEntity
	 */
	@PutMapping(path = "/{contractNegotiationId}/finalize")
    public ResponseEntity<GenericApiResponse<Void>> finalizeNegotiation(@PathVariable String contractNegotiationId) {
    	apiService.finalizeNegotiation(contractNegotiationId);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(null, "Contract negotiation finalized"));
    }

}
