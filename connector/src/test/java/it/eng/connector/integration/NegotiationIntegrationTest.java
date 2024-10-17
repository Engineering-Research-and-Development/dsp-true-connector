package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.MockObjectUtil;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.DSpaceConstants;

@TestMethodOrder(OrderAnnotation.class)
public class NegotiationIntegrationTest extends BaseIntegrationTest {
	
	private static String providerPid;
	private static String offerID = "urn:uuid:fdc45798-a123-4955-8baf-ab7fd66ac4d5";
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
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }

	@Order(2) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationTests() throws Exception {
    	
    	//needs to match offer in initial.data
    	Offer offer = Offer.Builder.newInstance()
    			.id(offerID)
    			.target(MockObjectUtil.TARGET)
    			.assigner(MockObjectUtil.ASSIGNER)
    			.permission(Arrays.asList(MockObjectUtil.PERMISSION_COUNT_5))
    			.build();
    	
    	ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
    			.callbackAddress(MockObjectUtil.CALLBACK_ADDRESS)
    			.consumerPid(MockObjectUtil.CONSUMER_PID)
    			.offer(offer)
    			.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/request")
    					.content(Serializer.serializeProtocol(contractRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isCreated())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    	
    	JsonNode jsonNode = mapper.readTree(result.andReturn().getResponse().getContentAsString());
    	providerPid = jsonNode.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText();

    	//TODO add protocol call using providerPid
    	offerCheck(getContractNegotiationOverAPI());
    }

	
	@Order(3) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void negotiationExistsTests() throws Exception {
    	ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
			.callbackAddress(MockObjectUtil.CALLBACK_ADDRESS)
			.consumerPid(TestUtil.CONSUMER_PID)
			.providerPid(TestUtil.PROVIDER_PID)
			.offer(MockObjectUtil.OFFER)
			.build();

    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/request")
    					.content(Serializer.serializeProtocol(crm))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
    
	@Order(4) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getNegotiationByProviderPidTests() throws Exception {
    	// insert data into db
		Offer offer = Offer.Builder.newInstance()
    			.id(offerID)
    			.target(MockObjectUtil.TARGET)
    			.assigner(MockObjectUtil.ASSIGNER)
    			.permission(Arrays.asList(MockObjectUtil.PERMISSION_COUNT_5))
    			.build();
    	
    	ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
    			.callbackAddress(MockObjectUtil.CALLBACK_ADDRESS)
    			.consumerPid(MockObjectUtil.CONSUMER_PID)
    			.offer(offer)
    			.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/request")
    					.content(Serializer.serializeProtocol(contractRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isCreated())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    	
    	JsonNode jsonNode = mapper.readTree(result.andReturn().getResponse().getContentAsString());
    	providerPid = jsonNode.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText();
    	
		mockMvc.perform(
			get("/negotiations/" + providerPid)
			.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isCreated())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getType())))
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
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(MockObjectUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
    
	@Order(6) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleAgreementTest() throws Exception {
		
		ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
				.consumerPid(MockObjectUtil.CONSUMER_PID)
				.providerPid(providerPid)
				.callbackAddress(MockObjectUtil.CALLBACK_ADDRESS)
				.agreement(MockObjectUtil.AGREEMENT)
				.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/negotiations/" + MockObjectUtil.CONSUMER_PID + "/agreement")
    					.content(Serializer.serializeProtocol(agreementMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    	
    	JsonNode contractNegotiation = getContractNegotiationOverAPI();
    	offerCheck(contractNegotiation);
    	agreementCheck(contractNegotiation);
    }
	
	@Order(7) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleVerifyAgreementTest() throws Exception {
		
		ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
				.consumerPid(MockObjectUtil.CONSUMER_PID)
				.providerPid(providerPid)
				.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/" + MockObjectUtil.PROVIDER_PID + "/agreement/verification")
    					.content(Serializer.serializeProtocol(verificationMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    	
    	JsonNode contractNegotiation = getContractNegotiationOverAPI();
    	offerCheck(contractNegotiation);
    	agreementCheck(contractNegotiation);
    }
	
	@Order(8) 
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void handleFinalizeEventTest() throws Exception {
		
		ContractNegotiationEventMessage  contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
				.consumerPid(MockObjectUtil.CONSUMER_PID)
				.providerPid(providerPid)
				.eventType(ContractNegotiationEventType.FINALIZED)
				.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/negotiations/" + MockObjectUtil.CONSUMER_PID + "/events")
    					.content(Serializer.serializeProtocol(contractNegotiationEventMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    	
    	JsonNode contractNegotiation = getContractNegotiationOverAPI();
    	offerCheck(contractNegotiation);
    	agreementCheck(contractNegotiation);
    }

	private JsonNode getContractNegotiationOverAPI()
			throws Exception, JsonProcessingException, JsonMappingException, UnsupportedEncodingException {
		final ResultActions result =
				mockMvc.perform(
						get(ApiEndpoints.NEGOTIATION_V1)
						.with(user(TestUtil.CONNECTOR_USER).password("password").roles("ADMIN"))
						.contentType(MediaType.APPLICATION_JSON));
		
		result.andExpect(status().isOk())
		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		JsonNode jsonNode = mapper.readTree(result.andReturn().getResponse().getContentAsString());
		return jsonNode.findValues("data").get(0).get(jsonNode.findValues("data").get(0).size()-1);
	}
	
	private void offerCheck(JsonNode contractNegotiation) {
		assertEquals(offerID, contractNegotiation.get("offer").get("originalId").asText());
	}
	
	private void agreementCheck(JsonNode contractNegotiation) {
		assertNotNull(contractNegotiation.get("agreement"));
	}
}
