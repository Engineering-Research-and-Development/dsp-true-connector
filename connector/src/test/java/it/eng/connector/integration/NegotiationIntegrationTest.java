package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.negotiation.model.ModelUtil;
import it.eng.tools.model.DSpaceConstants;

@SpringBootTest
@AutoConfigureMockMvc
public class NegotiationIntegrationTest {
	
    @Autowired
    private MockMvc mockMvc;
    
    @ParameterizedTest
    @WithUserDetails("milisav@mail.com")
    @ValueSource(strings = {"/request", "/1/request", "/1/events", "/1/agreement/verification", "1/termination"})
//    probaj da parametrizujes test
//    posto je samo ovo razlika post("/negotiations/request")
    public void negotiationFailingTests(String path) throws Exception {
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/negotiations" + path)
    					.content("{\"some\":\"json\"}")
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().is4xxClientError())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(ModelUtil.CONTRACT_NEGOTIATION_ERROR_MESSAGE.getType())));
    }

}
