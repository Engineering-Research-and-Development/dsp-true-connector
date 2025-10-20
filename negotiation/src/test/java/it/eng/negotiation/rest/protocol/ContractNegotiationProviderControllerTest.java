package it.eng.negotiation.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.ProviderPidNotBlankException;
import it.eng.negotiation.model.*;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationProviderStrategy;
import it.eng.tools.model.DSpaceConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationProviderControllerTest {

    @Mock
    private Environment environment;
    @Mock
    private ContractNegotiationProviderStrategy contractNegotiationService;
    @Mock
    private ServletRequestAttributes attrs;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ContractNegotiationProviderController controller;

    @BeforeEach
    public void before() {
        RequestContextHolder.setRequestAttributes(attrs);
    }

    private ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
            .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
            .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
            .state(ContractNegotiationState.REQUESTED)
            .build();

    @Test
    public void getNegotiationByProviderPid_success() {
        when(contractNegotiationService.getNegotiationByProviderPid(NegotiationMockObjectUtil.PROVIDER_PID))
                .thenReturn(contractNegotiation);
        ResponseEntity<JsonNode> response = controller.getNegotiationByProviderPid(NegotiationMockObjectUtil.PROVIDER_PID);
        assertNotNull(response, "Response is not null");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(response.getBody().get(DSpaceConstants.TYPE).asText(), ContractNegotiation.class.getSimpleName());
        assertEquals(response.getBody().get(DSpaceConstants.CONTEXT).get(0).asText(), DSpaceConstants.DSPACE_2025_01_CONTEXT);
    }

    @Test
    public void getNegotiationByProviderPid_failed() {
        when(contractNegotiationService.getNegotiationByProviderPid(NegotiationMockObjectUtil.PROVIDER_PID))
                .thenThrow(ContractNegotiationNotFoundException.class);

        assertThrows(ContractNegotiationNotFoundException.class, () -> controller.getNegotiationByProviderPid(NegotiationMockObjectUtil.PROVIDER_PID));
    }

    @Test
    public void createNegotiation_success() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(attrs.getRequest()).thenReturn(request);
        when(contractNegotiationService.handleInitialContractRequestMessage(any(ContractRequestMessage.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED);

        ResponseEntity<JsonNode> response = controller.createNegotiation(NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE));

        assertNotNull(response, "Response is not null");
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(response.getBody().get(DSpaceConstants.TYPE).asText(), ContractNegotiation.class.getSimpleName());
        assertEquals(response.getBody().get(DSpaceConstants.CONTEXT).get(0).asText(), DSpaceConstants.DSPACE_2025_01_CONTEXT);
    }

    @Test
    public void createNegotiation_failed() {
        when(contractNegotiationService.handleInitialContractRequestMessage(any(ContractRequestMessage.class)))
                .thenThrow(ProviderPidNotBlankException.class);

        assertThrows(ProviderPidNotBlankException.class, () -> controller.createNegotiation(NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE)));
    }

    @Test
    public void handleConsumerMakesOffer_success() {
        ResponseEntity<JsonNode> response = controller.handleConsumerMakesOffer(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE));
        assertNotNull(response, "Response is not null");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    public void handleNegotiationEventMessage_success() {
        when(contractNegotiationService.handleContractNegotiationEventMessage(any(ContractNegotiationEventMessage.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED);
        ResponseEntity<JsonNode> response = controller
                .handleNegotiationEventMessage(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE_ACCEPTED));
        assertNotNull(response, "Response is not null");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void handleVerifyAgreement_success() {
        ContractAgreementVerificationMessage cavm = ContractAgreementVerificationMessage.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .build();
        ResponseEntity<Void> response = controller.handleVerifyAgreement(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializeProtocolJsonNode(cavm));
        assertNotNull(response, "Response is not null");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void handleVerifyAgreement_failed() {
        doThrow(ContractNegotiationNotFoundException.class)
                .when(contractNegotiationService).verifyNegotiation(any(ContractAgreementVerificationMessage.class));

        assertThrows(ContractNegotiationNotFoundException.class, () -> controller.handleVerifyAgreement(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE)));
    }

    @Test
    public void handleTerminationMessage_success() {
        ContractNegotiationTerminationMessage cntm = ContractNegotiationTerminationMessage.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .code("1")
                .reason(Collections.singletonList("test"))
                .build();
        ResponseEntity<Void> response = controller.handleTerminationMessage(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializeProtocolJsonNode(cntm));
        assertNotNull(response, "Response is not null");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void handleTerminationMessage_error() {
        ContractNegotiationTerminationMessage cntm = ContractNegotiationTerminationMessage.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .code("1")
                .reason(Collections.singletonList("test"))
                .build();
        doThrow(ContractNegotiationNotFoundException.class)
                .when(contractNegotiationService).handleTerminationRequest(anyString(), any(ContractNegotiationTerminationMessage.class));
        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.handleTerminationMessage(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationSerializer.serializeProtocolJsonNode(cntm)));
    }
}
