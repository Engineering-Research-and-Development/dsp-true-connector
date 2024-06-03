package it.eng.negotiation.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.service.ContractNegotiationConsumerService;

@ExtendWith(MockitoExtension.class)
public class ConsumerContractNegotiationCallbackControllerTest {

	@InjectMocks
    private ConsumerContractNegotiationCallbackController controller;

    @Mock
    private ContractNegotiationConsumerService contractNegotiationConsumerService;
    @Mock
    private ContractNegotiationProperties properties;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void handleNegotiationOffers() throws JsonProcessingException {
        String json = Serializer.serializeProtocol(createContractOfferMessage());
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.processContractOffer(any(ContractOfferMessage.class)))
                .thenReturn(Serializer.serializeProtocolJsonNode(ModelUtil.CONTRACT_NEGOTIATION_OFFERED));
        when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
        ResponseEntity<JsonNode> response = controller.handleNegotiationOffers(jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleNegotiationOfferConsumerPid() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
        String json = Serializer.serializeProtocol(createContractOfferMessage());
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.handleNegotiationOfferConsumer(any(String.class), any(ContractOfferMessage.class)))
                .thenReturn(jsonNode);

        ResponseEntity<JsonNode> response = controller.handleNegotiationOfferConsumerPid(ModelUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleAgreement() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
        String json = Serializer.serializeProtocol(contractAgreementMessage());
        JsonNode jsonNode = mapper.readTree(json);
        doNothing().when(contractNegotiationConsumerService).handleAgreement(any(ContractAgreementMessage.class));

        ResponseEntity<JsonNode> response = controller.handleAgreement(ModelUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        verify(contractNegotiationConsumerService).handleAgreement(any(ContractAgreementMessage.class));
    }

    @Test
    public void handleEventsResponse() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
        String json = Serializer.serializeProtocol(contractNegotiationEventMessage());
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.handleEventsResponse(any(String.class), any(ContractNegotiationEventMessage.class)))
                .thenReturn(null);

        ResponseEntity<JsonNode> response = controller.handleEventsMessage(ModelUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void handleTerminationResponse() throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException {
        String json = Serializer.serializeProtocol(contractNegotiationTerminationMessage());
        JsonNode jsonNode = mapper.readTree(json);
        when(contractNegotiationConsumerService.handleTerminationResponse(any(String.class), any(ContractNegotiationTerminationMessage.class)))
                .thenReturn((null));

        ResponseEntity<JsonNode> response = controller.handleTerminationResponse(ModelUtil.CONSUMER_PID, jsonNode);
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    private ContractAgreementMessage contractAgreementMessage() {
        return ContractAgreementMessage.Builder.newInstance()
                .providerPid(ModelUtil.PROVIDER_PID)
                .consumerPid(ModelUtil.CONSUMER_PID)
                .callbackAddress(ModelUtil.CALLBACK_ADDRESS)
                .agreement(ModelUtil.AGREEMENT)
                .build();
    }

    private ContractNegotiationEventMessage contractNegotiationEventMessage() {
        return ContractNegotiationEventMessage.Builder.newInstance()
                .providerPid(ModelUtil.PROVIDER_PID)
                .consumerPid(ModelUtil.CONSUMER_PID)
                .eventType(ContractNegotiationEventType.FINALIZED)
                .build();
    }

    private ContractNegotiationTerminationMessage contractNegotiationTerminationMessage() {
        return ContractNegotiationTerminationMessage.Builder.newInstance()
                .providerPid(ModelUtil.PROVIDER_PID)
                .consumerPid(ModelUtil.CONSUMER_PID)
                .build();
    }

    private ContractOfferMessage createContractOfferMessage() {
        return ContractOfferMessage.Builder.newInstance()
//				.consumerPid(ModelUtil.CONSUMER_PID) // optional; If the message includes a consumerPid property, the request will be associated with an existing CN.
                .providerPid(ModelUtil.PROVIDER_PID)
                .offer(ModelUtil.OFFER)
                .callbackAddress(ModelUtil.CALLBACK_ADDRESS)   // mandatory???
                .build();
    }
}
