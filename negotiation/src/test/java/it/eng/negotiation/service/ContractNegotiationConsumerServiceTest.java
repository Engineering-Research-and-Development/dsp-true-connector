package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationConsumerServiceTest {

	@Mock
	private ContractNegotiationProperties properties;
	@Mock
	private ContractNegotiationPublisher publisher;
	@Mock
	private ContractNegotiationRepository repository;
	
	@Captor
	private ArgumentCaptor<ContractNegotiation> argCaptorContractNegotiation;
	
	@InjectMocks
	private ContractNegotiationConsumerService service;
	
	@Test
	@DisplayName("Process contract offer success")
	public void processContractOffer_success() {
		service.processContractOffer(ModelUtil.CONTRACT_OFFER_MESSAGE);
	}
	
	@Test
	@DisplayName("Process negotiation offer consumer success")
	public void handleNegotiationOfferConsumer_success() {
		service.handleNegotiationOfferConsumer(ModelUtil.CONSUMER_PID, ModelUtil.CONTRACT_OFFER_MESSAGE);
	}
	
	@Test
	@DisplayName("Process agreement message automatic negotiation ON success")
	public void handleAgreement_success() {
		when(properties.isAutomaticNegotiation()).thenReturn(true);
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION));
		service.handleAgreement(ModelUtil.CALLBACK_ADDRESS, ModelUtil.CONTRACT_AGREEMENT_MESSAGE);
		
		verify(publisher).publishEvent(any(ContractAgreementVerificationMessage.class));
	}
	
	@Test
	@DisplayName("Process agreement message automatic negotiation OFF success")
	public void handleAgreement_off_success() {
		when(properties.isAutomaticNegotiation()).thenReturn(false);
		when(repository.findByProviderPidAndConsumerPid(ModelUtil.PROVIDER_PID, ModelUtil.CONSUMER_PID)).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION));

		service.handleAgreement(ModelUtil.CALLBACK_ADDRESS, ModelUtil.CONTRACT_AGREEMENT_MESSAGE);
		
		verify(repository).findByProviderPidAndConsumerPid(ModelUtil.PROVIDER_PID, ModelUtil.CONSUMER_PID);
		verify(repository).save(argCaptorContractNegotiation.capture());

		//verify that status is updated to AGREED
		assertEquals(ContractNegotiationState.AGREED, argCaptorContractNegotiation.getValue().getState());
	}
	
	@Test
	@DisplayName("Process ACCEPTED event message reposne success")
	public void handleEventsResponse_accepted_success() {
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION));
		service.handleEventsResponse(ModelUtil.CONSUMER_PID, ModelUtil.getEventMessage(ContractNegotiationEventType.ACCEPTED));
	}
	
	@Test
	@DisplayName("Process FINALIZED event message reposne success")
	public void handleEventsResponse_finalized_success() {
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_FINALIZED));
		service.handleEventsResponse(ModelUtil.CONSUMER_PID, ModelUtil.getEventMessage(ContractNegotiationEventType.FINALIZED));
	
		verify(repository).save(argCaptorContractNegotiation.capture());

		//verify that status is updated to FINALIZED
		assertEquals(ContractNegotiationState.FINALIZED, argCaptorContractNegotiation.getValue().getState());
	
	}
	
	@Test
	@DisplayName("Process event message not found negotiation")
	public void handleEventsResponse_notFound() {
		when(repository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.empty());
		
		assertThrows(ContractNegotiationNotFoundException.class, () -> 
			service.handleEventsResponse(ModelUtil.CONSUMER_PID, ModelUtil.getEventMessage(ContractNegotiationEventType.ACCEPTED))
		);
	}
	
	@Test
	@DisplayName("Process termination message success")
	public void handleTerminationResponse_success() {
		service.handleTerminationResponse(ModelUtil.CONSUMER_PID, ModelUtil.TERMINATION_MESSAGE);
	}
}
