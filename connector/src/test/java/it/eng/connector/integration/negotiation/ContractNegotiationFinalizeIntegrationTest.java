package it.eng.connector.integration.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.controller.ApiEndpoints;

public class ContractNegotiationFinalizeIntegrationTest extends BaseIntegrationTest {
	
// VERIFIED->FINALIZED
// https://consumer.com/:callback/negotiations/:consumerPid/events	POST	ContractNegotiationEventMessage
// @PostMapping("/consumer/negotiations/{consumerPid}/events")
    
	@Autowired
	private ContractNegotiationRepository contractNegotiationRepository;
	@Autowired
	private AgreementRepository agreementRepository;
	@Autowired
	private OfferRepository offerRepository;
	
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleFinalizeEventTest() throws Exception {
    	
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
    			.originalId(offerID)
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
    			.agreement(agreement)
    			.offer(offer)
    			.state(ContractNegotiationState.VERIFIED)
    			.role("consumer")
    			.build();
    	
    	contractNegotiationRepository.save(contractNegotiationVerified);
    	
		ContractNegotiationEventMessage  contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
				.consumerPid(contractNegotiationVerified.getConsumerPid())
				.providerPid(contractNegotiationVerified.getProviderPid())
				.eventType(ContractNegotiationEventType.FINALIZED)
				.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/negotiations/" + contractNegotiationVerified.getConsumerPid() + "/events")
    					.content(NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	// no response required
    	result.andExpect(status().isOk());
    	
    	JsonNode contractNegotiation = getContractNegotiationOverAPI();
		ContractNegotiation contractNegotiationFinalized = NegotiationSerializer.deserializePlain(contractNegotiation.toPrettyString(), ContractNegotiation.class);
		assertEquals(ContractNegotiationState.FINALIZED, contractNegotiationFinalized.getState());
		offerCheck(contractNegotiationFinalized);
		agreementCheck(contractNegotiationFinalized);
    	
    	// must wait for event that creates initial transfer process is completed
    	TimeUnit.SECONDS.sleep(1);
    	
    	//check if Transfer Process is initialized
    	final ResultActions tp =
				mockMvc.perform(
						get(ApiEndpoints.TRANSFER_DATATRANSFER_V1)
						.with(user(TestUtil.CONNECTOR_USER).password("password").roles("ADMIN"))
						.contentType(MediaType.APPLICATION_JSON));
		
		tp.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		JsonNode jsonNode = jsonMapper.readTree(tp.andReturn().getResponse().getContentAsString());
		TransferProcess transferProcess = TransferSerializer.deserializePlain(jsonNode.findValues("data").get(0).get(jsonNode.findValues("data").get(0).size()-1).toString(), TransferProcess.class);
		
		assertEquals(TransferState.INITIALIZED, transferProcess.getState());
		assertNotNull(transferProcess.getAgreementId());
		assertNotNull(transferProcess.getCallbackAddress());
		assertNotNull(transferProcess.getRole());
		assertNotNull(transferProcess.getDatasetId());
		
		agreementRepository.delete(agreement);
		offerRepository.delete(offer);
		contractNegotiationRepository.deleteById(contractNegotiationVerified.getId());
    }
    
    @Test
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void handleFinalizeEventTest_negotiation_not_found() throws Exception {
    	
    	ContractNegotiationEventMessage  contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.eventType(ContractNegotiationEventType.FINALIZED)
				.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/negotiations/" + contractNegotiationEventMessage.getConsumerPid() + "/events")
    					.content(NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isNotFound());
		
    	result.andExpect(status().isNotFound());
	}
    
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleFinalizeEventTest_invalid_state() throws Exception {
    	Agreement agreement = Agreement.Builder.newInstance()
    			.assignee("assignee")
    			.assigner("assigner")
    			.target("test_dataset")
    			.permission(Arrays.asList(Permission.Builder.newInstance()
    					.action(Action.USE)
    					.constraint(Arrays.asList(Constraint.Builder.newInstance()
    							.leftOperand(LeftOperand.COUNT)
    							.operator(Operator.LTEQ)
    							.rightOperand("5")
    							.build()))
    					.build()))
    			.build();
    	
    	ContractNegotiation contractNegotiationVerified = ContractNegotiation.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.agreement(agreement)
    			.state(ContractNegotiationState.AGREED)
    			.build();
    	
    	contractNegotiationRepository.save(contractNegotiationVerified);
    	
		ContractNegotiationEventMessage  contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
				.consumerPid(contractNegotiationVerified.getConsumerPid())
				.providerPid(contractNegotiationVerified.getProviderPid())
				.eventType(ContractNegotiationEventType.FINALIZED)
				.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/negotiations/" + contractNegotiationVerified.getConsumerPid() + "/events")
    					.content(NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());
    }
}
