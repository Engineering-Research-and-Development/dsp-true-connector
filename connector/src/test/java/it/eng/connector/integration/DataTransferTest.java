package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.Serializer;
import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.tools.model.DSpaceConstants;

public class DataTransferTest extends BaseIntegrationTest {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID";
	public static final String AGREEMENT_ID = "urn:uuid:" + UUID.randomUUID().toString();
	public static final String CALLBACK_ADDRESS = "https://example.com/callback";
	
	@Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void initiateDataTransfer() throws Exception {
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(CONSUMER_PID)
	    		.agreementId("urn:uuid:AGREEMENT_ID_OK")
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
	
	@Test
	@DisplayName("Start transfer process - from requested")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_requested() throws Exception {
		// from init_data.json
		String providerPid = "urn:uuid:PROVIDER_PID_TRANSFER_REQ";
		String consumerPid = "urn:uuid:CONSUMER_PID_TRANSFER_REQ";
	      
		TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.build();
		
		String body = Serializer.serializeProtocol(transferStartMessage);
		
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + providerPid +"/start")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    	
    	ResultActions transferProcessStarted = mockMvc.perform(
    			get("/transfers/" + providerPid).contentType(MediaType.APPLICATION_JSON));
    	// check if status is STARTED
    	transferProcessStarted.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
    			is(DSpaceConstants.DSPACE + TransferProcess.class.getSimpleName())))
    	.andExpect(jsonPath("['" + DSpaceConstants.DSPACE_STATE + "']", is(TransferState.STARTED.toString())))
    	.andExpect(jsonPath("['" + DSpaceConstants.CONTEXT + "']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    	
    	// try again to start - error
		mockMvc.perform(
				post("/transfers/" + providerPid +"/start")
				.content(body)
				.contentType(MediaType.APPLICATION_JSON))
		.andExpect(err -> assertTrue(err.getResolvedException() instanceof TransferProcessInvalidStateException))
		.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
    			is(DSpaceConstants.DSPACE + TransferError.class.getSimpleName())))
    	.andExpect(jsonPath("['" + DSpaceConstants.CONTEXT + "']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
	}
	
	@Test
	@DisplayName("Start transfer process - from suspended")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_suspended() throws Exception {
		// from init_data.json
		String providerPid = "urn:uuid:PROVIDER_PID_TRANSFER_SUSP";
		String consumerPid = "urn:uuid:CONSUMER_PID_TRANSFER_SUSP";
	      
		TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.build();
		
		String body = Serializer.serializeProtocol(transferStartMessage);
		
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + providerPid +"/start")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    	
    	ResultActions transferProcessStarted = mockMvc.perform(
    			get("/transfers/" + providerPid).contentType(MediaType.APPLICATION_JSON));
    	// check if status is STARTED
    	transferProcessStarted.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
    			is(DSpaceConstants.DSPACE + TransferProcess.class.getSimpleName())))
    	.andExpect(jsonPath("['" + DSpaceConstants.DSPACE_STATE + "']", is(TransferState.STARTED.toString())))
    	.andExpect(jsonPath("['" + DSpaceConstants.CONTEXT + "']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
	}
	
	// consumer callback start transfer
	@Test
	@DisplayName("Start transfer process - consumer callback")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_requested_consumer() throws Exception {
		// from init_data.json
		String providerPid = "urn:uuid:PROVIDER_PID_TRANSFER_C_REQ";
		String consumerPid = "urn:uuid:CONSUMER_PID_TRANSFER_C_REQ";
	      
		TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.build();
		
		String body = Serializer.serializeProtocol(transferStartMessage);
		// sends using providerPid in URL - bad request
		ResultActions error =
				mockMvc.perform(
						post("/consumer/transfers/" + providerPid +"/start")
						.content(body)
						.contentType(MediaType.APPLICATION_JSON));
		error.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
	    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
	    			is(DSpaceConstants.DSPACE + TransferError.class.getSimpleName())))
	    	.andExpect(jsonPath("['" + DSpaceConstants.CONTEXT + "']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
		
		final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/transfers/" + consumerPid +"/start")
    					.content(body)
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    	
    	// TODO when API logic for provider sends start message, uncomment this code to check on PROVIDER side
    	// if status is updated once consumer returns 200 OK
    	/*
    	ResultActions transferProcessStarted = mockMvc.perform(
    			get("/transfers/" + providerPid).contentType(MediaType.APPLICATION_JSON));
    	// check if status is STARTED
    	transferProcessStarted.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
    			is(DSpaceConstants.DSPACE + TransferProcess.class.getSimpleName())))
    	.andExpect(jsonPath("['" + DSpaceConstants.DSPACE_STATE + "']", is(TransferState.STARTED.toString())))
    	.andExpect(jsonPath("['" + DSpaceConstants.CONTEXT + "']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
    	*/
	}
}
