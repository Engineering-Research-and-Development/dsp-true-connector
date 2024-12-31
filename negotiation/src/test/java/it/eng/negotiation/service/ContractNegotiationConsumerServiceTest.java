package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

import it.eng.negotiation.exception.ContractNegotiationInvalidEventTypeException;
import it.eng.negotiation.exception.ContractNegotiationInvalidStateException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.OfferNotFoundException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.service.policy.PolicyEnforcementService;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationConsumerServiceTest {

	@Mock
	private ContractNegotiationProperties properties;
	@Mock
	private ContractNegotiationPublisher publisher;
	@Mock
	private ContractNegotiationRepository contractNegotiationRepository;
	@Mock
	private AgreementRepository agreementRepository ;
	@Mock
	private OfferRepository offerRepository;
	@Mock
	private PolicyEnforcementService policyEnforcementService;
	
	@Captor
	private ArgumentCaptor<ContractNegotiation> argCaptorContractNegotiation;
	
	@InjectMocks
	private ContractNegotiationConsumerService service;
	
	@Test
	@DisplayName("Process contract offer success")
	public void processContractOffer_success() {
		service.processContractOffer(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE);
		verify(offerRepository).save(any(Offer.class));
		verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
		assertEquals(ContractNegotiationState.OFFERED, argCaptorContractNegotiation.getValue().getState());
	}
	
	
	@Test
	@DisplayName("Process agreement message - automatic negotiation - ON success")
	public void handleAgreement_success() {
		when(properties.isAutomaticNegotiation()).thenReturn(true);
when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));
		
		service.handleAgreement(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE);
		
		verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
		verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
		verify(agreementRepository).save(any(Agreement.class));
		//verify that status is updated to AGREED
		assertEquals(ContractNegotiationState.AGREED, argCaptorContractNegotiation.getValue().getState());
		assertEquals(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE.getAgreement().getId(), argCaptorContractNegotiation.getValue().getAgreement().getId());
		
		verify(publisher).publishEvent(any(ContractAgreementVerificationMessage.class));
	}
	
	@Test
	@DisplayName("Process agreement message - automatic negotiation OFF - success")
	public void handleAgreement_off_success() {
		when(properties.isAutomaticNegotiation()).thenReturn(false);
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));
		
		service.handleAgreement(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE);
		
		verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
		verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
		verify(agreementRepository).save(any(Agreement.class));
		//verify that status is updated to AGREED
		assertEquals(ContractNegotiationState.AGREED, argCaptorContractNegotiation.getValue().getState());
		assertEquals(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE.getAgreement().getId(), argCaptorContractNegotiation.getValue().getAgreement().getId());

		verify(publisher, times(0)).publishEvent(any(ContractAgreementVerificationMessage.class));
	}
	
	@Test
	@DisplayName("Process agreement message - automatic negotiation OFF - negotiation not found")
	public void handleAgreement_off_negotiationNotFound() {
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID)).thenReturn(Optional.ofNullable(null));

		assertThrows(ContractNegotiationNotFoundException.class, () -> service.handleAgreement( NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE));
		
		verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Process agreement message - automatic negotiation OFF - wrong negotiation state")
	public void handleAgreement_off_wrongNegotiationState() {
		
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));

		assertThrows(ContractNegotiationInvalidStateException.class, () -> service.handleAgreement( NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE));
		
		verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Process agreement message - automatic negotiation OFF - offer not found")
	public void handleAgreement_off_offerNotFound() {
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID))
			.thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED_NO_OFFER));

		assertThrows(OfferNotFoundException.class, () -> service.handleAgreement( NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE));
		
		verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
		verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
		verify(agreementRepository, times(0)).save(any(Agreement.class));
	}
	
	@Test
	@DisplayName("Process FINALIZED event message - response success")
	public void handleFinalizeEvent_success() {
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED));
		service.handleFinalizeEvent(NegotiationMockObjectUtil.getEventMessage(ContractNegotiationEventType.FINALIZED));
	
		verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
		//verify that status is updated to FINALIZED
		assertEquals(ContractNegotiationState.FINALIZED, argCaptorContractNegotiation.getValue().getState());
		verify(policyEnforcementService).createPolicyEnforcement(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getAgreement().getId());
		verify(publisher).publishEvent(any(InitializeTransferProcess.class));
	}
	
	@Test
	@DisplayName("Process FINALIZED event message - wrong event type")
	public void handleFinalizeEvent_wrongEventType() {
	
		assertThrows(ContractNegotiationInvalidEventTypeException.class,
				() -> service.handleFinalizeEvent(NegotiationMockObjectUtil.getEventMessage(ContractNegotiationEventType.ACCEPTED)));
	
	}
	
	@Test
	@DisplayName("Process FINALIZED event message - negotiation not found")
	public void handleFinalizeEvent_notFound() {
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.empty());
		
		assertThrows(ContractNegotiationNotFoundException.class,
				() -> service.handleFinalizeEvent(NegotiationMockObjectUtil.getEventMessage(ContractNegotiationEventType.FINALIZED)));
	}
	
	@Test
	@DisplayName("Process FINALIZED event message - invalid state")
	public void handleFinalizeEvent_invalidState() {
		when(contractNegotiationRepository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));
		
		assertThrows(ContractNegotiationInvalidStateException.class,
				() -> service.handleFinalizeEvent(NegotiationMockObjectUtil.getEventMessage(ContractNegotiationEventType.FINALIZED)));
	}
	
	@Test
	@DisplayName("Process termination message success")
	public void handleTerminationRequest_success() {
		when(contractNegotiationRepository.findByConsumerPid(any(String.class)))
			.thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));

		service.handleTerminationRequest(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.TERMINATION_MESSAGE);
		
		verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
		assertEquals(ContractNegotiationState.TERMINATED, argCaptorContractNegotiation.getValue().getState());
	}
	
	@Test
	@DisplayName("Process termination message failed - negotiation not found")
	public void handleTerminationRequest_fail() {
		when(contractNegotiationRepository.findByConsumerPid(any(String.class)))
			.thenReturn(Optional.empty());

		assertThrows(ContractNegotiationNotFoundException.class, 
				() -> service.handleTerminationRequest(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.TERMINATION_MESSAGE));
	}
	
	@Test
	@DisplayName("Process termination message failed - already terminated")
	public void handleTerminationRequest_fail_alreadyTerminated() {
		when(contractNegotiationRepository.findByConsumerPid(any(String.class)))
			.thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_TERMINATED));

		assertThrows(ContractNegotiationInvalidStateException.class, 
				() -> service.handleTerminationRequest(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.TERMINATION_MESSAGE));
	}
}
