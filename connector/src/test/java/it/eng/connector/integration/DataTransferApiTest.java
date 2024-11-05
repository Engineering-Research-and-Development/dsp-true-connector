package it.eng.connector.integration;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.catalog.serializer.InstantDeserializer;
import it.eng.catalog.serializer.InstantSerializer;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.serializer.Serializer;
import it.eng.datatransfer.util.MockObjectUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;

/**
 * Data Transfer API endpoints integration test
 */
public class DataTransferApiTest extends BaseIntegrationTest {
	
	private ObjectMapper mapper = new ObjectMapper();
	
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
		
		String TRANSFER_PROCESS_ID = "urn:uuid:abc45798-4444-4932-8baf-ab7fd66ql4d5";
		MvcResult result = mockMvc.perform(
    			get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" +TRANSFER_PROCESS_ID).contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andReturn();
		json = result.getResponse().getContentAsString();
		genericApiResponse = jsonMapper.readValue(json, javaType);

		assertNotNull(genericApiResponse);
		assertTrue(genericApiResponse.isSuccess());
		assertEquals(1, genericApiResponse.getData().size());
		// so far, must do like this because List<LinkedHashMap> was not able to get it to be List<TransferProcess>
		// commented out since here we are not using Serializer with custom @id handling
//		transferProcess = jsonMapper.convertValue(genericApiResponse.getData().get(0), TransferProcess.class);
//		assertNotNull(transferProcess);
//		assertEquals(TRANSFER_PROCESS_ID, genericApiResponse.getData().get(0).getId());
	}
	
	@Test
	@DisplayName("Request transfer process")
    @WithUserDetails(TestUtil.API_USER)
    public void initiateDataTransfer() throws Exception {
		Map<String, Object> map = new HashMap<>();
		map.put("Forward-To", "http://localhost:8090");
		map.put("agreementId", "urn:uuid:AGREEMENT_ID_INITIALIZED");
		map.put(DSpaceConstants.FORMAT, DataTransferFormat.HTTP_PULL.name());
		map.put(DSpaceConstants.DATA_ADDRESS, Serializer.serializePlainJsonNode(MockObjectUtil.DATA_ADDRESS));
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post(ApiEndpoints.TRANSFER_DATATRANSFER_V1)
    					.content(mapper.convertValue(map, JsonNode.class).toString())
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("$.data.state").value(TransferState.REQUESTED.name()))
    	.andExpect(jsonPath("$.data.role").value(IConstants.ROLE_CONSUMER))
    	.andExpect(jsonPath("$.data.format").isNotEmpty())
    	.andExpect(jsonPath("$.data.dataAddress").isNotEmpty());
    }
	
	/* TODO continue adding tests
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
