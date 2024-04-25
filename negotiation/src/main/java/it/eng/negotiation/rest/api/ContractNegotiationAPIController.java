package it.eng.negotiation.rest.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.service.ContractNegotiationAPIService;
import it.eng.negotiation.service.ContractNegotiationEventHandlerService;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponse;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/negotiations")
@Slf4j
public class ContractNegotiationAPIController {
	
	private ContractNegotiationEventHandlerService handlerService;
	private ContractNegotiationAPIService apiService;

    public ContractNegotiationAPIController(ContractNegotiationEventHandlerService handlerService,
			ContractNegotiationAPIService apiService) {
		this.handlerService = handlerService;
		this.apiService = apiService;
	}

	@PostMapping(path = "/startNegotiation")
    public ResponseEntity<GenericApiResponse<JsonNode>> startNegotiation(@RequestBody JsonNode startNegotiationRequest) {
    	String targetConnector = startNegotiationRequest.get("Forward-To").asText();
    	JsonNode offerNode = startNegotiationRequest.get("offer");
    	log.info("Consumer starts negotaition with {}", targetConnector);
    	JsonNode response = apiService.startNegotiation(targetConnector, offerNode);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Contract negotiation initiated"));
    }
    
    @PostMapping(path = "/offerApproved")
    public ResponseEntity<JsonNode> handleOfferApproved(@RequestBody JsonNode response) {
        log.info("Handling offer approved");
        String consumerPid = response.get(DSpaceConstants.CONSUMER_PID).asText();
        String providerPid = response.get(DSpaceConstants.PROVIDER_PID).asText();
        boolean offerAccepted = response.get("offerAccepted").asBoolean();
        JsonNode offer = response.get(DSpaceConstants.OFFER);
        ContractNegotiationOfferResponse offerResponse = new ContractNegotiationOfferResponse(consumerPid, providerPid, offerAccepted, offer);
        handlerService.handleContractNegotiationOfferResponse(offerResponse);
        
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(null);
    }
    
    @PostMapping(path = "/verifyNegotiation")
    public ResponseEntity<JsonNode> verifyNegotiation(@RequestBody JsonNode verifyNegotiationJson) {
    	log.info("Manual handling for verification message");
    	String consumerPid = verifyNegotiationJson.get(DSpaceConstants.CONSUMER_PID).asText();
        String providerPid = verifyNegotiationJson.get(DSpaceConstants.PROVIDER_PID).asText();
    	ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.build();
    	handlerService.contractAgreementVerificationMessage(verificationMessage);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(null);
    }

}
