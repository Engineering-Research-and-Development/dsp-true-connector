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
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.IConstants;

public class ContractNegotiationVerifiedIntegrationTest extends BaseIntegrationTest {

//@PostMapping(path = "/{providerPid}/agreement/verification")
//Provider must return an HTTP code 200 (OK). The response body is not specified and clients are not required to process it.
//AGREED->VERIFIED
	
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
	public void handleVerifyAgreementTest() throws Exception {
		
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
    	
		ContractNegotiation contractNegotiationVerified = ContractNegotiation.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.callbackAddress("callbackAddress.test")
    			.offer(offer)
    			.agreement(agreement)
    			.state(ContractNegotiationState.AGREED)
    			.role(IConstants.ROLE_CONSUMER)
    			.build();
		contractNegotiationRepository.save(contractNegotiationVerified);
		
		ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder
				.newInstance()
				.consumerPid(contractNegotiationVerified.getConsumerPid())
				.providerPid(contractNegotiationVerified.getProviderPid())
				.build();

		final ResultActions result = mockMvc
				.perform(post("/negotiations/" + contractNegotiationVerified.getProviderPid() + "/agreement/verification")
						.content(NegotiationSerializer.serializeProtocol(verificationMessage))
						.contentType(MediaType.APPLICATION_JSON));
		result.andExpect(status().isOk());

		JsonNode contractNegotiation = getContractNegotiationOverAPI();
		ContractNegotiation contractNegotiationResponse = NegotiationSerializer
				.deserializePlain(contractNegotiation.toPrettyString(), ContractNegotiation.class);
		assertEquals(ContractNegotiationState.VERIFIED, contractNegotiationResponse.getState());
		offerCheck(contractNegotiationResponse, CatalogMockObjectUtil.OFFER.getId());
		agreementCheck(contractNegotiationResponse);
	}
	
	@Test
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void handleVerifyAgreement_invalid_state() throws Exception {
		
		ContractNegotiation contractNegotiationVerified = ContractNegotiation.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.callbackAddress("callbackAddress.test")
    			.state(ContractNegotiationState.REQUESTED)
    			.role(IConstants.ROLE_CONSUMER)
    			.build();
		contractNegotiationRepository.save(contractNegotiationVerified);
		
		ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder
				.newInstance()
				.consumerPid(contractNegotiationVerified.getConsumerPid())
				.providerPid(contractNegotiationVerified.getProviderPid())
				.build();

		final ResultActions result = mockMvc
				.perform(post("/negotiations/" + contractNegotiationVerified.getProviderPid() + "/agreement/verification")
						.content(NegotiationSerializer.serializeProtocol(verificationMessage))
						.contentType(MediaType.APPLICATION_JSON));
		result.andExpect(status().isBadRequest());
	}
	
	@Test
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void handleVerifyAgreement_not_found() throws Exception {
		ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder
				.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.build();

		final ResultActions result = mockMvc
				.perform(post("/negotiations/" + verificationMessage.getProviderPid() + "/agreement/verification")
						.content(NegotiationSerializer.serializeProtocol(verificationMessage))
						.contentType(MediaType.APPLICATION_JSON));
		result.andExpect(status().isNotFound());
	}
}
