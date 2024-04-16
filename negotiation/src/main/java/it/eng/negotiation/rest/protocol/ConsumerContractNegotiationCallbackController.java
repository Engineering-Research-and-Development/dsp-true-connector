package it.eng.negotiation.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.model.*;
import it.eng.negotiation.service.CallbackHandler;
import it.eng.negotiation.service.ContractNegotiationConsumerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class ConsumerContractNegotiationCallbackController {
	
	private ContractNegotiationConsumerService contractNegotiationConsumerService;
	private CallbackHandler callbackHandler;
	
	public ConsumerContractNegotiationCallbackController(ContractNegotiationConsumerService contractNegotiationConsumerService,
			CallbackHandler callbackHandler) {
		super();
		this.contractNegotiationConsumerService = contractNegotiationConsumerService;
		this.callbackHandler = callbackHandler;
	}

	//	https://consumer.com/negotiations/offers	POST	ContractOfferMessage
	// returns 201 with body ContractNegotiation - OFFERED
	@PostMapping("/negotiations/offers")
	public ResponseEntity<JsonNode> handleNegotiationOffers(@RequestBody JsonNode contractOfferMessageJsonNode) 
			throws InterruptedException, ExecutionException { 
		ContractOfferMessage contractOfferMessage = Serializer.deserializeProtocol(contractOfferMessageJsonNode, 
				ContractOfferMessage.class);
		
		String callbackAddress = contractOfferMessage.getCallbackAddress();
		CompletableFuture<JsonNode> responseNode = contractNegotiationConsumerService.processContractOffer(contractOfferMessage);

//		callbackAddress = callbackAddress.endsWith("/") ? callbackAddress : callbackAddress + "/";
//		String finalCallback = callbackAddress + ContactNegotiationCallback.getProviderNegotiationOfferCallback(callbackAddress);
		//TODO assumption - using callbackAddress from request as-is
		callbackHandler.handleCallbackResponse(callbackAddress, responseNode);
		// send OK 200
		log.info("Sending response OK in callback case");
		return ResponseEntity.ok().build();
	}
	
	// https://consumer.com/:callback/negotiations/:consumerPid/offers	POST	ContractOfferMessage
	// process message - if OK return 200; The response body is not specified and clients are not required to process it.
	@PostMapping("/consumer/negotiations/{consumerPid}/offers")
	public ResponseEntity<JsonNode> handleNegotiationOfferConsumerPid(@PathVariable String consumerPid,
			 @RequestBody JsonNode contractOfferMessageJsonNode) throws InterruptedException, ExecutionException { 
		ContractOfferMessage contractOfferMessage = Serializer.deserializeProtocol(contractOfferMessageJsonNode, 
				ContractOfferMessage.class);

		String callbackAddress = contractOfferMessage.getCallbackAddress();
		CompletableFuture<JsonNode> responseNode = 
				contractNegotiationConsumerService.handleNegotiationOfferConsumer(consumerPid, contractOfferMessage);

//		callbackAddress = callbackAddress.endsWith("/") ? callbackAddress : callbackAddress + "/";
//		String finalCallback = callbackAddress + ContactNegotiationCallback.getNegotiationOfferConsumer(callbackAddress);
		//TODO assumption - using callbackAddress from request as-is
		callbackHandler.handleCallbackResponse(callbackAddress, responseNode);
		log.info("Sending response OK in callback case");
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
	}
	
	// https://consumer.com/:callback/negotiations/:consumerPid/agreement	POST	ContractAgreementMessage
	// after successful processing - 200 ok; body not specified
	@PostMapping("/consumer/negotiations/{consumerPid}/agreement")
	public ResponseEntity<JsonNode> handleAgreement(@PathVariable String consumerPid,
			@RequestBody JsonNode contractAgreementMessageJsonNode) throws InterruptedException, ExecutionException { 
		
		ContractAgreementMessage contractAgreementMessage = Serializer.deserializeProtocol(contractAgreementMessageJsonNode, 
				ContractAgreementMessage.class);
		
		String callbackAddress = contractAgreementMessage.getCallbackAddress();
		CompletableFuture<JsonNode> responseNode = 
				contractNegotiationConsumerService.handleAgreement(consumerPid, contractAgreementMessage);

//		callbackAddress = callbackAddress.endsWith("/") ? callbackAddress : callbackAddress + "/";
//		String finalCallback = callbackAddress + ContactNegotiationCallback.getProviderHandleAgreementCallback(callbackAddress);
		//TODO assumption - using callbackAddress from request as-is
		callbackHandler.handleCallbackResponse(callbackAddress, responseNode);
		log.info("Sending response OK in callback case");
		return ResponseEntity.ok().build();
	}
	
	// https://consumer.com/:callback/negotiations/:consumerPid/events	POST	ContractNegotiationEventMessage
	// No callbackAddress
	@PostMapping("/consumer/negotiations/{consumerPid}/events")
	public ResponseEntity<JsonNode> handleEventsResponse(@PathVariable String consumerPid,
			@RequestBody JsonNode contractNegotiationEventMessageJsonNode) throws InterruptedException, ExecutionException {
		
		ContractNegotiationEventMessage contractNegotiationEventMessage = 
				Serializer.deserializeProtocol(contractNegotiationEventMessageJsonNode, ContractNegotiationEventMessage.class);

		CompletableFuture<JsonNode> responseNode = 
				contractNegotiationConsumerService.handleEventsResponse(consumerPid, contractNegotiationEventMessage);
		
		// ACK or ERROR
		//If the CN's state is successfully transitioned, the Consumer must return HTTP code 200 (OK). 
		// The response body is not specified and clients are not required to process it.
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
				.body(responseNode.get());
	}

	// https://consumer.com/:callback/negotiations/:consumerPid/termination POST	ContractNegotiationTerminationMessage
	// No callbackAddress
	@PostMapping("/consumer/negotiations/{consumerPid}/termination")
	public ResponseEntity<JsonNode> handleTerminationResponse(@PathVariable String consumerPid, 
			@RequestBody JsonNode contractNegotiationTerminationMessageJsonNode) throws InterruptedException, ExecutionException { 
		
		ContractNegotiationTerminationMessage contractNegotiationTerminationMessage = 
				Serializer.deserializeProtocol(contractNegotiationTerminationMessageJsonNode, ContractNegotiationTerminationMessage.class);
		
		CompletableFuture<JsonNode> responseNode = 
				contractNegotiationConsumerService.handleTerminationResponse(consumerPid, contractNegotiationTerminationMessage);
	
		// ACK or ERROR
		// If the CN's state is successfully transitioned, the Consumer must return HTTP code 200 (OK). 
		// The response body is not specified and clients are not required to process it.
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
				.body(responseNode.get());
	}
}
