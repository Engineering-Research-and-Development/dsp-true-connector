package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.model.Serializer;
import it.eng.tools.model.DSpaceConstants;

@SpringBootTest
@AutoConfigureMockMvc
public class NegotiationIntegrationTest {
	
    @Autowired
    private MockMvc mockMvc;
    
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
    
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationTests() throws Exception {
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/request")
    					.content(Serializer.serializeProtocol(ModelUtil.CONTRACT_REQUEST_MESSAGE))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isCreated())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(ModelUtil.CONTRACT_NEGOTIATION.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
    
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
    
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getNegotiationByProviderPidTests() throws Exception {
    	
    	final ResultActions result =
    			mockMvc.perform(
    					get("/negotiations/" + TestUtil.PROVIDER_PID)
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(ModelUtil.CONTRACT_NEGOTIATION.getType())))
    	.andExpect(jsonPath("['"+DSpaceConstants.CONTEXT+"']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
    
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

}
