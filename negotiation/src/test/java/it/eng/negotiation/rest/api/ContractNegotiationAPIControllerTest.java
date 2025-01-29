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
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationAPIService;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationAPIControllerTest {
	
	@Mock
	private ContractNegotiationAPIService apiService;

	@InjectMocks
	private ContractNegotiationAPIController controller;

	ObjectMapper mapper = new ObjectMapper();

	@Test
	@DisplayName("Find all contract negotiations")
	public void findAll() {
		when(apiService.findContractNegotiations(null, null, null, null, null))
				.thenReturn(Arrays.asList(
						NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED),
						NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED)));
		ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getContractNegotiations(null, null, null, null, null);
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Find contract negotiations by state")
	public void findContractNegotiationByState() {
		when(apiService.findContractNegotiations(null, ContractNegotiationState.ACCEPTED.name(), null, null, null))
				.thenReturn(Arrays.asList(NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED)));
		ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getContractNegotiations(null, ContractNegotiationState.ACCEPTED.name(), null, null, null);
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Find contract negotiations by id")
	public void findContractNegotiationById() {
		when(apiService.findContractNegotiations(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId(), null, null, null, null))
				.thenReturn(Arrays.asList(NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED)));
		ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getContractNegotiations(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId(), null, null, null, null);
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Find contract negotiations by pids")
	public void findContractNegotiationByPids() {
		when(apiService.findContractNegotiations(null, null, null, 
				NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getConsumerPid(), NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getProviderPid()))
			.thenReturn(Arrays.asList(NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED)));
		ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getContractNegotiations(null, null, null,
				NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getConsumerPid(), NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getProviderPid());
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Start contract negotiation success")
	public void startNegotiation_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
		map.put("offer", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));
				
		when(apiService.startNegotiation(any(String.class), any(JsonNode.class)))
			.thenReturn(NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.startNegotiation(mapper.convertValue(map, JsonNode.class));
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).startNegotiation(any(String.class), any(JsonNode.class));
	}
	
	@Test
	@DisplayName("Verify negotiation success")
	public void verifyNegotiation_success() {
		
		ResponseEntity<GenericApiResponse<Void>> response = controller.verifyContractNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
	}
	
	@Test
	@DisplayName("Verify negotiation failed")
	public void verifyNegotiation_failed() {
		
		doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
		.when(apiService).verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
		
		assertThrows(ContractNegotiationAPIException.class, () ->
		controller.verifyContractNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));
	}
	
	@Test
	@DisplayName("Provider posts offer - success")
	public void providerPostsOffer_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
		map.put("offer", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));
		
		when(apiService.sendContractOffer(any(String.class), any(JsonNode.class)))
			.thenReturn(NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.sendContractOffer(mapper.convertValue(map, JsonNode.class));
	
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
		map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
		map.put("offer", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));
		
		when(apiService.sendContractOffer(any(String.class), any(JsonNode.class)))
			.thenThrow(new ContractNegotiationAPIException("Something not correct - tests"));

		assertThrows(ContractNegotiationAPIException.class, () ->
			controller.sendContractOffer(mapper.convertValue(map, JsonNode.class)));
	}
	
	@Test
	@DisplayName("Send agreement success")
	public void sendAgreement_success() {
		Map<String, Object> map = new HashMap<>();
		map.put("consumerPid", NegotiationMockObjectUtil.CONSUMER_PID);
		map.put("providerPid", NegotiationMockObjectUtil.PROVIDER_PID);
		map.put("agreement", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.AGREEMENT));
				
		ResponseEntity<GenericApiResponse<Void>> response = controller.sendAgreement(mapper.convertValue(map, JsonNode.class));
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).sendAgreement(any(String.class), any(String.class), any(JsonNode.class));
	}
	
	@Test
	@DisplayName("Send agreement failed")
	public void sendAgreement_failed() {
		Map<String, Object> map = new HashMap<>();
		map.put("consumerPid", NegotiationMockObjectUtil.CONSUMER_PID);
		map.put("providerPid", NegotiationMockObjectUtil.PROVIDER_PID);
		map.put("agreement", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.AGREEMENT));
				
		doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
		.when(apiService).sendAgreement(any(String.class), any(String.class), any(JsonNode.class));
		
		assertThrows(ContractNegotiationAPIException.class, () ->
		controller.sendAgreement(mapper.convertValue(map, JsonNode.class)));
	}
	
	@Test
	@DisplayName("Finalize negotiation success")
	public void finalizeNegotiation_success() {
				
		ResponseEntity<GenericApiResponse<Void>> response = controller.finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(apiService).finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
	}
	
	@Test
	@DisplayName("Finalize negotiation failed")
	public void finalizeNegotiation_failed() {
				
		doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
		.when(apiService).finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
		
		assertThrows(ContractNegotiationAPIException.class, () ->
		controller.finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));
	}
	
	@Test
	@DisplayName("Provider approves negotation")
	public void providerAcceptsCN() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(apiService.approveContractNegotiation(contractNegotaitionId))
			.thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED);
		
		ResponseEntity<GenericApiResponse<JsonNode>> response =  controller.approveContractNegotiation(contractNegotaitionId);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Provider accepts negotation - service throws error")
	public void providerAcceptsCN_error() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(apiService.approveContractNegotiation(contractNegotaitionId))
			.thenThrow(ContractNegotiationNotFoundException.class);
		
		assertThrows(ContractNegotiationNotFoundException.class,
				() -> controller.approveContractNegotiation(contractNegotaitionId));
	}		
	
	@Test
	@DisplayName("Provider terminates negotation")
	public void providerTerminatesCN() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(apiService.handleContractNegotiationTerminated(contractNegotaitionId))
			.thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_TERMINATED);

		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.terminateContractNegotiation(contractNegotaitionId);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Provider terminates negotation - service error")
	public void providerTerminatesCN_error() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(apiService.handleContractNegotiationTerminated(contractNegotaitionId))
			.thenThrow(ContractNegotiationNotFoundException.class);

		assertThrows(ContractNegotiationNotFoundException.class,
				() -> controller.terminateContractNegotiation(contractNegotaitionId));
	}
	
	@Test
	@DisplayName("Consumer accepts negotiation offered by provider")
	public void handleContractNegotationAccepted() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(apiService.handleContractNegotiationAccepted(contractNegotaitionId)).thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED);
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.acceptContractNegotiation(contractNegotaitionId);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Consumer acepts negotiation offered by provider - error while processing")
	public void handleContractNegotationAccepted_service_error() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(apiService.handleContractNegotiationAccepted(contractNegotaitionId))
			.thenThrow(ContractNegotiationNotFoundException.class);
		
		assertThrows(ContractNegotiationNotFoundException.class,
					() -> controller.acceptContractNegotiation(contractNegotaitionId));
	}
}
