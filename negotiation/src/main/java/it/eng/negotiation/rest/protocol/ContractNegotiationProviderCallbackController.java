package it.eng.negotiation.rest.protocol;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.service.CallbackHandler;
import it.eng.negotiation.service.ContractNegotiationConsumerService;
import it.eng.negotiation.transformer.to.JsonToContractOfferMessageTransformer;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class ContractNegotiationProviderCallbackController {
	
	private ContractNegotiationConsumerService contractNegotiationConsumerService;
	private CallbackHandler callbackHandler;
	
	public ContractNegotiationProviderCallbackController(ContractNegotiationConsumerService contractNegotiationConsumerService,
			CallbackHandler callbackHandler) {
		super();
		this.contractNegotiationConsumerService = contractNegotiationConsumerService;
		this.callbackHandler = callbackHandler;
	}

	//	https://consumer.com/negotiations/offers	POST	Section 3.2.1
	@PostMapping("/negotiations/offers")
	public ResponseEntity<JsonNode> handleNegotiationOffers(@PathVariable String providerPid, @RequestBody JsonNode contractOfferMessageJson) throws InterruptedException, ExecutionException { 
		JsonToContractOfferMessageTransformer transforer = new JsonToContractOfferMessageTransformer();
		ContractOfferMessage contractOfferMessage = transforer.transform(contractOfferMessageJson);
		
		String callbackAddress = contractOfferMessage.getCallbackAddress();
		CompletableFuture<JsonNode> responseNode = contractNegotiationConsumerService.getContractNegotiation(contractOfferMessage);

		if(ObjectUtils.isEmpty(callbackAddress)) {
			log.info("Sending response regular");
			URI location = URI.create("/negotiations/" + contractOfferMessage.getClass().getSimpleName());
			return ResponseEntity.created(location).contentType(MediaType.APPLICATION_JSON).body(responseNode.get());
		} else {
			// final callback = callback + extension base on message type
			callbackAddress = callbackAddress.endsWith("/") ? callbackAddress : callbackAddress + "/";
			String finalCallback = callbackAddress + ContactNegotiationCallback.getOffersCallback();
			callbackHandler.handleCallbackResponse(finalCallback, responseNode);
			// send OK 200
			log.info("Sending response OK in callback case");
			return ResponseEntity.ok().build();
		}
	}
	
	//	https://consumer.com/:callback/negotiations/:consumerPid/offers	POST	Section 3.3.1
	@PostMapping("/callback/negotiations/{consumerPid}/offers")
	public ResponseEntity<JsonNode> handleNegotiationOfferConsumerPid(@PathVariable String consumerPid) { 
		return ResponseEntity.ok().build();
	}
	
	// https://consumer.com/:callback/negotiations/:consumerPid/agreement	POST	Section 3.4.1
	@PostMapping("/callback/negotiations/{consumerPid}/agreement")
	public ResponseEntity<JsonNode> handleAgreement(@PathVariable String consumerPid) { 
		return ResponseEntity.ok().build();
	}
	
	// https://consumer.com/:callback/negotiations/:consumerPid/events	POST	Section 3.5.1
	@PostMapping("/callback/negotiations/{consumerPid}/events")
	public ResponseEntity<JsonNode> handleEventsResponse(@PathVariable String consumerPid) { 
		return ResponseEntity.ok().build();
	}

	// https://consumer.com/:callback/negotiations/:consumerPid/termination POST	Section 3.6.1
	@PostMapping("/callback/negotiations/{consumerPid}/termination")
	public ResponseEntity<JsonNode> handleTerminationResponse(@PathVariable String consumerPid) { 
		return ResponseEntity.ok().build();
	}
}
