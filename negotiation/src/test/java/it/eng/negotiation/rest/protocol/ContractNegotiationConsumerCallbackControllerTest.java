package it.eng.negotiation.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.service.CallbackHandler;
import it.eng.negotiation.service.ContractNegotiationConsumerService;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationConsumerCallbackControllerTest {

	private ContractNegotiationConsumerCallbackController controller;
	
	@Mock
	private ContractNegotiationConsumerService contractNegotiationConsumerService;
	@Mock
	private CallbackHandler callbackHandler;
	
	ObjectMapper mapper = new ObjectMapper(); 
	
	@BeforeEach
	public void setup() {
		controller = new ContractNegotiationConsumerCallbackController(contractNegotiationConsumerService, callbackHandler);
	}
	
	@Test
	public void handleNegotiationOffers() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
		String json = Serializer.serializeProtocol(createContractOfferMessage());
		JsonNode jsonNode = mapper.readTree(json);
		when(contractNegotiationConsumerService.processContractOffer(any(ContractOfferMessage.class)))
			.thenReturn(CompletableFuture.completedFuture(null));
		ResponseEntity<JsonNode> response = controller.handleNegotiationOffers(jsonNode);
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		
		verify(callbackHandler).handleCallbackResponse(any(String.class), any(Future.class));
	}
	
	@Test
	public void handleNegotiationOffers_error() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
		String json = Serializer.serializeProtocol(createContractOfferMessage());
		JsonNode jsonNode = mapper.readTree(json);
		when(contractNegotiationConsumerService.processContractOffer(any(ContractOfferMessage.class)))
			.thenReturn(CompletableFuture.failedFuture(new RuntimeException("Simulated Exception")));
		
		ResponseEntity<JsonNode> response = controller.handleNegotiationOffers(jsonNode);
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		
		verify(callbackHandler).handleCallbackResponse(any(String.class), any(Future.class));
	}
	
	@Test
	public void handleNegotiationOfferConsumerPid() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
		String json = Serializer.serializeProtocol(createContractOfferMessage());
		JsonNode jsonNode = mapper.readTree(json);
		when(contractNegotiationConsumerService.handleNegotiationOfferConsumer(any(String.class), any(ContractOfferMessage.class)))
			.thenReturn(CompletableFuture.completedFuture(null));
		
		ResponseEntity<JsonNode> response = controller.handleNegotiationOfferConsumerPid(ModelUtil.CONSUMER_PID, jsonNode);
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		
		verify(callbackHandler).handleCallbackResponse(any(String.class), any(Future.class));
	}
	
	@Test
	public void handleAgreement() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
		String json = Serializer.serializeProtocol(contractAgreementMessage());
		JsonNode jsonNode = mapper.readTree(json);
		when(contractNegotiationConsumerService.handleAgreement(any(String.class), any(ContractAgreementMessage.class)))
			.thenReturn(CompletableFuture.completedFuture(null));
		
		ResponseEntity<JsonNode> response = controller.handleAgreement(ModelUtil.CONSUMER_PID, jsonNode);
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		
		verify(callbackHandler).handleCallbackResponse(any(String.class), any(Future.class));
	}
	
	@Test
	public void handleEventsResponse() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
		String json = Serializer.serializeProtocol(contractNegotiationEventMessage());
		JsonNode jsonNode = mapper.readTree(json);
		when(contractNegotiationConsumerService.handleEventsResponse(any(String.class), any(ContractNegotiationEventMessage.class)))
			.thenReturn(CompletableFuture.completedFuture(null));
		
		ResponseEntity<JsonNode> response = controller.handleEventsResponse(ModelUtil.CONSUMER_PID, jsonNode);
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
	}
	
	@Test
	public void handleTerminationResponse() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
		String json = Serializer.serializeProtocol(contractNegotiationTerminationMessage());
		JsonNode jsonNode = mapper.readTree(json);
		when(contractNegotiationConsumerService.handleTerminationResponse(any(String.class), any(ContractNegotiationTerminationMessage.class)))
			.thenReturn(CompletableFuture.completedFuture(null));
		
		ResponseEntity<JsonNode> response = controller.handleTerminationResponse(ModelUtil.CONSUMER_PID, jsonNode);
		assertNotNull(response);
		assertTrue(response.getStatusCode().is2xxSuccessful());
	}
	
	private ContractAgreementMessage contractAgreementMessage() {
		return ContractAgreementMessage.Builder.newInstance()
				.providerPid(ModelUtil.PROVIDER_PID)
				.consumerPid(ModelUtil.CONSUMER_PID)
				.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
				.agreement(ModelUtil.AGREEMENT)
				.build();
	}
	
	private ContractNegotiationEventMessage contractNegotiationEventMessage() {
		return ContractNegotiationEventMessage.Builder.newInstance()
				.providerPid(ModelUtil.PROVIDER_PID)
				.consumerPid(ModelUtil.CONSUMER_PID)
				.eventType(ContractNegotiationEventType.FINALIZED)
				.build();
	}
	
	private ContractNegotiationTerminationMessage contractNegotiationTerminationMessage() {
		return ContractNegotiationTerminationMessage.Builder.newInstance()
				.providerPid(ModelUtil.PROVIDER_PID)
				.consumerPid(ModelUtil.CONSUMER_PID)
				.build();
	}
	
	private ContractOfferMessage createContractOfferMessage() {
		return ContractOfferMessage.Builder.newInstance()
//				.consumerPid(ModelUtil.CONSUMER_PID) // optional; If the message includes a consumerPid property, the request will be associated with an existing CN.
				.providerPid(ModelUtil.PROVIDER_PID) 
				.offer(ModelUtil.OFFER)
				.callbackAddress(ModelUtil.CALLBACK_ADDRESS)   // mandatory???
				.build();
	}
}
