package it.eng.negotiation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.*;
import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.util.CredentialUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private AuditEventPublisher publisher;

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
    private ArgumentCaptor<AuditEventType> eventTypeCaptor;
    @Captor
    private ArgumentCaptor<String> descriptionCaptor;
    @Captor
    private ArgumentCaptor<Map<String, Object>> argCaptorAuditEventDetails;

    @InjectMocks
    private ContractNegotiationAPIService service;

    ObjectMapper mapper = new ObjectMapper();

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
        when(contractNegotiationRepository.findById(anyString()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));
        ContractNegotiation response = service.findContractNegotiationById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId());
        assertNotNull(response);
    }

    @Test
    @DisplayName("Find contract negotiations by id - not found")
    public void findContractNegotiationById_notFound() {
        when(contractNegotiationRepository.findById(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.findContractNegotiationById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()));
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
    @DisplayName("Start contract negotiation success")
    public void sendContractRequestMessage_success() {
        HashMap<Object, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put(DSpaceConstants.OFFER, NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));
        when(apiResponse.isSuccess()).thenReturn(true);
        when(properties.consumerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(offerRepository.save(any(Offer.class))).thenReturn(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID);

        service.sendContractRequestMessage(mapper.convertValue(map, JsonNode.class));

        verify(offerRepository).save(argCaptorOffer.capture());
        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(IConstants.ROLE_CONSUMER, argCaptorContractNegotiation.getValue().getRole());
        assertEquals(NegotiationMockObjectUtil.OFFER.getId(), argCaptorOffer.getValue().getOriginalId());

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED, "Contract negotiation requested");
    }

    @Test
    @DisplayName("Start contract negotiation failed")
    public void sendContractRequestMessage_failed() {
        HashMap<Object, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put(DSpaceConstants.OFFER, NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE));
        when(properties.consumerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);

        assertThrows(ContractNegotiationAPIException.class, () -> service.sendContractRequestMessage(mapper.convertValue(map, JsonNode.class)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REJECTED, "Contract negotiation request failed");
    }

    @Test
    @DisplayName("Start contract negotiation json exception")
    public void sendContractRequestMessage_jsonException() {
        HashMap<Object, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put(DSpaceConstants.OFFER, NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn("not a JSON");
        when(apiResponse.isSuccess()).thenReturn(true);
        when(properties.consumerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);

        assertThrows(ContractNegotiationAPIException.class, () -> service.sendContractRequestMessage(mapper.convertValue(map, JsonNode.class)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Send contract request message as counteroffer - success")
    public void sendContractRequestMessageAsCounteroffer_success() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.OFFERED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(offerRepository.save(any(Offer.class))).thenReturn(counterOffer);

        ContractNegotiation result = service.sendContractRequestMessageAsCounteroffer(existingNegotiation.getId(),
                NegotiationSerializer.serializePlainJsonNode(counterOffer));

        assertNotNull(result);
        verify(offerRepository).save(argCaptorOffer.capture());
        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(IConstants.ROLE_CONSUMER, argCaptorContractNegotiation.getValue().getRole());

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REQUESTED, "Contract negotiation request as counteroffer successfully processed");
    }

    @Test
    @DisplayName("Send contract request message as counteroffer - negotiation not found")
    public void sendContractRequestMessageAsCounteroffer_negotiationNotFound() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId())).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractRequestMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_NOT_FOUND, "Contract negotiation not found");
    }

    @Test
    @DisplayName("Send contract request message as counteroffer - wrong state")
    public void sendContractRequestMessageAsCounteroffer_wrongState() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.AGREED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractRequestMessageAsCounteroffer(existingNegotiation.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_STATE_TRANSITION_ERROR, "Contract negotiation state transition error");
    }

    @Test
    @DisplayName("Send contract request message as counteroffer - offer id mismatch")
    public void sendContractRequestMessageAsCounteroffer_offerIdMismatch() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id("different-offer-id")
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.REQUESTED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractRequestMessageAsCounteroffer(existingNegotiation.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_INVALID_OFFER, "Contract negotiation offer not valid error");
    }

    @Test
    @DisplayName("Send contract request message as counteroffer - target mismatch")
    public void sendContractRequestMessageAsCounteroffer_targetMismatch() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target("different-target")
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.REQUESTED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractRequestMessageAsCounteroffer(existingNegotiation.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_INVALID_OFFER, "Contract negotiation offer not valid error");
    }

    @Test
    @DisplayName("Send contract request message as counteroffer - API error response")
    public void sendContractRequestMessageAsCounteroffer_apiError() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.OFFERED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE));

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractRequestMessageAsCounteroffer(existingNegotiation.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REJECTED, "Contract negotiation request as counteroffer failed");
    }

    @Test
    @DisplayName("Send contract request message as counteroffer - JSON processing exception")
    public void sendContractRequestMessageAsCounteroffer_jsonException() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.OFFERED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn("not a valid JSON");

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractRequestMessageAsCounteroffer(existingNegotiation.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REJECTED, "Contract negotiation request as counteroffer failed");
    }

    @Test
    @DisplayName("Process posted offer - success")
    public void postContractOffer_success() {
        HashMap<Object, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put(DSpaceConstants.OFFER, NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
        service.sendContractOfferMessage(mapper.convertValue(map, JsonNode.class));

        verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Process posted offer - error")
    public void postContractOffer_error() {
        HashMap<Object, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put(DSpaceConstants.OFFER, NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractOfferMessage(mapper.convertValue(map, JsonNode.class)));
    }
    @Test
    @DisplayName("Send contract offer message as counteroffer - success")
    public void sendContractOfferMessageAsCounteroffer_success() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.REQUESTED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
        when(offerRepository.save(any(Offer.class))).thenReturn(counterOffer);

        ContractNegotiation result = service.sendContractOfferMessageAsCounteroffer(existingNegotiation.getId(),
                NegotiationSerializer.serializePlainJsonNode(counterOffer));

        assertNotNull(result);
        verify(offerRepository).save(argCaptorOffer.capture());
        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(IConstants.ROLE_PROVIDER, argCaptorContractNegotiation.getValue().getRole());

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_OFFERED, "Contract negotiation offer as counteroffer successfully processed");
    }

    @Test
    @DisplayName("Send contract offer message as counteroffer - negotiation not found")
    public void sendContractOfferMessageAsCounteroffer_negotiationNotFound() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();


        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId())).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_NOT_FOUND, "Contract negotiation not found");
    }

    @Test
    @DisplayName("Send contract offer message as counteroffer - wrong state")
    public void sendContractOfferMessageAsCounteroffer_wrongState() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.OFFERED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractOfferMessageAsCounteroffer(existingNegotiation.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_STATE_TRANSITION_ERROR, "Contract negotiation state transition error");
    }

    @Test
    @DisplayName("Send contract offer message as counteroffer - offer id mismatch")
    public void sendContractOfferMessageAsCounteroffer_offerIdMismatch() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id("different-offer-id")
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.REQUESTED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractOfferMessageAsCounteroffer(existingNegotiation.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_INVALID_OFFER, "Contract negotiation offer not valid error");
    }

    @Test
    @DisplayName("Send contract offer message as counteroffer - target mismatch")
    public void sendContractOfferMessageAsCounteroffer_targetMismatch() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target("different-target")
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.REQUESTED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractOfferMessageAsCounteroffer(existingNegotiation.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_INVALID_OFFER, "Contract negotiation offer not valid error");
    }

    @Test
    @DisplayName("Send contract offer message as counteroffer - API error response")
    public void sendContractOfferMessageAsCounteroffer_apiError() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.REQUESTED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE));

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractOfferMessageAsCounteroffer(existingNegotiation.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(offerRepository, times(0)).save(any(Offer.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REJECTED, "Contract negotiation offer as counteroffer failed");
    }

    @Test
    @DisplayName("Send contract offer message as counteroffer - JSON processing exception")
    public void sendContractOfferMessageAsCounteroffer_jsonException() {
        Offer counterOffer = Offer.Builder.newInstance()
                .id(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID.getOriginalId())
                .target(NegotiationMockObjectUtil.TARGET)
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_SPATIAL))
                .build();

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.REQUESTED)
                .offer(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        when(contractNegotiationRepository.findById(existingNegotiation.getId())).thenReturn(Optional.of(existingNegotiation));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn("not a valid JSON");

        assertThrows(ContractNegotiationAPIException.class, () ->
                service.sendContractOfferMessageAsCounteroffer(existingNegotiation.getId(),
                        NegotiationSerializer.serializePlainJsonNode(counterOffer)));

        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REJECTED, "Contract negotiation offer as counteroffer failed");
    }

    @Test
    @DisplayName("Send agreement success - accepted state")
    public void sendAgreement_success_acceptedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        service.sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId());

        verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
        verify(agreementRepository).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Send agreement success - requested state")
    public void sendAgreement_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));

        service.sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());

        verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
        verify(agreementRepository).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Send agreement failed - negotiation not found")
    public void sendAgreement_failedNegotiationNotFound() {
        assertThrows(ContractNegotiationAPIException.class, () -> service.sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Send agreement failed - wrong negotiation state")
    public void sendAgreement_wrongNegotiationState() {

        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));

        assertThrows(ContractNegotiationAPIException.class, () -> service.sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId()));

        verify(contractNegotiationRepository).findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId());
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Send agreement failed - bad request")
    public void sendAgreement_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getMessage()).thenReturn("bad request");
        when(apiResponse.isSuccess()).thenReturn(false);
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        assertThrows(ContractNegotiationAPIException.class, () -> service.sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Finalize negotiation success")
    public void sendContractNegotiation_EventMessageFinalize_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED));

        service.sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());

        verify(contractNegotiationRepository).save(any(ContractNegotiation.class));
        verify(publisher).publishEvent(any(InitializeTransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_FINALIZED, "Contract negotiation finalized");
    }

    @Test
    @DisplayName("Finalize negotiation failed - negotiation not found")
    public void sendContractNegotiation_failedNegotiationEventMessageFinalizeNotFound() {
        assertThrows(ContractNegotiationAPIException.class, () -> service.sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Finalize negotiation failed - wrong negotiation state")
    public void sendContractNegotiation_wrongNegotiationEventMessageFinalizeState() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));

        verify(contractNegotiationRepository).findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
        verify(agreementRepository, times(0)).save(any(Agreement.class));
    }

    @Test
    @DisplayName("Finalize negotiation error - already finalized")
    public void sendContractNegotiation_EventMessageFinalize_error_finalized_state() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_FINALIZED));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));

        verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());
    }

    @Test
    @DisplayName("Finalize negotiation failed - bad request")
    public void sendContractNegotiation_EventMessageFinalize_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("bad request");
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED));

        assertThrows(ContractNegotiationAPIException.class, () -> service.sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(contractNegotiationRepository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Consumer accepts contract negotiation offered by provider")
    public void sendContractNegotiationEventMessageAccepted() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotiationId))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        ContractNegotiation response = service.sendContractNegotiationEventMessageAccepted(contractNegotiationId);
        assertNotNull(response);
        assertEquals(ContractNegotiationState.ACCEPTED, response.getState());

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_ACCEPTED, "Contract negotiation accepted");
    }

    @Test
    @DisplayName("Consumer accepts contract negotiation offered by provider")
    public void sendContractNegotiationEventMessageAccepted_invalid_state() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotiationId))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.sendContractNegotiationEventMessageAccepted(contractNegotiationId));
    }

    @Test
    @DisplayName("Consumer accepts contract negotiation offered by provider - error api")
    public void sendContractNegotiationEventMessageAccepted_error_api() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotiationId))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("error");

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.sendContractNegotiationEventMessageAccepted(contractNegotiationId));

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REJECTED, "Contract negotiation accepted failed");
    }

    @Test
    @DisplayName("Provider accepts contract negotiation")
    public void handleCNApproved() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotiationId)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        service.sendContractAgreementMessage(contractNegotiationId);

        verify(agreementRepository).save(argCaptorAgreement.capture());
        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(ContractNegotiationState.AGREED, argCaptorContractNegotiation.getValue().getState());
        assertEquals(argCaptorAgreement.getValue().getId(), argCaptorContractNegotiation.getValue().getAgreement().getId());

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_AGREED, "Contract negotiation agreed");
    }

    @Test
    @DisplayName("Provider accepts contract negotiation - invalid initial state")
    public void handleCNApproved_invalid_state() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotiationId)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.sendContractAgreementMessage(contractNegotiationId));
    }

    @Test
    @DisplayName("Provider accepts contract negotiation - error while contacting consumer")
    public void handleCNApproved_error_consumer() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotiationId)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("Error while contacting consumer");

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.sendContractAgreementMessage(contractNegotiationId));

        verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());
        verify(agreementRepository, times(0)).save(argCaptorAgreement.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REJECTED, "Contract negotiation approval failed");
    }

    @Test
    @DisplayName("Handle agreement verification message success")
    public void contractAgreementVerificationMessage_success() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        service.sendContractAgreementVerificationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED.getId());

        verify(contractNegotiationRepository).save(any(ContractNegotiation.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_VERIFIED, "Contract negotiation verified");
    }

    @Test
    @DisplayName("Handle agreement verification message - contract negotiation not found")
    public void contractAgreementVerificationMessage_contractNegotiationNotFound() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId())).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationAPIException.class, () -> service.sendContractAgreementVerificationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()));
    }

    @Test
    @DisplayName("Handle agreement verification message - invalid state")
    public void contractAgreementVerificationMessage_invalidState() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        assertThrows(ContractNegotiationAPIException.class, () -> service.sendContractAgreementVerificationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()));
    }

    @Test
    @DisplayName("Handle agreement verification message - bad request")
    public void contractAgreementVerificationMessage_badRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE));
        assertThrows(ContractNegotiationAPIException.class,
                () -> service.sendContractAgreementVerificationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()));

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REJECTED, "Contract negotiation verification failed");
    }

    @Test
    @DisplayName("Handle termination - provider")
    public void handleTerminationCN_provider() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED_PROVIDER.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED_PROVIDER));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        service.sendContractNegotiationTerminationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED_PROVIDER.getId());

        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(ContractNegotiationState.TERMINATED, argCaptorContractNegotiation.getValue().getState());

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED, "Contract negotiation terminated");
    }

    @Test
    @DisplayName("Handle termination - consumer")
    public void handleTerminationCN_consumer() {
        when(contractNegotiationRepository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        service.sendContractNegotiationTerminationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());

        verify(contractNegotiationRepository).save(argCaptorContractNegotiation.capture());
        assertEquals(ContractNegotiationState.TERMINATED, argCaptorContractNegotiation.getValue().getState());

        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED, "Contract negotiation terminated");
    }

    @Test
    @DisplayName("Provider terminate contract negotiation - contract negotiation not found")
    public void terminateNegotiation_cn_not_found() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotiationId)).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.sendContractNegotiationTerminationMessage(contractNegotiationId));

        verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());
    }

    @Test
    @DisplayName("Provider terminate contract negotiation - consumer did not respond")
    public void terminateNegotiation_consumer_error() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(contractNegotiationRepository.findById(contractNegotiationId)).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getData()).thenReturn(NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE));

        assertThrows(ContractNegotiationAPIException.class,
                () -> service.sendContractNegotiationTerminationMessage(contractNegotiationId));

        verify(contractNegotiationRepository, times(0)).save(argCaptorContractNegotiation.capture());
        verifyAuditEvent(AuditEventType.PROTOCOL_NEGOTIATION_REJECTED, "Contract negotiation termination failed");
    }

    private void verifyAuditEvent(AuditEventType eventType, String description) {
        verify(publisher).publishEvent(eventTypeCaptor.capture(), descriptionCaptor.capture(), argCaptorAuditEventDetails.capture());
        assertEquals(eventType, eventTypeCaptor.getValue());
        assertEquals(description, descriptionCaptor.getValue());
        assertNotNull(argCaptorAuditEventDetails.getValue());
    }
}
