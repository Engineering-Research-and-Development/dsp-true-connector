package it.eng.negotiation.service;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.exception.ContractNegotiationExistsException;
import it.eng.negotiation.exception.ContractNegotiationInvalidEventTypeException;
import it.eng.negotiation.exception.ContractNegotiationInvalidStateException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.OfferNotFoundException;
import it.eng.negotiation.model.*;
import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequestEvent;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;
import it.eng.tools.model.IConstants;
import it.eng.tools.service.AuditEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DSPContractNegotiationConsumerServiceTest {

    @Mock
    private AuditEventPublisher publisher;
    @Mock
    private ContractNegotiationRepository contractNegotiationRepository;
    @Mock
    private AgreementRepository agreementRepository;
    @Mock
    private OfferRepository offerRepository;
    @Mock
    private PolicyAdministrationPoint policyAdministrationPoint;

    @Captor
    private ArgumentCaptor<ContractNegotiation> argCaptorContractNegotiation;

    @Captor
    private ArgumentCaptor<Offer> argCaptorOffer;

    @InjectMocks
    private DSPContractNegotiationConsumerService service;

    @Test
    @DisplayName("Process contract offer success")
    public void handleContractOfferMessage_success() {
        ContractNegotiation result = service.handleContractOfferMessage(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE_INITIAL);
        verify(offerRepository).save(any(Offer.class));
        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(ContractNegotiationState.OFFERED, argCaptorContractNegotiation.getValue().getState());

        assertNotNull(result);
        assertEquals(result.getType(), "ContractNegotiation");
        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        verify(offerRepository).save(argCaptorOffer.capture());
        //verify that status is updated to OFFERED
        assertEquals(ContractNegotiationState.OFFERED, argCaptorContractNegotiation.getValue().getState());
        assertEquals(NegotiationMockObjectUtil.CALLBACK_ADDRESS, argCaptorContractNegotiation.getValue().getCallbackAddress());
        assertEquals(NegotiationMockObjectUtil.PROVIDER_PID, argCaptorContractNegotiation.getValue().getProviderPid());
        assertEquals(IConstants.ROLE_CONSUMER, argCaptorContractNegotiation.getValue().getRole());
        assertEquals(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE_INITIAL.getOffer().getId(), argCaptorOffer.getValue().getOriginalId());
        assertNotNull(argCaptorContractNegotiation.getValue().getConsumerPid());
        verify(publisher, times(0)).publishEvent(any(ContractNegotationOfferRequestEvent.class));
    }

    @Test
    @DisplayName("Process contract offer - negotiation already exists")
    public void handleContractOfferMessage_alreadyExists() {
        when(contractNegotiationRepository.findByProviderPid(NegotiationMockObjectUtil.PROVIDER_PID))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));

        assertThrows(ContractNegotiationExistsException.class,
                () -> service.handleContractOfferMessage(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE_INITIAL));

        verify(contractNegotiationRepository).findByProviderPid(NegotiationMockObjectUtil.PROVIDER_PID);
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));
    }

    @Test
    @DisplayName("Process contract offer as counteroffer - success")
    public void handleContractOfferMessageAsCounteroffer_success() {
        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .state(ContractNegotiationState.REQUESTED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        ContractOfferMessage counterOfferMessage = ContractOfferMessage.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .offer(Offer.Builder.newInstance()
                        .id("some-original-id")
                        .target(NegotiationMockObjectUtil.TARGET)
                        .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                        .assigner(NegotiationMockObjectUtil.ASSIGNER)
                        .permission(NegotiationMockObjectUtil.OFFER.getPermission())
                        .build())
                .build();

        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID))
                .thenReturn(Optional.of(existingNegotiation));

        service.handleContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONSUMER_PID, counterOfferMessage);

        verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        verify(offerRepository, times(2)).save(any(Offer.class));
        assertEquals(ContractNegotiationState.OFFERED, argCaptorContractNegotiation.getValue().getState());
        verify(publisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("Process contract offer as counteroffer - consumer PID mismatch")
    public void handleContractOfferMessageAsCounteroffer_consumerPidMismatch() {
        ContractOfferMessage message = ContractOfferMessage.Builder.newInstance()
                .consumerPid("wrong-consumer-pid")
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .offer(NegotiationMockObjectUtil.OFFER)
                .build();

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> service.handleContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONSUMER_PID, message));

        verify(contractNegotiationRepository, times(0)).findByProviderPidAndConsumerPid(any(), any());
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Process contract offer as counteroffer - negotiation not found")
    public void handleContractOfferMessageAsCounteroffer_negotiationNotFound() {
        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID))
                .thenReturn(Optional.empty());

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> service.handleContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE_COUNTEROFFER));

        verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Process contract offer as counteroffer - offer ID mismatch")
    public void handleContractOfferMessageAsCounteroffer_offerIdMismatch() {
        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .state(ContractNegotiationState.REQUESTED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        ContractOfferMessage counterOfferMessage = ContractOfferMessage.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .offer(Offer.Builder.newInstance()
                        .id("different-offer-id")
                        .target(NegotiationMockObjectUtil.TARGET)
                        .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                        .assigner(NegotiationMockObjectUtil.ASSIGNER)
                        .permission(NegotiationMockObjectUtil.OFFER.getPermission())
                        .build())
                .build();

        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID))
                .thenReturn(Optional.of(existingNegotiation));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.handleContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONSUMER_PID, counterOfferMessage));

        verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Process contract offer as counteroffer - target mismatch")
    public void handleContractOfferMessageAsCounteroffer_targetMismatch() {
        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .state(ContractNegotiationState.REQUESTED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        ContractOfferMessage counterOfferMessage = ContractOfferMessage.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .offer(Offer.Builder.newInstance()
                        .id("some-original-id")
                        .target("different-target")
                        .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                        .assigner(NegotiationMockObjectUtil.ASSIGNER)
                        .permission(NegotiationMockObjectUtil.OFFER.getPermission())
                        .build())
                .build();

        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID))
                .thenReturn(Optional.of(existingNegotiation));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.handleContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONSUMER_PID, counterOfferMessage));

        verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Process contract offer as counteroffer - invalid state")
    public void handleContractOfferMessageAsCounteroffer_invalidState() {
        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .state(ContractNegotiationState.FINALIZED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        ContractOfferMessage counterOfferMessage = ContractOfferMessage.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .offer(Offer.Builder.newInstance()
                        .id("some-original-id")
                        .target(NegotiationMockObjectUtil.TARGET)
                        .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                        .assigner(NegotiationMockObjectUtil.ASSIGNER)
                        .permission(NegotiationMockObjectUtil.OFFER.getPermission())
                        .build())
                .build();

        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID))
                .thenReturn(Optional.of(existingNegotiation));

        assertThrows(ContractNegotiationInvalidStateException.class,
                () -> service.handleContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONSUMER_PID, counterOfferMessage));

        verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Process agreement message - success")
    public void handleContractAgreement_Message_success() {
        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        service.handleContractAgreementMessage(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE);

        verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        verify(agreementRepository).save(any(Agreement.class));
        //verify that status is updated to AGREED
        assertEquals(ContractNegotiationState.AGREED, argCaptorContractNegotiation.getValue().getState());
        assertEquals(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE.getAgreement().getId(), argCaptorContractNegotiation.getValue().getAgreement().getId());
        verify(publisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("Process agreement message - negotiation not found")
    public void handleContractAgreement_Message_negotiationNotFound() {
        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID)).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationNotFoundException.class, () -> service.handleContractAgreementMessage(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE));

        verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Process agreement message - wrong negotiation state")
    public void handleContractAgreement_Message_wrongNegotiationState() {

        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));

        assertThrows(ContractNegotiationInvalidStateException.class, () -> service.handleContractAgreementMessage(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE));

        verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Process agreement message - offer not found")
    public void handleContractAgreement_Message_offerNotFound() {
        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED_NO_OFFER));

        assertThrows(OfferNotFoundException.class, () -> service.handleContractAgreementMessage(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE));

        verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Process FINALIZED event message - response success")
    public void handleContractNegotiationEvent_MessageFinalize_success() {
        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED));
        service.handleContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.getEventMessage(ContractNegotiationEventType.FINALIZED));

        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        //verify that status is updated to FINALIZED
        assertEquals(ContractNegotiationState.FINALIZED, argCaptorContractNegotiation.getValue().getState());
        verify(policyAdministrationPoint).createPolicyEnforcement(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getAgreement().getId());
        verify(publisher).publishEvent(any(InitializeTransferProcess.class));
    }

    @Test
    @DisplayName("Process FINALIZED event message - wrong event type")
    public void handleContractNegotiationEvent_wrongEventMessageFinalizeType() {

        assertThrows(ContractNegotiationInvalidEventTypeException.class,
                () -> service.handleContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.getEventMessage(ContractNegotiationEventType.ACCEPTED)));
    }

    @Test
    @DisplayName("Process FINALIZED event message - negotiation not found")
    public void handleContractNegotiationEvent_MessageFinalize_notFound() {
        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> service.handleContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.getEventMessage(ContractNegotiationEventType.FINALIZED)));
    }

    @Test
    @DisplayName("Process FINALIZED event message - invalid state")
    public void handleContractNegotiationEvent_MessageFinalize_invalidState() {
        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(any(String.class), any(String.class))).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));

        assertThrows(ContractNegotiationInvalidStateException.class,
                () -> service.handleContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.getEventMessage(ContractNegotiationEventType.FINALIZED)));
    }

    @Test
    @DisplayName("Process termination message success")
    public void handleContractNegotiationTerminationMessage_success() {
        when(contractNegotiationRepository.findByConsumerPid(any(String.class)))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));

        service.handleContractNegotiationTerminationMessage(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.TERMINATION_MESSAGE);

        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(ContractNegotiationState.TERMINATED, argCaptorContractNegotiation.getValue().getState());
    }

    @Test
    @DisplayName("Process termination message failed - negotiation not found")
    public void handleContractNegotiationTerminationMessage_fail() {
        when(contractNegotiationRepository.findByConsumerPid(any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> service.handleContractNegotiationTerminationMessage(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.TERMINATION_MESSAGE));
    }

    @Test
    @DisplayName("Process termination message failed - already terminated")
    public void handleContractNegotiationTerminationMessage_fail_alreadyTerminated() {
        when(contractNegotiationRepository.findByConsumerPid(any(String.class)))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_TERMINATED));

        assertThrows(ContractNegotiationInvalidStateException.class,
                () -> service.handleContractNegotiationTerminationMessage(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.TERMINATION_MESSAGE));
    }

    @Test
    @DisplayName("Get negotiation by consumer PID - success")
    public void getNegotiationByConsumerPid_success() {
        when(contractNegotiationRepository.findByConsumerPid(NegotiationMockObjectUtil.CONSUMER_PID))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));

        ContractNegotiation result = service.getNegotiationByConsumerPid(NegotiationMockObjectUtil.CONSUMER_PID);

        assertNotNull(result);
        assertEquals(NegotiationMockObjectUtil.CONSUMER_PID, result.getConsumerPid());
        verify(contractNegotiationRepository).findByConsumerPid(NegotiationMockObjectUtil.CONSUMER_PID);
        verify(publisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("Get negotiation by consumer PID - not found")
    public void getNegotiationByConsumerPid_notFound() {
        when(contractNegotiationRepository.findByConsumerPid(NegotiationMockObjectUtil.CONSUMER_PID))
                .thenReturn(Optional.empty());

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> service.getNegotiationByConsumerPid(NegotiationMockObjectUtil.CONSUMER_PID));

        verify(contractNegotiationRepository).findByConsumerPid(NegotiationMockObjectUtil.CONSUMER_PID);
    }

    @Test
    @DisplayName("Process agreement message - consumer PID mismatch")
    public void handleContractAgreementMessage_consumerPidMismatch() {
        ContractAgreementMessage message = ContractAgreementMessage.Builder.newInstance()
                .consumerPid("wrong-consumer-pid")
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .agreement(NegotiationMockObjectUtil.AGREEMENT)
                .build();

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> service.handleContractAgreementMessage(NegotiationMockObjectUtil.CONSUMER_PID, message));

        verify(contractNegotiationRepository, times(0)).findByProviderPidAndConsumerPid(any(), any());
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Process FINALIZED event message - consumer PID mismatch")
    public void handleContractNegotiationEventMessageFinalize_consumerPidMismatch() {
        ContractNegotiationEventMessage message = ContractNegotiationEventMessage.Builder.newInstance()
                .consumerPid("wrong-consumer-pid")
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .eventType(ContractNegotiationEventType.FINALIZED)
                .build();

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> service.handleContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONSUMER_PID, message));

        verify(contractNegotiationRepository, times(0)).findByProviderPidAndConsumerPid(any(), any());
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Process termination message - consumer PID mismatch")
    public void handleContractNegotiationTerminationMessage_consumerPidMismatch() {
        ContractNegotiationTerminationMessage message = ContractNegotiationTerminationMessage.Builder.newInstance()
                .consumerPid("wrong-consumer-pid")
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .code("Test")
                .reason(java.util.Collections.singletonList("test"))
                .build();

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> service.handleContractNegotiationTerminationMessage(NegotiationMockObjectUtil.CONSUMER_PID, message));

        verify(contractNegotiationRepository, times(0)).findByConsumerPid(any());
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }
}
