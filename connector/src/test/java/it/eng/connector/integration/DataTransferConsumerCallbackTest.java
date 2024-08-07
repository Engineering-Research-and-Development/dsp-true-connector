package it.eng.connector.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.Serializer;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.model.TransferTerminationMessage;

/**
 * Data Transfer integration test - callback consumer endpoints
 */
public class DataTransferConsumerCallbackTest extends BaseIntegrationTest {

	@Test
	@DisplayName("Terminate transfer process - consumer")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void termianteTransferProcess_consumer() throws Exception {
		String consumerPid = "urn:uuid:CONSUMER_PID_TRANSFER_C_TERM";
		String providerPid = "urn:uuid:PROVIDER_PID_TRANSFER_C_TERM";
		TransferTerminationMessage transferTerminationMessage = TransferTerminationMessage.Builder.newInstance()
	    		.consumerPid(consumerPid)
	    		.providerPid(providerPid)
	    		.code("1")
	    		.build();
		
		mockMvc.perform(
    					post("/consumer/transfers/" + consumerPid + "/termination")
    					.content(Serializer.serializeProtocol(transferTerminationMessage))
    					.contentType(MediaType.APPLICATION_JSON))
    					.andExpect(status().isOk())
				    	.andReturn();
		
		// check TransferProcess status for providerPid - not most elegant since checking on provider side
    	TransferProcess transferProcessTerminated = getTransferProcessForProviderPid(providerPid);
    	assertEquals(transferProcessTerminated.getState(), TransferState.TERMINATED);
	}
	
	private TransferProcess getTransferProcessForProviderPid(String providerPid)
			throws Exception, UnsupportedEncodingException, JsonMappingException, JsonProcessingException {
		MvcResult resultCompletedMessage = mockMvc.perform(
        			get("/transfers/" + providerPid)
        			.contentType(MediaType.APPLICATION_JSON))
    			.andExpect(status().isOk())
            	.andReturn();	
    	String jsonTransferProcessCompleted = resultCompletedMessage.getResponse().getContentAsString();
    	JsonNode jsonNodeCompleted = Serializer.serializeStringToProtocolJsonNode(jsonTransferProcessCompleted);
    	TransferProcess transferProcessCompleted = Serializer.deserializeProtocol(jsonNodeCompleted, TransferProcess.class);
		return transferProcessCompleted;
	}
}
