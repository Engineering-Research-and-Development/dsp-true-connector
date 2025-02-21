package it.eng.connector.integration.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.IConstants;

public class ContractNegotiationAgreedIntegrationTest extends BaseIntegrationTest {

//	Consumer callback
//	@PostMapping("/consumer/negotiations/{consumerPid}/agreement")
//	REQUESTED->AGREED
	
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
	public void handleAgreementTest() throws Exception {
		
		Permission permission = Permission.Builder.newInstance()
    			.action(Action.USE)
    			.constraint(Arrays.asList(Constraint.Builder.newInstance()
    					.leftOperand(LeftOperand.COUNT)
    					.operator(Operator.LTEQ)
    					.rightOperand("5")
    					.build()))
    			.build();
    	
    	Offer offer = Offer.Builder.newInstance()
    			.permission(Arrays.asList(permission))
    			.originalId(CatalogMockObjectUtil.OFFER.getId())
    			.target("test_dataset")
    			.assigner("assigner")
    			.build();
    	offerRepository.save(offer);
    	
		Agreement agreement = Agreement.Builder.newInstance()
    			.assignee("assignee")
    			.assigner("assigner")
    			.target("test_dataset")
    			.permission(Arrays.asList(permission))
    			.build();
    	agreementRepository.save(agreement);
    	
    	ContractNegotiation contractNegotiationRequested = ContractNegotiation.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.callbackAddress("callbackAddress.test")
    			.agreement(agreement)
    			.offer(offer)
    			.state(ContractNegotiationState.REQUESTED)
    			.role(IConstants.ROLE_CONSUMER)
    			.build();
    	
    	contractNegotiationRepository.save(contractNegotiationRequested);

		ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
				.consumerPid(contractNegotiationRequested.getConsumerPid())
				.providerPid(contractNegotiationRequested.getProviderPid())
				.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
				.agreement(agreement)
				.build();

		final ResultActions result = mockMvc
				.perform(post("/consumer/negotiations/" + contractNegotiationRequested.getConsumerPid() + "/agreement")
						.content(NegotiationSerializer.serializeProtocol(agreementMessage))
						.contentType(MediaType.APPLICATION_JSON));
		result.andExpect(status().isOk());

		JsonNode contractNegotiation = getContractNegotiationOverAPI();
		ContractNegotiation contractNegotiationAgreed = NegotiationSerializer.deserializePlain(contractNegotiation.toPrettyString(), ContractNegotiation.class);
		assertEquals(ContractNegotiationState.AGREED, contractNegotiationAgreed.getState());
		offerCheck(contractNegotiationAgreed, CatalogMockObjectUtil.OFFER.getId());
		agreementCheck(contractNegotiationAgreed);
	}
	
	@Test
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void handleAgreement_negotiation_not_found() throws Exception {
    	
    	ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
				.agreement(NegotiationMockObjectUtil.AGREEMENT).build();
    	
    	final ResultActions result = mockMvc
				.perform(post("/consumer/negotiations/" + agreementMessage.getConsumerPid() + "/agreement")
						.content(NegotiationSerializer.serializeProtocol(agreementMessage))
						.contentType(MediaType.APPLICATION_JSON));
		
    	result.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void handleAgreement_invalid_state() throws Exception {
		ContractNegotiation contractNegotiationRequested = ContractNegotiation.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.callbackAddress("callbackAddress.test")
    			.state(ContractNegotiationState.VERIFIED)
    			.role("consumer")
    			.build();
    	
    	contractNegotiationRepository.save(contractNegotiationRequested);
    	
    	ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
				.consumerPid(contractNegotiationRequested.getConsumerPid())
				.providerPid(contractNegotiationRequested.getProviderPid())
				.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
				.agreement(NegotiationMockObjectUtil.AGREEMENT).build();
    	
    	final ResultActions result = mockMvc
				.perform(post("/consumer/negotiations/" + contractNegotiationRequested.getConsumerPid() + "/agreement")
						.content(NegotiationSerializer.serializeProtocol(agreementMessage))
						.contentType(MediaType.APPLICATION_JSON));
		result.andExpect(status().isBadRequest());
	}
	
}
