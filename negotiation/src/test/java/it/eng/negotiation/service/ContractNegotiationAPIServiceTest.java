package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationAPIServiceTest {

	@Mock
	private OkHttpRestClient okHttpRestClient;
	@Mock
	private ContractNegotiationRepository contractNegotiationRepository;
	@Mock
	private OfferRepository offerRepository;
	@Mock
	private AgreementRepository agreementRepository;
	@Mock
	private ContractNegotiationProperties properties;
	@Mock
	private GenericApiResponse<String> apiResponse;
	
	@Captor
	private ArgumentCaptor<ContractNegotiation> argumentCaptor;

	@InjectMocks
	private ContractNegotiationAPIService service;
	
	@Test
	@DisplayName("Start contract negotiation success")
	public void startNegotiation_success() {
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.getData()).thenReturn(Serializer.serializeProtocol(ModelUtil.CONTRACT_NEGOTIATION));
		when(apiResponse.getHttpStatus()).thenReturn(201);
		when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		
		service.startNegotiation(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER));
		
		verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Start contract negotiation failed")
	public void startNegotiation_failed() {
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.getHttpStatus()).thenReturn(400);
		when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		
		assertThrows(ContractNegotiationAPIException.class, ()-> service.startNegotiation(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER)));
		
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Process posted offer - success")
	public void postContractOffer_success() {
		when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
			.thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(apiResponse.getData()).thenReturn(Serializer.serializeProtocol(ModelUtil.CONTRACT_NEGOTIATION_OFFERED));
		// plain jsonNode
		service.postContractOffer(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER));
		
		verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Process posted offer - error")
	public void postContractOffer_error() {
		when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
			.thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		
		assertThrows(ContractNegotiationAPIException.class, ()->
			service.postContractOffer(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER)));
	}
	
	@Test
	@DisplayName("Send agreement success")
	public void sendAgreement_success() {
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.getHttpStatus()).thenReturn(200);
		when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION));
		
		service.sendAgreement(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID, Serializer.serializePlainJsonNode(ModelUtil.AGREEMENT));
		
		verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
		verify(agreementRepository).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Send agreement failed - negotiation not found")
	public void sendAgreement_failedNegotiationNotFound() {
		assertThrows(ContractNegotiationAPIException.class, ()-> service.sendAgreement(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID, Serializer.serializePlainJsonNode(ModelUtil.AGREEMENT)));
		
		verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Send agreement failed - bad request")
	public void sendAgreement_failedBadRequest() {
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.getHttpStatus()).thenReturn(400);
		when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION));
		
		assertThrows(ContractNegotiationAPIException.class, ()-> service.sendAgreement(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID, Serializer.serializePlainJsonNode(ModelUtil.AGREEMENT)));
	
		verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
}
