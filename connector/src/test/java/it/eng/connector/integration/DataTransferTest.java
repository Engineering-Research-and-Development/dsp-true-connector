package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.catalog.serializer.InstantDeserializer;
import it.eng.catalog.serializer.InstantSerializer;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.Serializer;
import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.response.GenericApiResponse;

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
	
	
	/* API Endpoints tests  */
	@Test
	@DisplayName("TransferProcess API - get")
	@WithUserDetails(TestUtil.API_USER)
	public void getTransferProcess() throws Exception {
		SimpleModule instantConverterModule = new SimpleModule();
		instantConverterModule.addSerializer(Instant.class, new InstantSerializer());
		instantConverterModule.addDeserializer(Instant.class, new InstantDeserializer());
		JsonMapper jsonMapper = JsonMapper.builder()
        		.addModule(new JavaTimeModule())
        		.configure(MapperFeature.USE_ANNOTATIONS, false)
        		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        		.addModule(instantConverterModule)
                .build();
		
		mockMvc.perform(
    			get("/api/transfers").contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
	    	.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, ArrayList.class);

		MvcResult resultStarted =mockMvc.perform(
    			get("/api/transfers?state=STARTED").contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		String json = resultStarted.getResponse().getContentAsString();
		GenericApiResponse<List<TransferProcess>> genericApiResponse = jsonMapper.readValue(json, javaType);
		// so far, must do like this because List<LinkedHashMap> was not able to get it to be List<TransferProcess>
		TransferProcess transferProcess = jsonMapper.convertValue(genericApiResponse.getData().get(0), TransferProcess.class);
		assertNotNull(transferProcess);
		assertEquals(TransferState.STARTED, transferProcess.getState());
		
		String TRANSFER_PROCESS_ID = "abc45798-4444-4932-8baf-ab7fd66ql4d5";
		MvcResult result = mockMvc.perform(
    			get("/api/transfers/" + TRANSFER_PROCESS_ID).contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andReturn();
		json = result.getResponse().getContentAsString();
		genericApiResponse = jsonMapper.readValue(json, javaType);

		assertNotNull(genericApiResponse);
		assertTrue(genericApiResponse.isSuccess());
		assertEquals(1, genericApiResponse.getData().size());
		// so far, must do like this because List<LinkedHashMap> was not able to get it to be List<TransferProcess>
		transferProcess = jsonMapper.convertValue(genericApiResponse.getData().get(0), TransferProcess.class);
		assertNotNull(transferProcess);
		assertEquals(TRANSFER_PROCESS_ID, transferProcess.getId());
	}
}


