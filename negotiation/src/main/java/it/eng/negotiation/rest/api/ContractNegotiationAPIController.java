package it.eng.negotiation.rest.api;

import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.serializer.Serializer;
import it.eng.negotiation.service.ContractNegotiationAPIService;
import it.eng.negotiation.service.ContractNegotiationEventHandlerService;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;
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
    
    @GetMapping(path={ "/find", "/find/{state}" })
    public ResponseEntity<GenericApiResponse<Collection<JsonNode>>> findContractNegotations(@PathVariable(required = false) String state){
    	Collection<JsonNode> contractNegotiations = apiService.findContractNegotiations(state);
    	String message = StringUtils.isNotBlank(state) ? "Contract negotiations - " + state : "Contract negotiations";
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(contractNegotiations, message, HttpStatus.OK.value()));
    } 

    //consumer starts contract negotiation
	@PostMapping(path = "/startNegotiation")
    public ResponseEntity<GenericApiResponse<JsonNode>> startNegotiation(@RequestBody JsonNode startNegotiationRequest) {
    	String targetConnector = startNegotiationRequest.get("Forward-To").asText();
    	JsonNode offerNode = startNegotiationRequest.get(DSpaceConstants.OFFER);
    	log.info("Consumer starts negotaition with {}", targetConnector);
    	JsonNode response = apiService.startNegotiation(targetConnector, offerNode);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Contract negotiation initiated", HttpStatus.OK.value()));
    }
	
	@Deprecated(since = "Please use negotiationApproved negotiationTerminated instead")
    @PostMapping(path = "/offerApproved")
    public ResponseEntity<JsonNode> handleOfferApproved(@RequestBody JsonNode response) {
        log.info("Handling offer approved");
        String consumerPid = response.get(DSpaceConstants.CONSUMER_PID).asText();
        String providerPid = response.get(DSpaceConstants.PROVIDER_PID).asText();
        boolean offerAccepted = response.get("offerAccepted").asBoolean();
        JsonNode offer = response.get(DSpaceConstants.OFFER);
        ContractNegotiationOfferResponseEvent offerResponse = new ContractNegotiationOfferResponseEvent(consumerPid, providerPid, offerAccepted, offer);
        handlerService.handleContractNegotiationOfferResponse(offerResponse);
        
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
    }
	
	@PostMapping(path = "/negotiationAccepted/{contractNegotiationId}")
    public ResponseEntity<GenericApiResponse<JsonNode>> handleContractNegotationAccepted(@PathVariable String contractNegotiationId) {
        log.info("Handling contract negotiation accepted by consumer");
        ContractNegotiation contractNegotiationApproved = apiService.handleContractNegotiationAccepted(contractNegotiationId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        		.body(GenericApiResponse.success(Serializer.serializeProtocolJsonNode(contractNegotiationApproved),
        				"Contract negotiation approved", HttpStatus.OK.value()));
    }
    
    @PostMapping(path = "/negotiationApproved/{contractNegotiationId}")
    public ResponseEntity<GenericApiResponse<JsonNode>> handleContractNegotationAgreed(@PathVariable String contractNegotiationId) {
        log.info("Handling contract negotiation approved");
        ContractNegotiation contractNegotiationApproved = apiService.handleContractNegotiationAgreed(contractNegotiationId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        		.body(GenericApiResponse.success(Serializer.serializeProtocolJsonNode(contractNegotiationApproved),
        				"Contract negotiation approved", HttpStatus.OK.value()));
    }
    
    @PostMapping(path = "/negotiationTerminated/{contractNegotiationId}")
    public ResponseEntity<GenericApiResponse<JsonNode>> handleContractNegotationTerminated(@PathVariable String contractNegotiationId) {
        log.info("Handling contract negotiation approved");
        ContractNegotiation contractNegotiationTerminated = handlerService.handleContractNegotiationTerminated(contractNegotiationId);
        
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        		.body(GenericApiResponse.success(Serializer.serializeProtocolJsonNode(contractNegotiationTerminated),
        				"Contract negotiation terminated", HttpStatus.OK.value()));
    }
    
    @PostMapping(path = "/verifyNegotiation")
    public ResponseEntity<GenericApiResponse<JsonNode>> verifyNegotiation(@RequestBody JsonNode verifyNegotiationJson) {
    	log.info("Manual handling for verification message");
    	String consumerPid = verifyNegotiationJson.get(DSpaceConstants.CONSUMER_PID).asText();
        String providerPid = verifyNegotiationJson.get(DSpaceConstants.PROVIDER_PID).asText();
    	ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.build();
    	handlerService.verifyNegotiation(verificationMessage);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(null, "Verified negotiation", HttpStatus.OK.value()));
    }
    
    /********* PROVIDER ***********/
    @PostMapping(path = "/sendOffer")
    public ResponseEntity<GenericApiResponse<JsonNode>> sendOffer(@RequestBody JsonNode contractOfferRequest) {
    	String targetConnector = contractOfferRequest.get("Forward-To").asText();
    	JsonNode offerNode = contractOfferRequest.get(DSpaceConstants.OFFER);
    	log.info("Provider posts offer - starts negotaition with {}", targetConnector);
    	JsonNode response = apiService.sendContractOffer(targetConnector, offerNode);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(response, "Contract negotiation posted", HttpStatus.OK.value()));
    }
    
    @PostMapping(path = "/sendAgreement")
    public ResponseEntity<GenericApiResponse<JsonNode>> sendAgreement(@RequestBody JsonNode contractAgreementRequest) {
    	JsonNode agreementNode = contractAgreementRequest.get(DSpaceConstants.AGREEMENT);
    	String consumerPid = contractAgreementRequest.get(DSpaceConstants.CONSUMER_PID).asText();
        String providerPid = contractAgreementRequest.get(DSpaceConstants.PROVIDER_PID).asText();
    	apiService.sendAgreement(consumerPid, providerPid, agreementNode);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(null, "Contract agreement sent", HttpStatus.OK.value()));
    }
    
    @PostMapping(path = "/finalizeNegotiation")
    public ResponseEntity<GenericApiResponse<JsonNode>> finalizeNegotiation(@RequestBody JsonNode finalizeNegotiationRequest) {
    	String consumerPid = finalizeNegotiationRequest.get(DSpaceConstants.CONSUMER_PID).asText();
        String providerPid = finalizeNegotiationRequest.get(DSpaceConstants.PROVIDER_PID).asText();
        ContractNegotiationEventMessage  contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.eventType(ContractNegotiationEventType.FINALIZED)
				.build();
    	apiService.finalizeNegotiation(contractNegotiationEventMessage);
    	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
    			.body(GenericApiResponse.success(null, "Contract negotiation finalized", HttpStatus.OK.value()));
    }

}
