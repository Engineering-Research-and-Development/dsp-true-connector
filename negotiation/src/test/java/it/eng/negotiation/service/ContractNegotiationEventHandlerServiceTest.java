package it.eng.negotiation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponse;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationEventHandlerServiceTest {

	@Mock
	private ContractNegotiationRepository repository;
	@Mock
	private ContractNegotiationProperties properties;
	@Mock
	private OkHttpRestClient okHttpRestClient;
	@Mock
	private GenericApiResponse<String> apiResponse;
	
	@InjectMocks
	private ContractNegotiationEventHandlerService handlerService;
	
	@Test
	@DisplayName("Handle contract negotiation offer response success")
	public void handleContractNegotiationOfferResponse_accepted_success() {
		ContractNegotiationOfferResponse offerResponse = new ContractNegotiationOfferResponse(ModelUtil.CONSUMER_PID, 
				ModelUtil.PROVIDER_PID, true, Serializer.serializePlainJsonNode(ModelUtil.OFFER));
		
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		// TODO temporary until figure out how to get assignee and assigner
		when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		
		handlerService.handleContractNegotiationOfferResponse(offerResponse);
		
		verify(repository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Handle contract negotiation offer declined")
	public void handleContractNegotiationOfferResponse_declined() {
		ContractNegotiationOfferResponse offerResponse = new ContractNegotiationOfferResponse(ModelUtil.CONSUMER_PID, 
				ModelUtil.PROVIDER_PID, false, Serializer.serializeProtocolJsonNode(ModelUtil.OFFER));
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class)))
			.thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION));

		handlerService.handleContractNegotiationOfferResponse(offerResponse);
		
		verify(repository, times(0)).save(any(ContractNegotiation.class));
	}

	@Test
	@DisplayName("Handle agreement verification message success")
	public void contractAgreementVerificationMessage_success() {
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.getHttpStatus()).thenReturn(200);

		handlerService.contractAgreementVerificationMessage(ModelUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE);
		
		verify(repository).save(any(ContractNegotiation.class));
	}
	
}