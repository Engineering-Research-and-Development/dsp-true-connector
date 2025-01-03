package it.eng.connector.integration.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.serializer.Serializer;

public class ContractNegotiationRequestedIntegrationTest extends BaseIntegrationTest {
// -> REQUESTED
//	@PostMapping(path = "/request")
	
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiation_success() throws Exception {
    	
    	//needs to match offer in initial.data
    	Offer offer = Offer.Builder.newInstance()
    			.id(offerID)
    			.target(NegotiationMockObjectUtil.TARGET)
    			.assigner(NegotiationMockObjectUtil.ASSIGNER)
    			.permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_COUNT_5))
    			.build();
    	
    	ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
    			.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
    			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
    			.offer(offer)
    			.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/request")
    					.content(Serializer.serializeProtocol(contractRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isCreated())
	    	.andExpect(content().contentType(MediaType.APPLICATION_JSON));
	    	
    	String response = result.andReturn().getResponse().getContentAsString();
    	ContractNegotiation contractNegotiationRequested = Serializer.deserializeProtocol(response, ContractNegotiation.class);
    	assertNotNull(contractNegotiationRequested);
    	assertEquals(ContractNegotiationState.REQUESTED, contractNegotiationRequested.getState());
    	
    	offerCheck(getContractNegotiationOverAPI(contractNegotiationRequested.getConsumerPid(), contractNegotiationRequested.getProviderPid()));
    }
    
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiation_negotiation_exists() throws Exception {
    	
    	ContractNegotiation contractNegotiationRequestd = ContractNegotiation.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.callbackAddress("callbackAddress.test")
    			.state(ContractNegotiationState.REQUESTED)
    			.build();
    	
    	ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
			.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
			.consumerPid(contractNegotiationRequestd.getConsumerPid())
			.offer(NegotiationMockObjectUtil.OFFER)
			.build();

    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/request")
    					.content(Serializer.serializeProtocol(crm))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	String response = result.andReturn().getResponse().getContentAsString();
    	ContractNegotiationErrorMessage errorMessage = Serializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
    	assertNotNull(errorMessage);
    }
    
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiation_invalid_offer() throws Exception {
    	
    	// offer with new UUID as ID, that does not exists in catalog
    	Offer offer = Offer.Builder.newInstance()
    			.target(NegotiationMockObjectUtil.TARGET)
    			.assigner(NegotiationMockObjectUtil.ASSIGNER)
    			.permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_COUNT_5))
    			.build();
    	
    	ContractNegotiation contractNegotiationRequestd = ContractNegotiation.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.callbackAddress("callbackAddress.test")
    			.state(ContractNegotiationState.REQUESTED)
    			.build();
    	
    	ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
			.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
			.consumerPid(contractNegotiationRequestd.getConsumerPid())
			.offer(offer)
			.build();

    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/request")
    					.content(Serializer.serializeProtocol(crm))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	String response = result.andReturn().getResponse().getContentAsString();
    	ContractNegotiationErrorMessage errorMessage = Serializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
    	assertNotNull(errorMessage);
    }
    
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiation_invalid_offer_constraint() throws Exception {
    	// offer with dateTime constraint - will not match with one in initial_data
    	Offer offer = Offer.Builder.newInstance()
    			.id(offerID)
    			.target(NegotiationMockObjectUtil.TARGET)
    			.assigner(NegotiationMockObjectUtil.ASSIGNER)
    			.permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION))
    			.build();
    	
    	ContractNegotiation contractNegotiationRequestd = ContractNegotiation.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.callbackAddress("callbackAddress.test")
    			.state(ContractNegotiationState.REQUESTED)
    			.build();
    	
    	ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
			.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
			.consumerPid(contractNegotiationRequestd.getConsumerPid())
			.offer(offer)
			.build();

    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/request")
    					.content(Serializer.serializeProtocol(crm))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	String response = result.andReturn().getResponse().getContentAsString();
    	ContractNegotiationErrorMessage errorMessage = Serializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
    	assertNotNull(errorMessage);
    }
}
