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

    @InjectMocks
    private ContractNegotiationConsumerCallbackController controller;

    @Mock
    private ContractNegotiationConsumerService contractNegotiationConsumerService;
    @Mock
    private ContractNegotiationProperties properties;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void handleContractOfferMessage_success() throws JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE_INITIAL);
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.handleContractOfferMessage(any(ContractOfferMessage.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED);
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        ResponseEntity<JsonNode> response = controller.handleContractOfferMessage(jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleContractOfferMessageAsCounteroffer_success() throws JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE_INITIAL);
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.handleContractOfferMessageAsCounteroffer(anyString(),any(ContractOfferMessage.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED);
        ResponseEntity<JsonNode> response = controller.handleContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleContractAgreement_Message_success() throws JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE);
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.handleContractAgreementMessage(anyString(), any(ContractAgreementMessage.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED);;

        ResponseEntity<Void> response = controller.handleContractAgreementMessage(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
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
                controller.handleContractAgreementMessage(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode));
    }

    @Test
    public void handleContractNegotiationEvent_MessageFinalize_success() throws JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE);
        JsonNode jsonNode = mapper.readTree(json);
        doNothing().when(contractNegotiationConsumerService).handleContractNegotiationEventMessageFinalize(anyString(), any(ContractNegotiationEventMessage.class));

        ResponseEntity<Void> response = controller.handleContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNull(response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleContractNegotiationEvent_MessageFinalize_failed() {
        JsonNode jsonNode = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE);

        doThrow(new ContractNegotiationInvalidStateException("Something not correct - tests", NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID))
                .when(contractNegotiationConsumerService).handleContractNegotiationEventMessageFinalize(anyString(), any(ContractNegotiationEventMessage.class));

        assertThrows(ContractNegotiationInvalidStateException.class, () ->
                controller.handleContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode));
    }

    @Test
    public void handleContractNegotiationTerminationMessage() {
        JsonNode jsonNode = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.TERMINATION_MESSAGE);

        ResponseEntity<JsonNode> response = controller.handleContractNegotiationTerminationMessage(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleContractNegotiationTerminationMessage_error_service() {
        JsonNode jsonNode = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.TERMINATION_MESSAGE);
        doThrow(ContractNegotiationNotFoundException.class).when(contractNegotiationConsumerService)
                .handleContractNegotiationTerminationMessage(any(String.class), any(ContractNegotiationTerminationMessage.class));
        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.handleContractNegotiationTerminationMessage(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode));
    }
}
