package it.eng.connector.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.catalog.serializer.InstantDeserializer;
import it.eng.catalog.serializer.InstantSerializer;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;

/**
 * Data Transfer API endpoints integration test
 */
public class DataTransferApiTest extends BaseIntegrationTest {

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

		MvcResult resultStarted =mockMvc.perform(
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
		
		String TRANSFER_PROCESS_ID = "abc45798-4444-4932-8baf-ab7fd66ql4d5";
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
		transferProcess = jsonMapper.convertValue(genericApiResponse.getData().get(0), TransferProcess.class);
		assertNotNull(transferProcess);
		assertEquals(TRANSFER_PROCESS_ID, transferProcess.getId());
	}
	
}
