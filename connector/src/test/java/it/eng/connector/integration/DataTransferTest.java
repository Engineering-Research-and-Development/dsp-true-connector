package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.Serializer;
import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.tools.model.DSpaceConstants;

public class DataTransferTest extends BaseIntegrationTest {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String AGREEMENT_ID = "urn:uuid:" + UUID.randomUUID().toString();
	public static final String CALLBACK_ADDRESS = "https://example.com/callback";
	
	@Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void initiateDataTransfer() throws Exception {
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(CONSUMER_PID)
	    		.agreementId(AGREEMENT_ID)
	    		.format("HTTP_PULL")
	    		.callbackAddress(CALLBACK_ADDRESS)
	    		.build();
		
    	String body = Serializer.serializeProtocol(transferRequestMessage);
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/request")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isCreated())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
    			is(DSpaceConstants.DSPACE + TransferProcess.class.getSimpleName())))
    	.andExpect(jsonPath("['" + DSpaceConstants.DSPACE_STATE + "']", is(TransferState.REQUESTED.toString())))
    	.andExpect(jsonPath("['" + DSpaceConstants.CONTEXT + "']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
	
	@Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void initiateDataTransfer_agreement_exists() throws Exception {
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(CONSUMER_PID)
	    		.agreementId("urn:uuid:AGREEMENT_ID") // this one should be present in init_data.json
	    		.format("HTTP_PULL")
	    		.callbackAddress(CALLBACK_ADDRESS)
	    		.build();
		
    	String body = Serializer.serializeProtocol(transferRequestMessage);
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/request")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
    			is(DSpaceConstants.DSPACE + TransferError.class.getSimpleName())))
    	.andExpect(jsonPath("['" + DSpaceConstants.CONTEXT + "']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    }
}
