package it.eng.negotiation.rest.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.serializer.Serializer;
import it.eng.negotiation.service.ContractNegotiationAPIService;
import it.eng.negotiation.service.ContractNegotiationEventHandlerService;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationAPIControllerTest {
	
	@Mock
	private ContractNegotiationEventHandlerService handlerService;
	@Mock
	private ContractNegotiationAPIService apiService;

	@InjectMocks
	private ContractNegotiationAPIController controller;

	ObjectMapper mapper = new ObjectMapper();

	@Test
	@DisplayName("Find all contract negotiations")
	public void findAll() {
		when(apiService.findContractNegotiations(null))
				.thenReturn(Arrays.asList(
						Serializer.serializePlainJsonNode(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED),
						Serializer.serializePlainJsonNode(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED)));
		ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.findContractNegotations(null);
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Find contract negotiations by state")
	public void findContractNegotiationByState() {
		when(apiService.findContractNegotiations(ContractNegotiationState.ACCEPTED.name()))
				.thenReturn(Arrays.asList(Serializer.serializePlainJsonNode(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED)));
		ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.findContractNegotations(ContractNegotiationState.ACCEPTED.name());
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Start contract negotiation success")
	public void startNegotiation_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("Forward-To", ModelUtil.FORWARD_TO);
		map.put("offer", Serializer.serializeProtocolJsonNode(ModelUtil.OFFER));
				
		when(apiService.startNegotiation(any(String.class), any(JsonNode.class)))
			.thenReturn(Serializer.serializeProtocolJsonNode(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.startNegotiation(mapper.convertValue(map, JsonNode.class));
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).startNegotiation(any(String.class), any(JsonNode.class));
	}
	
	@Test
	@DisplayName("Approve requested offer success")
	public void offerApproved_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("offer", Serializer.serializeProtocolJsonNode(ModelUtil.OFFER));
		map.put("consumerPid", ModelUtil.CONSUMER_PID);
		map.put("providerPid", ModelUtil.PROVIDER_PID);
		map.put("offerAccepted", true);
		
		ResponseEntity<JsonNode> response = controller.handleOfferApproved(mapper.convertValue(map, JsonNode.class));
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(handlerService).handleContractNegotiationOfferResponse(any(ContractNegotiationOfferResponseEvent.class));
	}
	
	@Test
	@DisplayName("Verify negotiation success")
	public void verifyNegotiation_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("consumerPid", ModelUtil.CONSUMER_PID);
		map.put("providerPid", ModelUtil.PROVIDER_PID);
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.verifyNegotiation(mapper.convertValue(map, JsonNode.class));
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(handlerService).verifyNegotiation(any(ContractAgreementVerificationMessage.class));
	}
	
	@Test
	@DisplayName("Verify negotiation failed")
	public void verifyNegotiation_failed() {
		Map<String, Object> map = new HashMap<>();
		map.put("consumerPid", ModelUtil.CONSUMER_PID);
		map.put("providerPid", ModelUtil.PROVIDER_PID);
		
		doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
		.when(handlerService).verifyNegotiation(any(ContractAgreementVerificationMessage.class));
		
		assertThrows(ContractNegotiationAPIException.class, () ->
		controller.verifyNegotiation(mapper.convertValue(map, JsonNode.class)));
	}
	
	@Test
	@DisplayName("Provider posts offer - success")
	public void providerPostsOffer_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("Forward-To", ModelUtil.FORWARD_TO);
		map.put("offer", Serializer.serializeProtocolJsonNode(ModelUtil.OFFER));
		
		when(apiService.sendContractOffer(any(String.class), any(JsonNode.class)))
			.thenReturn(Serializer.serializeProtocolJsonNode(ModelUtil.CONTRACT_NEGOTIATION_OFFERED));
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.sendOffer(mapper.convertValue(map, JsonNode.class));
	
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().isSuccess());
		assertEquals(response.getBody().getData().get(DSpaceConstants.TYPE).asText(), DSpaceConstants.DSPACE + ContractNegotiation.class.getSimpleName());
	}
	
	@Test
	@DisplayName("Provider posts offer - error")
	public void providerPostsOffer_error() {
		Map<String, Object> map = new HashMap<>();
		map.put("Forward-To", ModelUtil.FORWARD_TO);
		map.put("offer", Serializer.serializeProtocolJsonNode(ModelUtil.OFFER));
		
		when(apiService.sendContractOffer(any(String.class), any(JsonNode.class)))
			.thenThrow(new ContractNegotiationAPIException("Something not correct - tests"));

		assertThrows(ContractNegotiationAPIException.class, () ->
			controller.sendOffer(mapper.convertValue(map, JsonNode.class)));
	}
	
	@Test
	@DisplayName("Send agreement success")
	public void sendAgreement_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("consumerPid", ModelUtil.CONSUMER_PID);
		map.put("providerPid", ModelUtil.PROVIDER_PID);
		map.put("agreement", Serializer.serializeProtocolJsonNode(ModelUtil.AGREEMENT));
				
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.sendAgreement(mapper.convertValue(map, JsonNode.class));
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).sendAgreement(any(String.class), any(String.class), any(JsonNode.class));
	}
	
	@Test
	@DisplayName("Send agreement failed")
	public void sendAgreement_failed() {
		Map<String, Object> map = new HashMap<>();
		map.put("consumerPid", ModelUtil.CONSUMER_PID);
		map.put("providerPid", ModelUtil.PROVIDER_PID);
		map.put("agreement", Serializer.serializeProtocolJsonNode(ModelUtil.AGREEMENT));
				
		doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
		.when(apiService).sendAgreement(any(String.class), any(String.class), any(JsonNode.class));
		
		assertThrows(ContractNegotiationAPIException.class, () ->
		controller.sendAgreement(mapper.convertValue(map, JsonNode.class)));
	}
	
	@Test
	@DisplayName("Finalize negotiation success")
	public void finalizeNegotiation_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("consumerPid", ModelUtil.CONSUMER_PID);
		map.put("providerPid", ModelUtil.PROVIDER_PID);
				
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.finalizeNegotiation(mapper.convertValue(map, JsonNode.class));
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).finalizeNegotiation(any(ContractNegotiationEventMessage.class));
	}
	
	@Test
	@DisplayName("Finalize negotiation failed")
	public void finalizeNegotiation_failed() {
		Map<String, Object> map = new HashMap<>();
		map.put("consumerPid", ModelUtil.CONSUMER_PID);
		map.put("providerPid", ModelUtil.PROVIDER_PID);
				
		doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
		.when(apiService).finalizeNegotiation(any(ContractNegotiationEventMessage.class));
		
		assertThrows(ContractNegotiationAPIException.class, () ->
		controller.finalizeNegotiation(mapper.convertValue(map, JsonNode.class)));
	}
	
	@Test
	@DisplayName("Provider accepts negotation")
	public void providerAcceptsCN() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(handlerService.handleContractNegotiationApproved(contractNegotaitionId))
			.thenReturn(ModelUtil.CONTRACT_NEGOTIATION_AGREED);
		
		ResponseEntity<GenericApiResponse<JsonNode>> response =  controller.handleContractNegotationApproved(contractNegotaitionId);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Provider accepts negotation - service throws error")
	public void providerAcceptsCN_error() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(handlerService.handleContractNegotiationApproved(contractNegotaitionId))
			.thenThrow(ContractNegotiationNotFoundException.class);
		
		assertThrows(ContractNegotiationNotFoundException.class,
				() -> controller.handleContractNegotationApproved(contractNegotaitionId));
	}		
	
	@Test
	@DisplayName("Provider terminates negotation")
	public void providerTerminatesCN() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(handlerService.handleContractNegotiationTerminated(contractNegotaitionId))
			.thenReturn(ModelUtil.CONTRACT_NEGOTIATION_TERMINATED);

		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.handleContractNegotationTerminated(contractNegotaitionId);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Provider terminates negotation - service error")
	public void providerTerminatesCN_error() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(handlerService.handleContractNegotiationTerminated(contractNegotaitionId))
			.thenThrow(ContractNegotiationNotFoundException.class);

		assertThrows(ContractNegotiationNotFoundException.class,
				() -> controller.handleContractNegotationTerminated(contractNegotaitionId));
	}
	
	@Test
	@DisplayName("Consumer acepts negotiation offered by provider")
	public void handleContractNegotationAccepted() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(apiService.handleContractNegotiationAccepted(contractNegotaitionId)).thenReturn(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED);
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.handleContractNegotationAccepted(contractNegotaitionId);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Consumer acepts negotiation offered by provider - error while processing")
	public void handleContractNegotationAccepted_service_error() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(apiService.handleContractNegotiationAccepted(contractNegotaitionId))
			.thenThrow(ContractNegotiationNotFoundException.class);
		
		assertThrows(ContractNegotiationNotFoundException.class,
					() -> controller.handleContractNegotationAccepted(contractNegotaitionId));
	}
}
