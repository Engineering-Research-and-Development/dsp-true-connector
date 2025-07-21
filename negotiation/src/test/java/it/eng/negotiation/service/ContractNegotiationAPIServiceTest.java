package it.eng.negotiation.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.*;
import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationAPIServiceTest {

    @Mock
    private ApplicationEventPublisher publisher;
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
    @Mock
    private PolicyAdministrationPoint policyAdministrationPoint;
    @Mock
    private Pageable pageable;

    @Captor
    private ArgumentCaptor<ContractNegotiation> argCaptorContractNegotiation;
    @Captor
    private ArgumentCaptor<Agreement> argCaptorAgreement;
    @Captor
    private ArgumentCaptor<Offer> argCaptorOffer;
    @Captor
    private ArgumentCaptor<AuditEvent> argCaptorAuditEvent;

    @InjectMocks
    private ContractNegotiationAPIService service;

    @Test
    @DisplayName("Start contract negotiation success")
    public void startNegotiation_success() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));
        when(apiResponse.isSuccess()).thenReturn(true);
        when(properties.consumerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(offerRepository.save(any(Offer.class))).thenReturn(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID);

        service.startNegotiation(NegotiationMockObjectUtil.FORWARD_TO, NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER));

        verify(offerRepository).save(argCaptorOffer.capture());
        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(IConstants.ROLE_CONSUMER, argCaptorContractNegotiation.getValue().getRole());
        assertEquals(NegotiationMockObjectUtil.OFFER.getId(), argCaptorOffer.getValue().getOriginalId());

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED, auditEvent.getEventType());
        assertEquals("Contract negotiation requested", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Start contract negotiation failed")
    public void startNegotiation_failed() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE));
        when(properties.consumerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);

        assertThrows(ContractNegotiationAPIException.class, () -> service.startNegotiation(NegotiationMockObjectUtil.FORWARD_TO, NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED, auditEvent.getEventType());
        assertEquals("Contract negotiation request failed", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Start contract negotiation json exception")
    public void startNegotiation_jsonException() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn("not a JSON");
        when(apiResponse.isSuccess()).thenReturn(true);
        when(properties.consumerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);

        assertThrows(ContractNegotiationAPIException.class, () -> service.startNegotiation(NegotiationMockObjectUtil.FORWARD_TO, NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Process posted offer - success")
    public void postContractOffer_success() {
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
        // plain jsonNode
        service.sendContractOffer(NegotiationMockObjectUtil.FORWARD_TO, NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER));

        verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Process posted offer - error")
    public void postContractOffer_error() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractOffer(NegotiationMockObjectUtil.FORWARD_TO, NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER)));
    }

    @Test
    @DisplayName("Send agreement success - accepted state")
    public void sendAgreement_success_acceptedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        service.sendAgreement(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.AGREEMENT));

        verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
        verify(agreementRepository).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Send agreement success - requested state")
    public void sendAgreement_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));

        service.sendAgreement(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.AGREEMENT));

        verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
        verify(agreementRepository).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Send agreement failed - negotiation not found")
    public void sendAgreement_failedNegotiationNotFound() {
        assertThrows(ContractNegotiationAPIException.class, () -> service.sendAgreement(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.AGREEMENT)));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Send agreement failed - wrong negotiation state")
    public void sendAgreement_wrongNegotiationState() {

        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));

        assertThrows(ContractNegotiationAPIException.class, () -> service.sendAgreement(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.AGREEMENT)));

        verify(contractNegotiationRepository).findByProviderPidAndConsumerPid(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.CONSUMER_PID);
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Send agreement failed - bad request")
    public void sendAgreement_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(contractNegotiationRepository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        assertThrows(ContractNegotiationAPIException.class, () -> service.sendAgreement(NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.AGREEMENT)));

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
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED));

        service.finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());

        verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
        verify(publisher).publishEvent(any(InitializeTransferProcess.class));

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_FINALIZED, auditEvent.getEventType());
        assertEquals("Contract negotiation finalized", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Finalize negotiation failed - negotiation not found")
    public void finalizeNegotiation_failedNegotiationNotFound() {
        assertThrows(ContractNegotiationAPIException.class, () -> service.finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Finalize negotiation failed - wrong negotiation state")
    public void finalizeNegotiation_wrongNegotiationState() {

        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));

        verify(contractNegotiationRepository).findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Finalize negotiation error - already finalized")
    public void finalizeNegotiation_error_finalized_state() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_FINALIZED));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));

        verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());
    }

    @Test
    @DisplayName("Finalize negotiation failed - bad request")
    public void finalizeNegotiation_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("bad request");
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED));

        assertThrows(ContractNegotiationAPIException.class, () -> service.finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Find contract negotiations by role")
    public void findContractNegotiationByRole() {
        when(contractNegotiationRepository.findWithDynamicFilters(anyMap(), eq(ContractNegotiation.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Arrays.asList(
                        NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED,
                        NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED)));
        Map<String, Object> filters = new HashMap<>();
        filters.put("role", IConstants.ROLE_CONSUMER);
        Page<ContractNegotiation> response = service.findContractNegotiations(filters, pageable);
        assertNotNull(response);
        assertEquals(2, response.getTotalElements());
    }

    @Test
    @DisplayName("Find contract negotiations by id")
    public void findContractNegotiationById() {
        when(contractNegotiationRepository.findWithDynamicFilters(anyMap(), eq(ContractNegotiation.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED)));
        Map<String, Object> filters = new HashMap<>();
        filters.put("id", NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId());
        Page<ContractNegotiation> response = service.findContractNegotiations(filters, pageable);
        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    @DisplayName("Find contract negotiations by pids")
    public void findContractNegotiationByPids() {
        when(contractNegotiationRepository.findWithDynamicFilters(anyMap(), eq(ContractNegotiation.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED)));
        Map<String, Object> filters = new HashMap<>();
        filters.put(IConstants.CONSUMER_PID, NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getConsumerPid());
        filters.put(IConstants.PROVIDER_PID, NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getProviderPid());
        Page<ContractNegotiation> response = service.findContractNegotiations(filters, pageable);
        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    @DisplayName("Consumer accepts contract negotiation offered by provider")
    public void handleContractNegotiationAccepted() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotaitionId))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        ContractNegotiation response = service.handleContractNegotiationAccepted(contractNegotaitionId);
        assertNotNull(response);
        assertEquals(ContractNegotiationState.ACCEPTED, response.getState());

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_ACCEPTED, auditEvent.getEventType());
        assertEquals("Contract negotiation accepted", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Consumer accepts contract negotiation offered by provider")
    public void handleContractNegotiationAccepted_invalid_state() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotaitionId))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.handleContractNegotiationAccepted(contractNegotaitionId));
    }

    @Test
    @DisplayName("Consumer accepts contract negotiation offered by provider - error api")
    public void handleContractNegotiationAccepted_error_api() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotaitionId))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("error");

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.handleContractNegotiationAccepted(contractNegotaitionId));

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_ACCEPTED, auditEvent.getEventType());
        assertEquals("Contract negotiation accepted failed", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Provider accepts contract negotiation")
    public void handleCNApproved() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(properties.getAssignee()).thenReturn(NegotiationMockObjectUtil.ASSIGNEE);
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        service.approveContractNegotiation(contractNegotaitionId);

        verify(agreementRepository).save(argCaptorAgreement.capture());
        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(ContractNegotiationState.AGREED, argCaptorContractNegotiation.getValue().getState());
        assertEquals(argCaptorAgreement.getValue().getId(), argCaptorContractNegotiation.getValue().getAgreement().getId());

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_AGREED, auditEvent.getEventType());
        assertEquals("Contract negotiation agreed", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Provider accepts contract negotiation - invalid initial state")
    public void handleCNApproved_invalid_state() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.approveContractNegotiation(contractNegotaitionId));
    }

    @Test
    @DisplayName("Provider accepts contract negotiation - error while contacting consumer")
    public void handleCNApproved_error_consumer() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(properties.getAssignee()).thenReturn(NegotiationMockObjectUtil.ASSIGNEE);
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("Error while contacting consumer");

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.approveContractNegotiation(contractNegotaitionId));

        verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());
        verify(agreementRepository, times(0)).save(argCaptorAgreement.capture());

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_AGREED, auditEvent.getEventType());
        assertEquals("Contract negotiation approval failed", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Handle agreement verification message success")
    public void contractAgreementVerificationMessage_success() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        service.verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED.getId());

        verify(contractNegotiationRepository).save(any(ContractNegotiation.class));

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_VERIFIED, auditEvent.getEventType());
        assertEquals("Contract negotiation verified", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Handle agreement verification message - contract negotiation not found")
    public void contractAgreementVerificationMessage_contractNegotiationNotFound() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId())).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationAPIException.class, () -> service.verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()));
    }

    @Test
    @DisplayName("Handle agreement verification message - invalid state")
    public void contractAgreementVerificationMessage_invalidState() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        assertThrows(ContractNegotiationAPIException.class, () -> service.verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()));
    }

    @Test
    @DisplayName("Handle agreement verification message - bad request")
    public void contractAgreementVerificationMessage_badRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE));
        assertThrows(ContractNegotiationAPIException.class,
                () -> service.verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()));

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_VERIFIED, auditEvent.getEventType());
        assertEquals("Contract negotiation verification failed", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Handle termination - provider")
    public void handleTerminationCN_provider() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED_PROIVDER.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED_PROIVDER));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        service.handleContractNegotiationTerminated(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED_PROIVDER.getId());

        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(ContractNegotiationState.TERMINATED, argCaptorContractNegotiation.getValue().getState());

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED, auditEvent.getEventType());
        assertEquals("Contract negotiation terminated", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Handle termination - consumer")
    public void handleTerminationCN_consumer() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        service.handleContractNegotiationTerminated(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());

        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(ContractNegotiationState.TERMINATED, argCaptorContractNegotiation.getValue().getState());

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED, auditEvent.getEventType());
        assertEquals("Contract negotiation terminated", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    @Test
    @DisplayName("Provider terminate contract negotiation - contract negotiation not found")
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
        when(contractNegotiationRepository.findById(contractNegotaitionId)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.handleContractNegotiationTerminated(contractNegotaitionId));

        verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());

        verify(publisher).publishEvent(argCaptorAuditEvent.capture());
        AuditEvent auditEvent = argCaptorAuditEvent.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED, auditEvent.getEventType());
        assertEquals("Contract negotiation termination failed", auditEvent.getDescription());
        assertNotNull(auditEvent.getDetails());
    }

    // validate agreement
    @Test
    @DisplayName("Validate agreement valid")
    public void validateAgreement() {
        when(contractNegotiationRepository.findByAgreement(NegotiationMockObjectUtil.AGREEMENT.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_FINALIZED));
        when(policyAdministrationPoint.policyEnforcementExists(NegotiationMockObjectUtil.AGREEMENT.getId())).thenReturn(true);
        assertDoesNotThrow(() -> service.validateAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
    }

    @Test
    @DisplayName("Validate agreement - not valid")
    public void validateAgreement_not_valid() {
        when(contractNegotiationRepository.findByAgreement(NegotiationMockObjectUtil.AGREEMENT.getId())).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationAPIException.class, () -> service.validateAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
    }
}
