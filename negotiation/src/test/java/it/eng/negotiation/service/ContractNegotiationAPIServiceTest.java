package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

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
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;

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
	@Mock
    private CredentialUtils credentialUtils;
	
	@Captor
	private ArgumentCaptor<ContractNegotiation> argumentCaptor;

	@InjectMocks
	private ContractNegotiationAPIService service;
	
	@Test
	@DisplayName("Start contract negotiation success")
	public void startNegotiation_success() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.getData()).thenReturn(Serializer.serializeProtocol(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));
		when(apiResponse.isSuccess()).thenReturn(true);
		when(properties.consumerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		
		service.startNegotiation(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER));
		
		verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
		verify(offerRepository).save(any(Offer.class));
	}
	
	@Test
	@DisplayName("Start contract negotiation failed")
	public void startNegotiation_failed() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(properties.consumerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		
		assertThrows(ContractNegotiationAPIException.class, ()-> service.startNegotiation(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER)));
		
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Start contract negotiation json exception")
	public void startNegotiation_jsonException() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.getData()).thenReturn("not a JSON");
		when(apiResponse.isSuccess()).thenReturn(true);
		when(properties.consumerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		
		assertThrows(ContractNegotiationAPIException.class, ()-> service.startNegotiation(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER)));
		
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Process posted offer - success")
	public void postContractOffer_success() {
		when(properties.providerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
			.thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(apiResponse.getData()).thenReturn(Serializer.serializeProtocol(ModelUtil.CONTRACT_NEGOTIATION_OFFERED));
		// plain jsonNode
		service.sendContractOffer(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER));
		
		verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Process posted offer - error")
	public void postContractOffer_error() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(properties.providerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
			.thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		
		assertThrows(ContractNegotiationAPIException.class, ()->
			service.sendContractOffer(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER)));
	}
	
	@Test
	@DisplayName("Send agreement success - accepted state")
	public void sendAgreement_success_acceptedState() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(properties.providerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));
		
		service.sendAgreement(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID, Serializer.serializePlainJsonNode(ModelUtil.AGREEMENT));
		
		verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
		verify(agreementRepository).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Send agreement success - requested state")
	public void sendAgreement_success_requestedState() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(properties.providerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		
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
	@DisplayName("Send agreement failed - wrong negotiation state")
	public void sendAgreement_wrongNegotiationState() {
		
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(ModelUtil.PROVIDER_PID, ModelUtil.CONSUMER_PID)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_OFFERED));

		assertThrows(ContractNegotiationAPIException.class, () -> service.sendAgreement(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID, Serializer.serializePlainJsonNode(ModelUtil.AGREEMENT)));
		
		verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(ModelUtil.PROVIDER_PID, ModelUtil.CONSUMER_PID);
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Send agreement failed - bad request")
	public void sendAgreement_failedBadRequest() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(properties.providerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));
		
		assertThrows(ContractNegotiationAPIException.class, ()-> service.sendAgreement(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID, Serializer.serializePlainJsonNode(ModelUtil.AGREEMENT)));
	
		verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Finalize negotiation success")
	public void finalizeNegotiation_success_requestedState() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_VERIFIED));
		
		service.finalizeNegotiation(ModelUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE);
		
		verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Finalize negotiation failed - negotiation not found")
	public void finalizeNegotiation_failedNegotiationNotFound() {
		assertThrows(ContractNegotiationAPIException.class, ()-> service.finalizeNegotiation(ModelUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE));
		
		verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Finalize negotiation failed - wrong negotiation state")
	public void finalizeNegotiation_wrongNegotiationState() {
		
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(ModelUtil.PROVIDER_PID, ModelUtil.CONSUMER_PID)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_OFFERED));

		assertThrows(ContractNegotiationAPIException.class, () -> service.finalizeNegotiation(ModelUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE));
		
		verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(ModelUtil.PROVIDER_PID, ModelUtil.CONSUMER_PID);
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Finalize negotiation failed - bad request")
	public void finalizeNegotiation_failedBadRequest() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString()))
			.thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_VERIFIED));
		
		assertThrows(ContractNegotiationAPIException.class, ()-> service.finalizeNegotiation(ModelUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE));
	
		verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Find all contract negotiations")
	public void findAll() {
		when(contractNegotiationRepository.findAll())
				.thenReturn(Arrays.asList(
						ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED,
						ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		Collection<JsonNode> response = service.findContractNegotiations(null);
		assertNotNull(response);
		assertEquals(2, response.size());
	}
	
	@Test
	@DisplayName("Find contract negotiations by state")
	public void findContractNegotiationByState() {
		when(contractNegotiationRepository.findByState(ContractNegotiationState.ACCEPTED.name()))
				.thenReturn(Arrays.asList(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));
		Collection<JsonNode> response = service.findContractNegotiations(ContractNegotiationState.ACCEPTED.name());
		assertNotNull(response);
		assertEquals(1, response.size());
	}
	
	@Test
	@DisplayName("Consumer accepts contract negotiation offered by provider")
	public void handleContractNegotiationAccepted() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(contractNegotiationRepository.findById(contractNegotaitionId))
			.thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_OFFERED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);

		ContractNegotiation response = service.handleContractNegotiationAccepted(contractNegotaitionId);
		assertNotNull(response);
		assertEquals(ContractNegotiationState.ACCEPTED, response.getState());
	}
	
	@Test
	@DisplayName("Consumer accepts contract negotiation offered by provider")
	public void handleContractNegotiationAccepted_invalid_state() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(contractNegotiationRepository.findById(contractNegotaitionId))
			.thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));

		assertThrows(ContractNegotiationAPIException.class, 
				()-> service.handleContractNegotiationAccepted(contractNegotaitionId));
	}
	
	@Test
	@DisplayName("Consumer accepts contract negotiation offered by provider - error api")
	public void handleContractNegotiationAccepted_error_api() {
		String contractNegotaitionId = UUID.randomUUID().toString();
		when(contractNegotiationRepository.findById(contractNegotaitionId))
			.thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_OFFERED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);

		assertThrows(ContractNegotiationAPIException.class, 
				()-> service.handleContractNegotiationAccepted(contractNegotaitionId));	}
}
