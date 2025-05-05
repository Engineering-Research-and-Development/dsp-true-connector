package it.eng.connector.integration.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.Reason;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.IConstants;

public class ContractNegotiationTerminateIntegrationTest extends BaseIntegrationTest {

	private static final String CALLBACK_ADDRESS = "http://localhost:8080/consumer";

	private static final String INVALID_PID = "INVALID_PID";
	
	@Autowired
	private ContractNegotiationRepository contractNegotiationRepository;

	@AfterEach
	public void cleanup() {
		contractNegotiationRepository.deleteAll();
	}
	
    @Test
    @DisplayName("Provider endpoint terminate negotiation - success")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void providerTerminateNegotiation_success() throws Exception {
    	ContractNegotiation cn = createContractNegotiation();
    	
    	ContractNegotiationTerminationMessage terminaionMessage = ContractNegotiationTerminationMessage.Builder.newInstance()
    			.consumerPid(cn.getConsumerPid())
    			.providerPid(cn.getProviderPid())
    			.code("test")
    			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("test").build()))
    			.build();
    	String body = NegotiationSerializer.serializeProtocol(terminaionMessage);
    	
    	//negotiations/:id/termination resource
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/" + cn.getProviderPid() + "/termination")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	// result is 200 OK
    	result.andExpect(status().isOk());
    	// Spring does not set content type when there is no body - if DSP requires it, uncomment
//    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    @Test
    @DisplayName("Provider endpoint terminate negotiation - wrong Pid")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void providerTerminateNegotiation_error() throws Exception {
    	ContractNegotiation cn = createContractNegotiation();
    	
    	ContractNegotiationTerminationMessage terminaionMessage = ContractNegotiationTerminationMessage.Builder.newInstance()
    			.consumerPid(cn.getConsumerPid())
    			.providerPid(cn.getProviderPid())
    			.code("test")
    			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("test").build()))
    			.build();
    	String body = NegotiationSerializer.serializeProtocol(terminaionMessage);
    	
    	//negotiations/:id/termination resource
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations/" + INVALID_PID + "/termination")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	// result is 400 error
    	result.andExpect(status().is4xxClientError())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	ContractNegotiationErrorMessage erroMessage = NegotiationSerializer.deserializeProtocol(result.andReturn().getResponse().getContentAsString(), 
    			ContractNegotiationErrorMessage.class);
    	assertNotNull(erroMessage);
    	assertEquals(cn.getConsumerPid(), erroMessage.getConsumerPid());
    	assertEquals(INVALID_PID, erroMessage.getProviderPid());
    }

    @Test
    @DisplayName("Consumer callback endpoint terminate negotiation - success")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void consumerTerminateNegotiation_success() throws Exception {
    	ContractNegotiation cn = createContractNegotiation();
    	
    	ContractNegotiationTerminationMessage terminaionMessage = ContractNegotiationTerminationMessage.Builder.newInstance()
    			.consumerPid(cn.getConsumerPid())
    			.providerPid(cn.getProviderPid())
    			.code("test")
    			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("test").build()))
    			.build();
    	String body = NegotiationSerializer.serializeProtocol(terminaionMessage);
    	
    	// /consumer/negotiations/{consumerPid}/termination
    	final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/negotiations/" + cn.getConsumerPid() + "/termination")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	// result is 200 OK
    	result.andExpect(status().isOk());
//    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    @Test
    @DisplayName("Consumer callback endpoint terminate negotiation - invalid Pid")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void consumerTerminateNegotiation_error() throws Exception {
    	ContractNegotiation cn = createContractNegotiation();
    	
    	ContractNegotiationTerminationMessage terminaionMessage = ContractNegotiationTerminationMessage.Builder.newInstance()
    			.consumerPid(cn.getConsumerPid())
    			.providerPid(cn.getProviderPid())
    			.code("test")
    			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("test").build()))
    			.build();
    	String body = NegotiationSerializer.serializeProtocol(terminaionMessage);
    	
    	// /consumer/negotiations/{consumerPid}/termination
    	final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/negotiations/" + INVALID_PID + "/termination")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	// result is 400 error
    	result.andExpect(status().is4xxClientError())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	ContractNegotiationErrorMessage erroMessage = NegotiationSerializer.deserializeProtocol(result.andReturn().getResponse().getContentAsString(), 
    			ContractNegotiationErrorMessage.class);
    	assertNotNull(erroMessage);
    	assertEquals(INVALID_PID, erroMessage.getConsumerPid());
    	assertEquals(cn.getProviderPid(), erroMessage.getProviderPid());
    }
    
	private ContractNegotiation createContractNegotiation() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.callbackAddress(CALLBACK_ADDRESS)
    			.role(IConstants.ROLE_PROVIDER)
    			.state(ContractNegotiationState.REQUESTED)
    			.build();
    	contractNegotiationRepository.save(cn);
		return cn;
	}
	
   
}
