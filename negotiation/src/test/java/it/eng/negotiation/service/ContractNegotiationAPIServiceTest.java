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
import it.eng.negotiation.exception.OfferNotFoundException;
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
	private ArgumentCaptor<ContractNegotiation> argCaptorContractNegotiation;
	@Captor
	private ArgumentCaptor<Agreement> argCaptorAgreement;

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
		
		service.finalizeNegotiation(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID);
		
		verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Finalize negotiation failed - negotiation not found")
	public void finalizeNegotiation_failedNegotiationNotFound() {
		assertThrows(ContractNegotiationAPIException.class, ()-> service.finalizeNegotiation(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID));
		
		verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Finalize negotiation failed - wrong negotiation state")
	public void finalizeNegotiation_wrongNegotiationState() {
		
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(ModelUtil.PROVIDER_PID, ModelUtil.CONSUMER_PID)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_OFFERED));

		assertThrows(ContractNegotiationAPIException.class, 
				() -> service.finalizeNegotiation(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID));
		
		verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(ModelUtil.PROVIDER_PID, ModelUtil.CONSUMER_PID);
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Finalize negotiation error - already finalized")
	public void finalizeNegotiation_error_finalized_state() {
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString()))
			.thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_FINALIZED));
		
		assertThrows(ContractNegotiationAPIException.class,
				() -> service.finalizeNegotiation(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID));
		
		verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());
	}
	
	@Test
	@DisplayName("Finalize negotiation failed - bad request")
	public void finalizeNegotiation_failedBadRequest() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString()))
			.thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_VERIFIED));
		
		assertThrows(ContractNegotiationAPIException.class, ()-> service.finalizeNegotiation(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID));
	
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
				()-> service.handleContractNegotiationAccepted(contractNegotaitionId));	
		}
	
	@Test
	@DisplayName("Provider accepts contract negotiation")
	public void handleCNApproved() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(properties.providerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(properties.getAssignee()).thenReturn(ModelUtil.ASSIGNEE);
		when(offerRepository.findById(any(String.class))).thenReturn(Optional.of(ModelUtil.OFFER));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		
		service.handleContractNegotiationAgreed(contractNegotaitionId);
		
		verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
		assertEquals(ContractNegotiationState.AGREED, argCaptorContractNegotiation.getValue().getState());
	}
	
	@Test
	@DisplayName("Provider accepts contract negotiation - invalid initial state")
	public void handleCNApproved_invalid_state() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_AGREED));
		
		assertThrows(ContractNegotiationAPIException.class, 
				() -> service.handleContractNegotiationAgreed(contractNegotaitionId));
	}
	
	@Test
	@DisplayName("Provider accepts contract negotiation - offer does not exists")
	public void handleCNApproved_no_offer() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		when(offerRepository.findById(any(String.class))).thenReturn(Optional.empty());
		
		assertThrows(OfferNotFoundException.class, 
				() -> service.handleContractNegotiationAgreed(contractNegotaitionId));
	}
	
	@Test
	@DisplayName("Provider accepts contract negotiation - error while contacting consumer")
	public void handleCNApproved_error_consumer() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(properties.providerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(properties.getAssignee()).thenReturn(ModelUtil.ASSIGNEE);
		when(offerRepository.findById(any(String.class))).thenReturn(Optional.of(ModelUtil.OFFER));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		
		assertThrows(ContractNegotiationAPIException.class, 
				() -> service.handleContractNegotiationAgreed(contractNegotaitionId));
		
		verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());
		verify(agreementRepository, times(0)).save(argCaptorAgreement.capture());
	}
	
	@Test
	@DisplayName("Handle agreement verification message success")
	public void contractAgreementVerificationMessage_success() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_AGREED));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);

		service.verifyNegotiation(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID);
		
		verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Handle agreement verification message - contract negotiation not found")
	public void contractAgreementVerificationMessage_contractNegotiationNotFound() {
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.empty());

		assertThrows(ContractNegotiationAPIException.class, () -> service.verifyNegotiation(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID));
	}
	
	@Test
	@DisplayName("Handle agreement verification message - invalid state")
	public void contractAgreementVerificationMessage_invalidState() {
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));

		assertThrows(ContractNegotiationAPIException.class, () -> service.verifyNegotiation(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID));
	}
	
	@Test
	@DisplayName("Handle agreement verification message - bad request")
	public void contractAgreementVerificationMessage_badRequest() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_AGREED));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		
		assertThrows(ContractNegotiationAPIException.class, 
				() -> service.verifyNegotiation(ModelUtil.CONSUMER_PID, ModelUtil.PROVIDER_PID));
	}
	
	@Test
	@DisplayName("Provider terminate contract negotiation")
	public void terminateNegotiation() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		
		service.handleContractNegotiationTerminated(contractNegotaitionId);
		
		verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
		assertEquals(ContractNegotiationState.TERMINATED, argCaptorContractNegotiation.getValue().getState());
	}
	
	@Test
	@DisplayName("Provider terminate contract negotiation - contract negotiaton not found")
	public void terminateNegotiation_cn_not_found() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.empty());

		assertThrows(ContractNegotiationAPIException.class,
				() -> service.handleContractNegotiationTerminated(contractNegotaitionId));
		
		verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());
	}
	
	@Test
	@DisplayName("Provider terminate contract negotiation - consumer did not respond")
	public void terminateNegotiation_consumer_error() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);

		assertThrows(ContractNegotiationAPIException.class,
				() -> service.handleContractNegotiationTerminated(contractNegotaitionId));
		
		verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());
	}

}
