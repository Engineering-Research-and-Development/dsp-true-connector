package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.model.DSpaceConstants;

@TestMethodOrder(OrderAnnotation.class)
public class NegotiationIntegrationTest extends BaseIntegrationTest {
	
	private static String providerPid;
	private final ObjectMapper mapper = new ObjectMapper();
	
	@Order(1)
    @ParameterizedTest
    @ValueSource(strings = {"/request", "/1/request", "/1/events", "/1/agreement/verification", "/1/termination"})
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void negotiationWrongMessageTests(String path) throws Exception {
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations" + path)
    					.content("{\"some\":\"json\"}")
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(ModelUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }

	@Order(2) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationTests() throws Exception {
    	
    	//needs to match offer in initial.data
    	Offer offer = Offer.Builder.newInstance()
    			.id("fdc45798-a123-4955-8baf-ab7fd66ac4d5")
    			.target(ModelUtil.TARGET)
    			.permission(Arrays.asList(ModelUtil.PERMISSION_COUNT_5))
    			.build();
    	
    	ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
    			.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
    			.consumerPid(ModelUtil.CONSUMER_PID)
    			.offer(offer)
    			.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/request")
    					.content(Serializer.serializeProtocol(contractRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isCreated())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    	
    	JsonNode jsonNode = mapper.readTree(result.andReturn().getResponse().getContentAsString());
    	providerPid = jsonNode.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText();
    }
    
	@Order(3) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void negotiationExistsTests() throws Exception {
    	ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
		.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
		.consumerPid(TestUtil.CONSUMER_PID)
		.providerPid(TestUtil.PROVIDER_PID)
		.offer(ModelUtil.OFFER)
		.build();

    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/request")
    					.content(Serializer.serializeProtocol(crm))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(ModelUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
    
	@Order(4) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getNegotiationByProviderPidTests() throws Exception {
    	
    	final ResultActions result =
    			mockMvc.perform(
    					get("/negotiations/" + TestUtil.PROVIDER_PID)
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
    
	@Order(5) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void noNegotiationFoundTests() throws Exception {
    	
    	final ResultActions result =
    			mockMvc.perform(
    					get("/negotiations/1")
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isNotFound())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(ModelUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
    
	@Order(6) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleAgreementTest() throws Exception {
		
		ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(providerPid)
				.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
				.agreement(ModelUtil.AGREEMENT)
				.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/negotiations/" + ModelUtil.CONSUMER_PID + "/agreement")
    					.content(Serializer.serializeProtocol(agreementMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    }
	
	@Order(7) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleVerifyAgreementTest() throws Exception {
		
		ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(providerPid)
				.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/" + ModelUtil.PROVIDER_PID + "/agreement/verification")
    					.content(Serializer.serializeProtocol(verificationMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    }
	
	@Order(8) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleFinalizeEventTest() throws Exception {
		
		ContractNegotiationEventMessage  contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(providerPid)
				.eventType(ContractNegotiationEventType.FINALIZED)
				.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/negotiations/" + ModelUtil.CONSUMER_PID + "/events")
    					.content(Serializer.serializeProtocol(contractNegotiationEventMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    }

}
