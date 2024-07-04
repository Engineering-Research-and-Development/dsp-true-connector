package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import it.eng.negotiation.exception.ContractNegotiationInvalidStateException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.OfferNotFoundException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationEventHandlerServiceTest {

	@Mock
	private ContractNegotiationRepository repository;
	@Mock
	private OfferRepository offerRepository;
	@Mock
	private AgreementRepository agreementRepository;
	@Mock
	private ContractNegotiationProperties properties;
	@Mock
	private OkHttpRestClient okHttpRestClient;
	@Mock
	private GenericApiResponse<String> apiResponse;
	@Mock
	private CredentialUtils credentialUtils;
	
	@InjectMocks
	private ContractNegotiationEventHandlerService handlerService;
	
	@Captor
	private ArgumentCaptor<ContractNegotiation> argCaptorContractNegotiation;
	@Captor
	private ArgumentCaptor<Agreement> argCaptorAgreement;
	
	@Test
	@DisplayName("Handle contract negotiation offer response success")
	public void handleContractNegotiationOfferResponse_accepted_success() {
		ContractNegotiationOfferResponseEvent offerResponse = new ContractNegotiationOfferResponseEvent(ModelUtil.CONSUMER_PID, 
				ModelUtil.PROVIDER_PID, true, Serializer.serializePlainJsonNode(ModelUtil.OFFER));
		when(properties.getAssignee()).thenReturn(ModelUtil.ASSIGNEE);
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		// TODO temporary until figure out how to get assignee and assigner
		when(properties.providerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		
		handlerService.handleContractNegotiationOfferResponse(offerResponse);
		
		verify(repository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Handle contract negotiation offer declined")
	public void handleContractNegotiationOfferResponse_declined() {
		ContractNegotiationOfferResponseEvent offerResponse = new ContractNegotiationOfferResponseEvent(ModelUtil.CONSUMER_PID, 
				ModelUtil.PROVIDER_PID, false, Serializer.serializeProtocolJsonNode(ModelUtil.OFFER));
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class)))
			.thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));

		handlerService.handleContractNegotiationOfferResponse(offerResponse);
		
		verify(repository, times(0)).save(any(ContractNegotiation.class));
	}

	@Test
	@DisplayName("Handle agreement verification message success")
	public void contractAgreementVerificationMessage_success() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_AGREED));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);

		handlerService.verifyNegotiation(ModelUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE);
		
		verify(repository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Handle agreement verification message - contract negotiation not found")
	public void contractAgreementVerificationMessage_contractNegotiationNotFound() {
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.empty());

		assertThrows(ContractNegotiationAPIException.class, () -> handlerService.verifyNegotiation(ModelUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE));
	}
	
	@Test
	@DisplayName("Handle agreement verification message - invalid state")
	public void contractAgreementVerificationMessage_invalidState() {
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));

		assertThrows(ContractNegotiationAPIException.class, () -> handlerService.verifyNegotiation(ModelUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE));
	}
	
	@Test
	@DisplayName("Handle agreement verification message - bad request")
	public void contractAgreementVerificationMessage_badRequest() {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_AGREED));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		
		assertThrows(ContractNegotiationAPIException.class, 
				() -> handlerService.verifyNegotiation(ModelUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE));
	}
	
	@Test
	@DisplayName("Provider accepts contract negotiation")
	public void handleCNApproved() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(repository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(properties.providerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(properties.getAssignee()).thenReturn(ModelUtil.ASSIGNEE);
		when(offerRepository.findById(any(String.class))).thenReturn(Optional.of(ModelUtil.OFFER));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		
		handlerService.handleContractNegotiationApproved(contractNegotaitionId);
		
		verify(repository).save(argCaptorContractNegotiation.capture());
		assertEquals(ContractNegotiationState.AGREED, argCaptorContractNegotiation.getValue().getState());
	}
	
	@Test
	@DisplayName("Provider accepts contract negotiation - invalid initial state")
	public void handleCNApproved_invalid_state() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(repository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_AGREED));
		
		assertThrows(ContractNegotiationInvalidStateException.class, 
				() -> handlerService.handleContractNegotiationApproved(contractNegotaitionId));
	}
	
	@Test
	@DisplayName("Provider accepts contract negotiation - offer does not exists")
	public void handleCNApproved_no_offer() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(repository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		when(offerRepository.findById(any(String.class))).thenReturn(Optional.empty());
		
		assertThrows(OfferNotFoundException.class, 
				() -> handlerService.handleContractNegotiationApproved(contractNegotaitionId));
	}
	
	@Test
	@DisplayName("Provider accepts contract negotiation - error while contacting consumer")
	public void handleCNApproved_error_consumer() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(repository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(properties.providerCallbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(properties.getAssignee()).thenReturn(ModelUtil.ASSIGNEE);
		when(offerRepository.findById(any(String.class))).thenReturn(Optional.of(ModelUtil.OFFER));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		
		assertThrows(ContractNegotiationAPIException.class, 
				() -> handlerService.handleContractNegotiationApproved(contractNegotaitionId));
		
		verify(repository, times(0)).save(argCaptorContractNegotiation.capture());
		verify(agreementRepository, times(0)).save(argCaptorAgreement.capture());
	}
	
	@Test
	@DisplayName("Provider terminate contract negotiation")
	public void terminateNegotiation() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(repository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		
		handlerService.handleContractNegotiationTerminated(contractNegotaitionId);
		
		verify(repository).save(argCaptorContractNegotiation.capture());
		assertEquals(ContractNegotiationState.TERMINATED, argCaptorContractNegotiation.getValue().getState());
	}
	
	@Test
	@DisplayName("Provider terminate contract negotiation - contract negotiaton not found")
	public void terminateNegotiation_cn_not_found() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(repository.findById(contractNegotaitionId)).thenReturn(Optional.empty());

		assertThrows(ContractNegotiationNotFoundException.class,
				() -> handlerService.handleContractNegotiationTerminated(contractNegotaitionId));
		
		verify(repository, times(0)).save(argCaptorContractNegotiation.capture());
	}
	
	@Test
	@DisplayName("Provider terminate contract negotiation - consumer did not respond")
	public void terminateNegotiation_consumer_error() {
		String contractNegotaitionId = UUID.randomUUID().toString(); 
		when(repository.findById(contractNegotaitionId)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_REQUESTED));
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);

		assertThrows(ContractNegotiationAPIException.class,
				() -> handlerService.handleContractNegotiationTerminated(contractNegotaitionId));
		
		verify(repository, times(0)).save(argCaptorContractNegotiation.capture());
	}
}
