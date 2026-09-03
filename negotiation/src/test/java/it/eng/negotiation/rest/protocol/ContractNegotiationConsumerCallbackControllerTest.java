package it.eng.negotiation.rest.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.negotiation.exception.ContractNegotiationInvalidStateException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.model.*;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationConsumerService;
import it.eng.tools.model.Tenant;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationConsumerCallbackControllerTest {

    private static final String TENANT_ID = "engineering";

    @InjectMocks
    private ContractNegotiationConsumerCallbackController controller;

    @Mock
    private ContractNegotiationConsumerService contractNegotiationConsumerService;
    @Mock
    private ContractNegotiationProperties properties;
    @Mock
    private TenantService tenantService;

    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        Tenant tenant = Tenant.Builder.newInstance()
                .id(TENANT_ID).name("Engineering").participantId("c1")
                .enabled(true).build();
        when(tenantService.findEnabledTenantById(TENANT_ID)).thenReturn(tenant);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    public void handleContractOfferMessage_success() throws JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE_INITIAL);
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.handleContractOfferMessage(any(ContractOfferMessage.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED);
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        ResponseEntity<JsonNode> response = controller.handleContractOfferMessage(TENANT_ID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleContractOfferMessageAsCounteroffer_success() throws JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE_INITIAL);
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.handleContractOfferMessageAsCounteroffer(anyString(), any(ContractOfferMessage.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED);
        ResponseEntity<JsonNode> response = controller.handleContractOfferMessageAsCounteroffer(TENANT_ID, NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleContractAgreement_Message_success() throws JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE);
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.handleContractAgreementMessage(anyString(), any(ContractAgreementMessage.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED);

        ResponseEntity<Void> response = controller.handleContractAgreementMessage(TENANT_ID, NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        verify(contractNegotiationConsumerService).handleContractAgreementMessage(anyString(), any(ContractAgreementMessage.class));
    }

    @Test
    public void handleContractAgreement_Message_failed() throws JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE);
        JsonNode jsonNode = mapper.readTree(json);

        doThrow(new ContractNegotiationInvalidStateException("Something not correct - tests", NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID))
                .when(contractNegotiationConsumerService).handleContractAgreementMessage(anyString(), any(ContractAgreementMessage.class));

        assertThrows(ContractNegotiationInvalidStateException.class, () ->
                controller.handleContractAgreementMessage(TENANT_ID, NegotiationMockObjectUtil.CONSUMER_PID, jsonNode));
    }

    @Test
    public void handleContractNegotiationEvent_MessageFinalize_success() throws JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE);
        JsonNode jsonNode = mapper.readTree(json);
        doNothing().when(contractNegotiationConsumerService).handleContractNegotiationEventMessageFinalize(anyString(), any(ContractNegotiationEventMessage.class));

        ResponseEntity<Void> response = controller.handleContractNegotiationEventMessageFinalize(TENANT_ID, NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNull(response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleContractNegotiationEvent_MessageFinalize_failed() {
        JsonNode jsonNode = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE);

        doThrow(new ContractNegotiationInvalidStateException("Something not correct - tests", NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID))
                .when(contractNegotiationConsumerService).handleContractNegotiationEventMessageFinalize(anyString(), any(ContractNegotiationEventMessage.class));

        assertThrows(ContractNegotiationInvalidStateException.class, () ->
                controller.handleContractNegotiationEventMessageFinalize(TENANT_ID, NegotiationMockObjectUtil.CONSUMER_PID, jsonNode));
    }

    @Test
    public void handleContractNegotiationTerminationMessage() {
        JsonNode jsonNode = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.TERMINATION_MESSAGE);

        ResponseEntity<JsonNode> response = controller.handleContractNegotiationTerminationMessage(TENANT_ID, NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleContractNegotiationTerminationMessage_error_service() {
        JsonNode jsonNode = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.TERMINATION_MESSAGE);
        doThrow(ContractNegotiationNotFoundException.class).when(contractNegotiationConsumerService)
                .handleContractNegotiationTerminationMessage(any(String.class), any(ContractNegotiationTerminationMessage.class));
        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.handleContractNegotiationTerminationMessage(TENANT_ID, NegotiationMockObjectUtil.CONSUMER_PID, jsonNode));
    }
}
