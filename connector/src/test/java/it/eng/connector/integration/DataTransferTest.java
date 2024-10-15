package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.model.TransferTerminationMessage;
import it.eng.datatransfer.serializer.Serializer;
import it.eng.tools.model.DSpaceConstants;

/**
 * Data Transfer integration test - provider endpoints
 */
public class DataTransferTest extends BaseIntegrationTest {
	
	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID";
	public static final String AGREEMENT_ID = "urn:uuid:" + UUID.randomUUID().toString();
	public static final String CALLBACK_ADDRESS = "https://example.com/callback";
	
	private final ObjectMapper mapper = new ObjectMapper();
	
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
    	.andExpect(jsonPath("['" + DSpaceConstants.DSPACE_STATE + "']", is(TransferState.REQUESTED.toString())));
    	
    	JsonNode jsonNode = mapper.readTree(result.andReturn().getResponse().getContentAsString());
		JsonNode contextNode = jsonNode.get(DSpaceConstants.CONTEXT);
		assertTrue(DSpaceConstants.validateContext(contextNode));
	}
	
	@Test
    @DisplayName("Start transfer - unauthorized")
    public void getCatalogSUnauthorizedTest() throws Exception {
    	
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
    					.contentType(MediaType.APPLICATION_JSON)
    					.header("Authorization", "Basic YXNkckBtYWlsLmNvbTpwYXNzd29yZA=="));
    	result.andExpect(status().isUnauthorized())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(DSpaceConstants.DSPACE + TransferError.class.getSimpleName())));

    	JsonNode jsonNode = mapper.readTree(result.andReturn().getResponse().getContentAsString());
		JsonNode contextNode = jsonNode.get(DSpaceConstants.CONTEXT);
		assertTrue(DSpaceConstants.validateContext(contextNode));
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
    			is(DSpaceConstants.DSPACE + TransferError.class.getSimpleName())));

    	JsonNode jsonNode = mapper.readTree(result.andReturn().getResponse().getContentAsString());
		JsonNode contextNode = jsonNode.get(DSpaceConstants.CONTEXT);
		assertTrue(DSpaceConstants.validateContext(contextNode));
	}
	
	@Test
	@DisplayName("Start transfer process - from requested")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_requested() throws Exception {
		// from init_data.json
		String providerPid = "urn:uuid:PROVIDER_PID_TRANSFER_REQ";
		String consumerPid = "urn:uuid:CONSUMER_PID_TRANSFER_REQ";
	      
		sendTransferStartMessage(consumerPid, providerPid)
			.andExpect(status().isOk());;
    	
    	ResultActions transferProcessStarted = mockMvc.perform(
    			get("/transfers/" + providerPid).contentType(MediaType.APPLICATION_JSON));
    	// check if status is STARTED
    	transferProcessStarted.andExpect(status().isOk())
	    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
	    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
	    			is(DSpaceConstants.DSPACE + TransferProcess.class.getSimpleName())))
	    	.andExpect(jsonPath("['" + DSpaceConstants.DSPACE_STATE + "']", is(TransferState.STARTED.toString())));
    	
    	JsonNode jsonNode = mapper.readTree(transferProcessStarted.andReturn().getResponse().getContentAsString());
		JsonNode contextNode = jsonNode.get(DSpaceConstants.CONTEXT);
		assertTrue(DSpaceConstants.validateContext(contextNode));
		
    	// try again to start - error
		sendTransferStartMessage(consumerPid, providerPid)
			.andExpect(err -> assertTrue(err.getResolvedException() instanceof TransferProcessInvalidStateException))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
	    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
	    			is(DSpaceConstants.DSPACE + TransferError.class.getSimpleName())));

		jsonNode = mapper.readTree(transferProcessStarted.andReturn().getResponse().getContentAsString());
		contextNode = jsonNode.get(DSpaceConstants.CONTEXT);
		assertTrue(DSpaceConstants.validateContext(contextNode));
	}
	
	@Test
	@DisplayName("Start transfer process - from suspended")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_suspended() throws Exception {
		// from init_data.json
		String providerPid = "urn:uuid:PROVIDER_PID_TRANSFER_SUSP";
		String consumerPid = "urn:uuid:CONSUMER_PID_TRANSFER_SUSP";
	      
		sendTransferStartMessage(consumerPid, providerPid)
			.andExpect(status().isOk());
    	
    	ResultActions transferProcessStarted = mockMvc.perform(
    			get("/transfers/" + providerPid).contentType(MediaType.APPLICATION_JSON));
    	// check if status is STARTED
    	transferProcessStarted.andExpect(status().isOk())
	    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
	    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
	    			is(DSpaceConstants.DSPACE + TransferProcess.class.getSimpleName())))
	    	.andExpect(jsonPath("['" + DSpaceConstants.DSPACE_STATE + "']", is(TransferState.STARTED.toString())));

    	JsonNode jsonNode = mapper.readTree(transferProcessStarted.andReturn().getResponse().getContentAsString());
		JsonNode contextNode = jsonNode.get(DSpaceConstants.CONTEXT);
		assertTrue(DSpaceConstants.validateContext(contextNode));
		
    	String transactionId = Base64.getEncoder().encodeToString((consumerPid + "|" + providerPid).getBytes(Charset.forName("UTF-8")));
    	downloadArifact(transactionId);
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
	    			is(DSpaceConstants.DSPACE + TransferError.class.getSimpleName())));

		JsonNode jsonNode = mapper.readTree(error.andReturn().getResponse().getContentAsString());
		JsonNode contextNode = jsonNode.get(DSpaceConstants.CONTEXT);
		assertTrue(DSpaceConstants.validateContext(contextNode));
		
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
	
	@Test
	@DisplayName("Complete transfer process - provider")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void completeTransferProcess_request_start_complete() throws Exception {
		String consumerPid = "urn:uuid" + UUID.randomUUID().toString();
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(consumerPid)
	    		.agreementId("urn:uuid:AGREEMENT_ID_COMPLETED_TRANSFER_TEST")
	    		.format("HTTP_PULL")
	    		.callbackAddress(CALLBACK_ADDRESS)
	    		.build();
		MvcResult mvcResult = mockMvc.perform(
    					post("/transfers/request")
    					.content(Serializer.serializeProtocol(transferRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON))
    					.andExpect(status().isCreated())
				    	.andReturn();
    	String jsonTransferProcess = mvcResult.getResponse().getContentAsString();
    	JsonNode jsonNode = Serializer.serializeStringToProtocolJsonNode(jsonTransferProcess);
    	TransferProcess transferProcessRequested = Serializer.deserializeProtocol(jsonNode, TransferProcess.class);
    	String providerPid = transferProcessRequested.getProviderPid();
		String transactionId = Base64.getEncoder().encodeToString((consumerPid + "|" + providerPid).getBytes(Charset.forName("UTF-8")));

    	// Try to access artifact before process is started - should result in error
    	downloadArtifactFail(transactionId);
    	
    	// send TransferStartMessage
    	sendTransferStartMessage(consumerPid, providerPid)
    		.andExpect(status().isOk());
		
		// download artifact
		downloadArifact(transactionId);
		
		// send suspend message
		TransferSuspensionMessage transferSuspensionMessage = TransferSuspensionMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.code("1")
//				.reason(Arrays.asList("Need to take a break."))
				.build();
		mockMvc.perform(
				post("/transfers/" + providerPid +"/suspension")
				.content(Serializer.serializeProtocol(transferSuspensionMessage))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
		
		// download artifact - fail
		downloadArtifactFail(transactionId);
		
		// send start message
		sendTransferStartMessage(consumerPid, providerPid)
			.andExpect(status().isOk());
		
		// download artifact
		downloadArifact(transactionId);
		
    	// send transfer completed
    	TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
    			.consumerPid(consumerPid)
    			.providerPid(providerPid)
    			.build();
    	mockMvc.perform(
    			post("/transfers/" + providerPid +"/completion")
    			.content(Serializer.serializeProtocol(transferCompletionMessage))
    			.contentType(MediaType.APPLICATION_JSON))
    			.andExpect(status().isOk());	
        	
       // check for transfer process status to be completed
    	TransferProcess transferProcessCompleted = getTransferProcessForProviderPid(providerPid);
    	assertEquals(TransferState.COMPLETED, transferProcessCompleted.getState());
    	
    	downloadArtifactFail(transactionId);
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

	private ResultActions sendTransferStartMessage(String consumerPid, String providerPid) throws Exception {
		TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.build();
		return mockMvc.perform(
			post("/transfers/" + providerPid +"/start")
			.content(Serializer.serializeProtocol(transferStartMessage))
			.contentType(MediaType.APPLICATION_JSON));
	}

	private void downloadArtifactFail(String transactionId) throws Exception {
		mockMvc.perform(post("/artifacts/" + transactionId + "/" + "artifactIdTest")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().is(HttpStatus.PRECONDITION_FAILED.value()));
	}

	private void downloadArifact(String transactionId) throws Exception, UnsupportedEncodingException {
		MvcResult resultArtifact = mockMvc.perform(post("/artifacts/" + transactionId + "/" + "artifactIdTest")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();
		String artifact = resultArtifact.getResponse().getContentAsString();
		assertTrue(artifact.contains("John"));
		assertTrue(artifact.contains("Doe"));
	}
	
	@Test
	@DisplayName("Terminate transfer process - provider")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void termianteTransferProcess_provider() throws Exception {
		String consumerPid = "urn:uuid" + UUID.randomUUID().toString();
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(consumerPid)
	    		.agreementId("urn:uuid:AGREEMENT_ID_TERMINATE_TRANSFER_TEST")
	    		.format("HTTP_PULL")
	    		.callbackAddress(CALLBACK_ADDRESS)
	    		.build();
		MvcResult mvcResult = mockMvc.perform(
    					post("/transfers/request")
    					.content(Serializer.serializeProtocol(transferRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON))
    					.andExpect(status().isCreated())
				    	.andReturn();
    	String jsonTransferProcess = mvcResult.getResponse().getContentAsString();
    	JsonNode jsonNode = Serializer.serializeStringToProtocolJsonNode(jsonTransferProcess);
    	TransferProcess transferProcessRequested = Serializer.deserializeProtocol(jsonNode, TransferProcess.class);
    	String providerPid = transferProcessRequested.getProviderPid();
    	
    	// send terminate message
    	TransferTerminationMessage transferTerminationMessage = TransferTerminationMessage.Builder.newInstance()
	    		.consumerPid(consumerPid)
	    		.providerPid(providerPid)
	    		.code("1")
	    		.build();
    	mvcResult = mockMvc.perform(
				post("/transfers/" + providerPid + "/termination")
				.content(Serializer.serializeProtocol(transferTerminationMessage))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
		    	.andReturn();
    	
    	// check TransferProcess status for providerPid
    	TransferProcess transferProcessTerminated = getTransferProcessForProviderPid(providerPid);
    	assertEquals(transferProcessTerminated.getState(), TransferState.TERMINATED);
	}
	
}