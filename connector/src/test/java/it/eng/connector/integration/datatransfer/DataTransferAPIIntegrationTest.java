package it.eng.connector.integration.datatransfer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.InjectWireMock;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.Reason;
import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;

/**
 * Data Transfer API endpoints integration test
 */
public class DataTransferAPIIntegrationTest extends BaseIntegrationTest {
	
	@Autowired
	private TransferProcessRepository transferProcessRepository;
	
	@InjectWireMock 
	private WireMockServer wiremock;
	
	@AfterEach
	public void cleanup() {
		transferProcessRepository.deleteAll();
	}
	
	@Test
	@DisplayName("TransferProcess API - get")
	@WithUserDetails(TestUtil.API_USER)
	public void getTransferProcess() throws Exception {
		TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.state(TransferState.REQUESTED)
				.build();
		transferProcessRepository.save(transferProcessRequested);
		
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.state(TransferState.STARTED)
				.build();
		transferProcessRepository.save(transferProcessStarted);
		
		mockMvc.perform(
    			get(ApiEndpoints.TRANSFER_DATATRANSFER_V1).contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
	    	.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, ArrayList.class);

		MvcResult resultStarted = mockMvc.perform(
    			get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "?state=STARTED").contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		String json = resultStarted.getResponse().getContentAsString();
		GenericApiResponse<List<TransferProcess>> genericApiResponse = jsonMapper.readValue(json, javaType);
		// so far, must do like this because List<LinkedHashMap> was not able to get it to be List<TransferProcess>
		TransferProcess transferProcess = jsonMapper.convertValue(genericApiResponse.getData().get(0), TransferProcess.class);
		assertNotNull(transferProcess);
		assertEquals(TransferState.STARTED, transferProcess.getState());
		
		MvcResult result = mockMvc.perform(
    			get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessRequested.getId()).contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andReturn();
		json = result.getResponse().getContentAsString();
		genericApiResponse = jsonMapper.readValue(json, javaType);

		assertNotNull(genericApiResponse);
		assertTrue(genericApiResponse.isSuccess());
		assertEquals(1, genericApiResponse.getData().size());
		// so far, must do like this because List<LinkedHashMap> was not able to get it to be List<TransferProcess>
		TransferProcess transferProcessFromDB = TransferSerializer.deserializePlain(jsonMapper.valueToTree(genericApiResponse.getData().get(0)), 
				TransferProcess.class);
		assertNotNull(transferProcessFromDB);
		assertEquals(transferProcessRequested.getId(), transferProcessFromDB.getId());
	}
	
	@Test
	@DisplayName("Request transfer process - success")
    @WithUserDetails(TestUtil.API_USER)
	public void initiateDataTransfer() throws Exception {
		TransferProcess transferProcessInitialized = TransferProcess.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(IConstants.TEMPORARY_PROVIDER_PID)
				.agreementId(createNewId())
				.callbackAddress(wiremock.baseUrl())
				.state(TransferState.INITIALIZED)
				.role(IConstants.ROLE_CONSUMER)
				.build();
		transferProcessRepository.save(transferProcessInitialized);
		
		DataTransferRequest dataTransferRequest = new DataTransferRequest(transferProcessInitialized.getId(), 
				DataTransferFormat.HTTP_PULL.name(), null);
		
		// mock provider success response TransferRequestMessage
		TransferProcess providerResponse = TransferProcess.Builder.newInstance()
				.consumerPid(transferProcessInitialized.getId())
				.providerPid(createNewId())
				.state(TransferState.REQUESTED)
				.build();
		
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/transfers/request")
				.withBasicAuth("connector@mail.com", "password")
				.withRequestBody(WireMock.containing("dspace:TransferRequestMessage"))
				.willReturn(
	                aResponse().withHeader("Content-Type", "application/json")
	                .withBody(TransferSerializer.serializeProtocol(providerResponse))));
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post(ApiEndpoints.TRANSFER_DATATRANSFER_V1)
    					.content(jsonMapper.convertValue(dataTransferRequest, JsonNode.class).toString())
    					.contentType(MediaType.APPLICATION_JSON));
    	
    	result.andExpect(status().isOk())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	String json = result.andReturn().getResponse().getContentAsString();
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, TransferProcess.class);
		GenericApiResponse<TransferProcess> genericApiResponse = jsonMapper.readValue(json, javaType);
		assertNotNull(genericApiResponse);
		assertTrue(genericApiResponse.isSuccess());
		assertNotNull(genericApiResponse.getData());
		assertEquals(TransferProcess.class, genericApiResponse.getData().getClass());
		
		// check if the Transfer Process is properly inserted and that consumerPid and providerPid are correct
    	TransferProcess transferProcessFromDb = transferProcessRepository.findById(transferProcessInitialized.getId()).get();
    	
    	assertEquals(transferProcessInitialized.getConsumerPid(), transferProcessFromDb.getConsumerPid());
    	assertEquals(genericApiResponse.getData().getProviderPid(), transferProcessFromDb.getProviderPid());
    	assertEquals(TransferState.REQUESTED, transferProcessFromDb.getState());
    }
	
	@Test
	@DisplayName("Request transfer process - provider error")
    @WithUserDetails(TestUtil.API_USER)
	public void initiateDataTransfer_provider_error() throws Exception { 
		TransferProcess transferProcessInitialized = TransferProcess.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.agreementId(createNewId())
				.callbackAddress(wiremock.baseUrl())
				.state(TransferState.INITIALIZED)
				.build();
		transferProcessRepository.save(transferProcessInitialized);
		
		DataTransferRequest dataTransferRequest = new DataTransferRequest(transferProcessInitialized.getId(), 
				DataTransferFormat.HTTP_PULL.name(), null);
		
		// mock provider transfer error response TransferRequestMessage
		TransferError providerErrorResponse = TransferError.Builder.newInstance()
				.consumerPid(transferProcessInitialized.getId())
				.providerPid(createNewId())
				.code("TEST")
				.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("TEST").build()))
				.build();
		
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/transfers/request")
				.withBasicAuth("connector@mail.com", "password")
				.withRequestBody(WireMock.containing("dspace:TransferRequestMessage"))
				.willReturn(
	                aResponse().withHeader("Content-Type", "application/json")
	                .withStatus(400)
	                .withBody(TransferSerializer.serializeProtocol(providerErrorResponse))));
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post(ApiEndpoints.TRANSFER_DATATRANSFER_V1)
    					.content(jsonMapper.convertValue(dataTransferRequest, JsonNode.class).toString())
    					.contentType(MediaType.APPLICATION_JSON));
		
    	result.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	String json = result.andReturn().getResponse().getContentAsString();
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, TransferError.class);
		GenericApiResponse<TransferError> genericApiResponse = jsonMapper.readValue(json, javaType);
		assertNotNull(genericApiResponse);
		assertFalse(genericApiResponse.isSuccess());
		assertNotNull(genericApiResponse.getData());
		assertEquals(TransferError.class, genericApiResponse.getData().getClass());
		
		// check if the Transfer Process is unchanged and that consumerPid and providerPid are correct
    	TransferProcess transferProcessFromDb = transferProcessRepository.findById(transferProcessInitialized.getId()).get();
    	
    	assertEquals(transferProcessInitialized.getProviderPid(), transferProcessFromDb.getProviderPid());
    	assertEquals(transferProcessInitialized.getConsumerPid(), transferProcessFromDb.getConsumerPid());
    	assertEquals(TransferState.INITIALIZED, transferProcessFromDb.getState());
	}
	
	
	/* TODO continue adding tests
	 * @PutMapping(path = "/{transferProcessId}/start")
	 * @PutMapping(path = "/{transferProcessId}/complete")
	 * @PutMapping(path = "/{transferProcessId}/suspend")
	 * @PutMapping(path = "/{transferProcessId}/terminate")
	@Test
	@DisplayName("Start transfer process - from requested")
	@WithUserDetails(TestUtil.API_USER)
	public void startTransferProcess_requested() throws Exception {
		// from init_data.json
		String transferProcessId = "urn:uuid:abc45798-5555-4932-8baf-ab7fd66ql4d5";
	      
    	ResultActions transferProcessStarted = mockMvc.perform(
    			put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessId + "/start").contentType(MediaType.APPLICATION_JSON));
    	// check if status is STARTED
    	transferProcessStarted.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("$.data.state").value(TransferState.STARTED.name()))
    	.andExpect(jsonPath("$.data.role").value(IConstants.ROLE_CONSUMER))
    	.andExpect(jsonPath("$.data.format").isNotEmpty())
    	.andExpect(jsonPath("$.data.dataAddress").isNotEmpty());
    	
    	ResultActions transferProcessError= mockMvc.perform(
    			put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessId + "/start").contentType(MediaType.APPLICATION_JSON));
    	// try again to start - error
    	transferProcessError.andExpect(err -> assertTrue(err.getResolvedException() instanceof TransferProcessInvalidStateException))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
	    	.andExpect(jsonPath("['" + DSpaceConstants.TYPE + "']", 
	    			is(DSpaceConstants.DSPACE + TransferError.class.getSimpleName())))
	    	.andExpect(jsonPath("['" + DSpaceConstants.CONTEXT + "']", is(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE)));
	}
	
	private JsonNode getContractNegotiationOverAPI()
			throws Exception, JsonProcessingException, JsonMappingException, UnsupportedEncodingException {
		final ResultActions result =
				mockMvc.perform(
						get(ApiEndpoints.NEGOTIATION_V1)
						.with(user(TestUtil.CONNECTOR_USER).password("password").roles("ADMIN"))
						.contentType(MediaType.APPLICATION_JSON));
		
		result.andExpect(status().isOk())
		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		JsonNode jsonNode = mapper.readTree(result.andReturn().getResponse().getContentAsString());
		return jsonNode.findValues("data").get(0).get(jsonNode.findValues("data").get(0).size()-1);
	}
	
	private void offerCheck(JsonNode contractNegotiation) {
		assertEquals(offerID, contractNegotiation.get("offer").get("originalId").asText());
	}
	
	private void agreementCheck(JsonNode contractNegotiation) {
		assertNotNull(contractNegotiation.get("agreement"));
	} */
	
}
