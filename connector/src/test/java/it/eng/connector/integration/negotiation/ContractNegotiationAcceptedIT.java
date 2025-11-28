package it.eng.connector.integration.negotiation;

import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.*;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.IConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ContractNegotiationAcceptedIT extends BaseIntegrationTest {

// OFFERED->ACCEPTED
// https://provider.com/:callback/negotiations/:providerPid/events	POST	ContractNegotiationEventMessage
// @PostMapping("/negotiations/{providerPid}/events")

    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;
    @Autowired
    private AgreementRepository agreementRepository;
    @Autowired
    private OfferRepository offerRepository;

    @AfterEach
    public void cleanup() {
        contractNegotiationRepository.deleteAll();
        agreementRepository.deleteAll();
        offerRepository.deleteAll();
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleAcceptedEventTest() throws Exception {

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Collections.singletonList(Constraint.Builder.newInstance()
                        .leftOperand(LeftOperand.COUNT)
                        .operator(Operator.LTEQ)
                        .rightOperand("5")
                        .build()))
                .build();

        Offer offer = Offer.Builder.newInstance()
                .permission(Collections.singletonList(permission))
                .originalId(CatalogMockObjectUtil.OFFER.getId())
                .target("test_dataset")
                .assigner("assigner")
                .build();
        offerRepository.save(offer);

        ContractNegotiation contractNegotiationOffered = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress("callbackAddress.test")
                .offer(offer)
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_PROVIDER)
                .build();

        contractNegotiationRepository.save(contractNegotiationOffered);

        ContractNegotiationEventMessage contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
                .consumerPid(contractNegotiationOffered.getConsumerPid())
                .providerPid(contractNegotiationOffered.getProviderPid())
                .eventType(ContractNegotiationEventType.ACCEPTED)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/negotiations/" + contractNegotiationOffered.getProviderPid() + "/events")
                                .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage)))
                                .contentType(MediaType.APPLICATION_JSON));
        // no response required
        result.andExpect(status().isOk());

        ContractNegotiation contractNegotiationAccepted = getContractNegotiationOverAPI(
                contractNegotiationOffered.getConsumerPid(),
                contractNegotiationOffered.getProviderPid());

        // Verify contract negotiation exists and basic fields
        assertNotNull(contractNegotiationAccepted, "Contract negotiation should not be null");
        assertNotNull(contractNegotiationAccepted.getId(), "Contract negotiation ID should not be null");

        // Verify state transition
        assertEquals(ContractNegotiationState.ACCEPTED, contractNegotiationAccepted.getState(),
                "Contract negotiation state should be ACCEPTED");

        // Verify PIDs
        assertEquals(contractNegotiationOffered.getConsumerPid(), contractNegotiationAccepted.getConsumerPid(),
                "Consumer PID should match");
        assertEquals(contractNegotiationOffered.getProviderPid(), contractNegotiationAccepted.getProviderPid(),
                "Provider PID should match");

        // Verify role
        assertEquals(IConstants.ROLE_PROVIDER, contractNegotiationAccepted.getRole(),
                "Role should be PROVIDER");

        // Verify callback address is preserved
        assertEquals(contractNegotiationOffered.getCallbackAddress(), contractNegotiationAccepted.getCallbackAddress(),
                "Callback address should be preserved");

        // Verify offer details
        assertNotNull(contractNegotiationAccepted.getOffer(), "Offer should not be null");
        offerCheck(contractNegotiationAccepted, CatalogMockObjectUtil.OFFER.getId());
        assertEquals(offer.getTarget(), contractNegotiationAccepted.getOffer().getTarget(),
                "Offer target should match");
        assertEquals(offer.getAssigner(), contractNegotiationAccepted.getOffer().getAssigner(),
                "Offer assigner should match");

        // Verify permissions are preserved
        assertNotNull(contractNegotiationAccepted.getOffer().getPermission(), "Permissions should not be null");
        assertEquals(1, contractNegotiationAccepted.getOffer().getPermission().size(),
                "Should have one permission");

        Permission savedPermission = contractNegotiationAccepted.getOffer().getPermission().get(0);
        assertEquals(Action.USE, savedPermission.getAction(), "Permission action should be USE");
        assertNotNull(savedPermission.getConstraint(), "Permission constraints should not be null");
        assertEquals(1, savedPermission.getConstraint().size(), "Should have one constraint");

        Constraint savedConstraint = savedPermission.getConstraint().get(0);
        assertEquals(LeftOperand.COUNT, savedConstraint.getLeftOperand(), "Constraint left operand should be COUNT");
        assertEquals(Operator.LTEQ, savedConstraint.getOperator(), "Constraint operator should be LTEQ");
        assertEquals("5", savedConstraint.getRightOperand(), "Constraint right operand should be 5");
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleAcceptedEventTest_negotiation_not_found() throws Exception {

        ContractNegotiationEventMessage contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .eventType(ContractNegotiationEventType.ACCEPTED)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/negotiations/" + contractNegotiationEventMessage.getProviderPid() + "/events")
                                .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage)))
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isNotFound());

        // Verify error response contains meaningful information
        String responseContent = result.andReturn().getResponse().getContentAsString();
        assertNotNull(responseContent, "Error response should not be empty");
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleAcceptedEventTest_invalid_state() throws Exception {
        ContractNegotiation contractNegotiationRequested = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .state(ContractNegotiationState.ACCEPTED)
                .role(IConstants.ROLE_PROVIDER)
                .callbackAddress("http://callback.test")
                .build();

        contractNegotiationRepository.save(contractNegotiationRequested);

        ContractNegotiationEventMessage contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
                .consumerPid(contractNegotiationRequested.getConsumerPid())
                .providerPid(contractNegotiationRequested.getProviderPid())
                .eventType(ContractNegotiationEventType.ACCEPTED)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/negotiations/" + contractNegotiationRequested.getProviderPid() + "/events")
                                .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage)))
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isBadRequest());

        // Verify error response contains meaningful information
        String responseContent = result.andReturn().getResponse().getContentAsString();
        assertNotNull(responseContent, "Error response should not be empty");

        // Verify the state hasn't changed in the database
        ContractNegotiation unchangedNegotiation = contractNegotiationRepository
                .findByProviderPid(contractNegotiationRequested.getProviderPid())
                .orElseThrow();
        assertEquals(ContractNegotiationState.ACCEPTED, unchangedNegotiation.getState(),
                "State should remain ACCEPTED after failed transition attempt");
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleAcceptedEventTest_provider_pid_mismatch() throws Exception {
        ContractNegotiation contractNegotiationOffered = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_PROVIDER)
                .callbackAddress("http://callback.test")
                .build();

        contractNegotiationRepository.save(contractNegotiationOffered);

        ContractNegotiationEventMessage contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
                .consumerPid(contractNegotiationOffered.getConsumerPid())
                .providerPid(contractNegotiationOffered.getProviderPid())
                .eventType(ContractNegotiationEventType.ACCEPTED)
                .build();

        String wrongProviderPid = createNewId();
        final ResultActions result =
                mockMvc.perform(
                        post("/negotiations/" + wrongProviderPid + "/events")
                                .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage)))
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isNotFound());

        // Verify error response contains meaningful information
        String responseContent = result.andReturn().getResponse().getContentAsString();
        assertNotNull(responseContent, "Error response should not be empty");

        // Verify the state hasn't changed in the database
        ContractNegotiation unchangedNegotiation = contractNegotiationRepository
                .findByProviderPid(contractNegotiationOffered.getProviderPid())
                .orElseThrow();
        assertEquals(ContractNegotiationState.OFFERED, unchangedNegotiation.getState(),
                "State should remain OFFERED after failed request with mismatched PID");
    }
}

