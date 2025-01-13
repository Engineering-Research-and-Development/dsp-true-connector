package it.eng.connector.integration.negotiation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.serializer.NegotiationSerializer;

@TestMethodOrder(OrderAnnotation.class)
public class NegotiationIntegrationTest extends BaseIntegrationTest {
	
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
	    	.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
      	String response = result.andReturn().getResponse().getContentAsString();
      	ContractNegotiationErrorMessage contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
    	assertNotNull(contractNegotiationErrorMessage);
    }
    
    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void noNegotiationFoundTests() throws Exception {
    	
    	final ResultActions result =
    			mockMvc.perform(
    					get("/negotiations/1")
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isNotFound())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
     	String response = result.andReturn().getResponse().getContentAsString();
      	ContractNegotiationErrorMessage contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
    	assertNotNull(contractNegotiationErrorMessage);
    }

    @Test
    public void contractNegotiation_notAuthorized() throws Exception {
    	final ResultActions result =
    			mockMvc.perform(
    					get("/negotiations/1")
    					.contentType(MediaType.APPLICATION_JSON)
    					.header("Authorization", "Basic YXNkckBtYWlsLmNvbTpwYXNzd29yZA=="));
    	result.andExpect(status().isUnauthorized())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	String response = result.andReturn().getResponse().getContentAsString();
      	ContractNegotiationErrorMessage contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
    	assertNotNull(contractNegotiationErrorMessage);
    	}
}
