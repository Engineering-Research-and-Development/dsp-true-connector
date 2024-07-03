package it.eng.negotiation.rest.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
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
		doNothing().when(handlerService).handleContractNegotiationApproved(contractNegotaitionId);
		
		ResponseEntity<JsonNode> response =  controller.handleContractNegotationApproved(contractNegotaitionId);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Provider terminates negotation")
	public void providerTerminatesCN() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		doNothing().when(handlerService).handleContractNegotiationTerminated(contractNegotaitionId);

		ResponseEntity<JsonNode> response =  controller.handleContractNegotationTerminated(contractNegotaitionId);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
}
