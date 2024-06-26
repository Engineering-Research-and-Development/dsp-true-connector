package it.eng.negotiation.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.ProviderPidNotBlankException;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.model.Reason;
import it.eng.negotiation.serializer.Serializer;
import it.eng.negotiation.service.ContractNegotiationProviderService;
import it.eng.tools.model.DSpaceConstants;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
public class ProviderContractNegotiationControllerTest {

	@Mock
	private ContractNegotiationProviderService contractNegotiationService;
	@Mock
	private ServletRequestAttributes attrs;
	@Mock
	private HttpServletRequest request;
	
	@InjectMocks
	private ProviderContractNegotiationController controller;
	
	@BeforeEach
	public void before() {
	    RequestContextHolder.setRequestAttributes(attrs);
	}
	
	private ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.state(ContractNegotiationState.REQUESTED)
			.build();
			
	@Test
	public void getNegotiationByProviderPid_success() throws InterruptedException, ExecutionException {
		when(contractNegotiationService.getNegotiationByProviderPid(ModelUtil.PROVIDER_PID))
			.thenReturn(contractNegotiation);
		ResponseEntity<JsonNode> response = controller.getNegotiationByProviderPid(ModelUtil.PROVIDER_PID);
		assertNotNull(response, "Response is not null");
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(response.getBody().get(DSpaceConstants.TYPE).asText(), DSpaceConstants.DSPACE + ContractNegotiation.class.getSimpleName());
		assertEquals(response.getBody().get(DSpaceConstants.CONTEXT).asText(), DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE);
	}
	
	@Test
	public void getNegotiationByProviderPid_failed() throws InterruptedException, ExecutionException {
		when(contractNegotiationService.getNegotiationByProviderPid(ModelUtil.PROVIDER_PID))
			.thenThrow(ContractNegotiationNotFoundException.class);
		
		assertThrows(ContractNegotiationNotFoundException.class, () -> controller.getNegotiationByProviderPid(ModelUtil.PROVIDER_PID));
	}
	
	@Test
	public void createNegotiation_success() throws InterruptedException, ExecutionException {
	   when(attrs.getRequest()).thenReturn(request);
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .state(ContractNegotiationState.REQUESTED)
                .consumerPid(ModelUtil.CONSUMER_PID)
                .providerPid(ModelUtil.PROVIDER_PID)
                .build();
		when(contractNegotiationService.startContractNegotiation(any(ContractRequestMessage.class)))
			.thenReturn(cn);
		
		ResponseEntity<JsonNode> response = controller.createNegotiation(Serializer.serializeProtocolJsonNode(ModelUtil.CONTRACT_REQUEST_MESSAGE));
		
		assertNotNull(response, "Response is not null");
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertEquals(response.getBody().get(DSpaceConstants.TYPE).asText(), DSpaceConstants.DSPACE + ContractNegotiation.class.getSimpleName());
		assertEquals(response.getBody().get(DSpaceConstants.CONTEXT).asText(), DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE);
	}
	
	@Test
	public void createNegotiation_failed() throws InterruptedException, ExecutionException {
		when(contractNegotiationService.startContractNegotiation(any(ContractRequestMessage.class)))
		.thenThrow(ProviderPidNotBlankException.class);
		
		assertThrows(ProviderPidNotBlankException.class, () -> controller.createNegotiation(Serializer.serializeProtocolJsonNode(ModelUtil.CONTRACT_REQUEST_MESSAGE)));
	}
	
	@Test
	public void handleConsumerMakesOffer_success() {
		ResponseEntity<JsonNode> response = controller.handleConsumerMakesOffer(ModelUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(ModelUtil.CONTRACT_REQUEST_MESSAGE));
		assertNotNull(response, "Response is not null");
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
	}
	
	@Test
	public void handleNegotiationEventMessage_success() {
		ContractNegotiationEventMessage cnem = ContractNegotiationEventMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(ModelUtil.PROVIDER_PID)
				.eventType(ContractNegotiationEventType.ACCEPTED)
				.build();
		ResponseEntity<JsonNode> response = controller.handleNegotiationEventMessage(ModelUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(cnem));
		assertNotNull(response, "Response is not null");
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
	}
	
	@Test
	public void handleVerifyAgreement_success() {
		ContractAgreementVerificationMessage cavm = ContractAgreementVerificationMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(ModelUtil.PROVIDER_PID)
				.build();
		ResponseEntity<JsonNode> response = controller.handleVerifyAgreement(ModelUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(cavm));
		assertNotNull(response, "Response is not null");
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	public void handleVerifyAgreement_failed() {
		doThrow(ContractNegotiationNotFoundException.class)
		.when(contractNegotiationService).verifyNegotiation(any(ContractAgreementVerificationMessage.class));
		
		assertThrows(ContractNegotiationNotFoundException.class, () -> controller.handleVerifyAgreement(ModelUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(ModelUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE)));
	}
	
	@Test
	public void handleTerminationMessage_success() {
		ContractNegotiationTerminationMessage cntm = ContractNegotiationTerminationMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(ModelUtil.PROVIDER_PID)
				.code("1")
				.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("test").build()))
				.build();
		ResponseEntity<JsonNode> response = controller.handleTerminationMessage(ModelUtil.PROVIDER_PID, Serializer.serializeProtocolJsonNode(cntm));
		assertNotNull(response, "Response is not null");
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
	}
	
}
