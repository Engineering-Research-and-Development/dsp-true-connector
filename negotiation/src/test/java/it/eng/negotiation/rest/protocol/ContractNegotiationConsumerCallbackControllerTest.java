package it.eng.negotiation.rest.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
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

import java.util.concurrent.ExecutionException;

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
    public void handleNegotiationOffers() throws JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE);
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.handleContractOfferMessage(null, any(ContractOfferMessage.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED);
        when(properties.providerCallbackAddress()).thenReturn(NegotiationMockObjectUtil.CALLBACK_ADDRESS);
        ResponseEntity<JsonNode> response = controller.handleNegotiationOffers(jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleNegotiationOfferConsumerPid() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE);
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.processContractOffer(any(ContractOfferMessage.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED);
        ResponseEntity<JsonNode> response = controller.handleNegotiationOfferConsumerPid(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleAgreement_success() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE);
        JsonNode jsonNode = mapper.readTree(json);
        doNothing().when(contractNegotiationConsumerService).handleContractAgreementMessage(any(ContractAgreementMessage.class));

        ResponseEntity<Void> response = controller.handleAgreement(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        verify(contractNegotiationConsumerService).handleContractAgreementMessage(any(ContractAgreementMessage.class));
    }

    @Test
    public void handleAgreement_failed() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE);
        JsonNode jsonNode = mapper.readTree(json);

        doThrow(new ContractNegotiationInvalidStateException("Something not correct - tests", NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID))
                .when(contractNegotiationConsumerService).handleContractAgreementMessage(any(ContractAgreementMessage.class));

        assertThrows(ContractNegotiationInvalidStateException.class, () ->
                controller.handleAgreement(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode));
    }

    @Test
    public void handleFinalizeEvent_success() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
        String json = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE);
        JsonNode jsonNode = mapper.readTree(json);
        doNothing().when(contractNegotiationConsumerService).handleFinalizeEvent(any(ContractNegotiationEventMessage.class));

        ResponseEntity<Void> response = controller.handleFinalizeEvent(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNull(response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleFinalizeEvent_failed() {
        JsonNode jsonNode = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE);

        doThrow(new ContractNegotiationInvalidStateException("Something not correct - tests", NegotiationMockObjectUtil.CONSUMER_PID, NegotiationMockObjectUtil.PROVIDER_PID))
                .when(contractNegotiationConsumerService).handleFinalizeEvent(any(ContractNegotiationEventMessage.class));

        assertThrows(ContractNegotiationInvalidStateException.class, () ->
                controller.handleFinalizeEvent(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode));
    }

    @Test
    public void handleTerminationResponse() {
        JsonNode jsonNode = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.TERMINATION_MESSAGE);

        ResponseEntity<JsonNode> response = controller.handleTerminationResponse(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleTerminationResponse_error_service() {
        JsonNode jsonNode = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.TERMINATION_MESSAGE);
        doThrow(ContractNegotiationNotFoundException.class).when(contractNegotiationConsumerService)
                .handleTerminationRequest(any(String.class), any(ContractNegotiationTerminationMessage.class));
        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.handleTerminationResponse(NegotiationMockObjectUtil.CONSUMER_PID, jsonNode));
    }
}
