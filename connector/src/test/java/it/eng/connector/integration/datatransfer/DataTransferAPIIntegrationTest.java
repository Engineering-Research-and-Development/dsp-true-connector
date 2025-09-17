package it.eng.connector.integration.datatransfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.InjectWireMock;

import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                        authenticatedGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1, TestUtil.API_USER).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        MvcResult resultStarted = mockMvc.perform(
                        authenticatedGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId(), TestUtil.API_USER)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String json = resultStarted.getResponse().getContentAsString();
        GenericApiResponse<TransferProcess> genericApiResponse = TransferSerializer.deserializePlain(json,
                new TypeReference<GenericApiResponse<TransferProcess>>() {
                });

        assertNotNull(genericApiResponse);
        assertTrue(genericApiResponse.isSuccess());
        TransferProcess transferProcess = genericApiResponse.getData();
        assertNotNull(transferProcess);
        assertEquals(TransferState.STARTED, transferProcess.getState());

        MvcResult result = mockMvc.perform(
                        authenticatedGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessRequested.getId(), TestUtil.API_USER)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        json = result.getResponse().getContentAsString();
        genericApiResponse = TransferSerializer.deserializePlain(json,
                new TypeReference<GenericApiResponse<TransferProcess>>() {
                });

        assertNotNull(genericApiResponse);
        assertTrue(genericApiResponse.isSuccess());
        TransferProcess transferProcessFromDB = genericApiResponse.getData();
        assertNotNull(transferProcessFromDB);
        assertEquals(transferProcessRequested.getId(), transferProcessFromDB.getId());
    }

    @Test
    @DisplayName("Request transfer process - success")
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
                        authenticatedPost(ApiEndpoints.TRANSFER_DATATRANSFER_V1, TestUtil.API_USER)
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
                        authenticatedPost(ApiEndpoints.TRANSFER_DATATRANSFER_V1, TestUtil.API_USER)
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

    @Test
    @DisplayName("Filter by datasetId only")
    public void filterByDatasetId() throws Exception {
        // Setup test data with diverse values
        TransferProcess process1 = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("dataset-1")
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.REQUESTED)
                .build();

        TransferProcess process2 = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("dataset-1")
                .role(IConstants.ROLE_PROVIDER)
                .state(TransferState.STARTED)
                .build();

        TransferProcess process3 = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("dataset-2")
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.COMPLETED)
                .build();

        transferProcessRepository.saveAll(List.of(process1, process2, process3));

        MvcResult result = mockMvc.perform(
                        authenticatedGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1, TestUtil.API_USER)
                                .param("datasetId", "dataset-1")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<List<TransferProcess>> genericApiResponse = parseResponse(json);

        assertNotNull(genericApiResponse);
        assertTrue(genericApiResponse.isSuccess());
        assertEquals(2, genericApiResponse.getData().size());

        // Verify both processes have correct datasetId
        for (Object obj : genericApiResponse.getData()) {
            TransferProcess process = jsonMapper.convertValue(obj, TransferProcess.class);
            assertEquals("dataset-1", process.getDatasetId());
        }
    }

    @Test
    @DisplayName("Filter by providerPid only")
    public void filterByProviderPid() throws Exception {
        String testProviderPid = createNewId();

        TransferProcess process1 = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(testProviderPid)
                .datasetId("dataset-1")
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.REQUESTED)
                .build();

        TransferProcess process2 = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("dataset-2")
                .role(IConstants.ROLE_PROVIDER)
                .state(TransferState.STARTED)
                .build();

        transferProcessRepository.saveAll(List.of(process1, process2));

        MvcResult result = mockMvc.perform(
                        authenticatedGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1, TestUtil.API_USER)
                                .param("providerPid", testProviderPid)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<List<TransferProcess>> genericApiResponse = parseResponse(json);

        assertNotNull(genericApiResponse);
        assertTrue(genericApiResponse.isSuccess());
        assertEquals(1, genericApiResponse.getData().size());

        TransferProcess process = jsonMapper.convertValue(genericApiResponse.getData().get(0), TransferProcess.class);
        assertEquals(testProviderPid, process.getProviderPid());
    }

    @Test
    @DisplayName("Filter by consumerPid only")
    public void filterByConsumerPid() throws Exception {
        String testConsumerPid = createNewId();

        TransferProcess process1 = TransferProcess.Builder.newInstance()
                .consumerPid(testConsumerPid)
                .providerPid(createNewId())
                .datasetId("dataset-1")
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.REQUESTED)
                .build();

        TransferProcess process2 = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("dataset-2")
                .role(IConstants.ROLE_PROVIDER)
                .state(TransferState.STARTED)
                .build();

        transferProcessRepository.saveAll(List.of(process1, process2));

        MvcResult result = mockMvc.perform(
                        authenticatedGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1, TestUtil.API_USER)
                                .param("consumerPid", testConsumerPid)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<List<TransferProcess>> genericApiResponse = parseResponse(json);

        assertNotNull(genericApiResponse);
        assertTrue(genericApiResponse.isSuccess());
        assertEquals(1, genericApiResponse.getData().size());

        TransferProcess process = jsonMapper.convertValue(genericApiResponse.getData().get(0), TransferProcess.class);
        assertEquals(testConsumerPid, process.getConsumerPid());
    }

    @Test
    @DisplayName("Filter by multiple parameters (datasetId, state, role)")
    public void filterByMultipleParameters() throws Exception {
        TransferProcess process1 = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("dataset-1")
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.REQUESTED)
                .build();

        TransferProcess process2 = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("dataset-1")
                .role(IConstants.ROLE_PROVIDER)
                .state(TransferState.REQUESTED)
                .build();

        TransferProcess process3 = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("dataset-1")
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.STARTED)
                .build();

        transferProcessRepository.saveAll(List.of(process1, process2, process3));

        MvcResult result = mockMvc.perform(
                        authenticatedGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1, TestUtil.API_USER)
                                .param("datasetId", "dataset-1")
                                .param("state", TransferState.REQUESTED.name())
                                .param("role", IConstants.ROLE_CONSUMER)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<List<TransferProcess>> genericApiResponse = parseResponse(json);

        assertNotNull(genericApiResponse);
        assertTrue(genericApiResponse.isSuccess());
        assertEquals(1, genericApiResponse.getData().size());

        TransferProcess process = jsonMapper.convertValue(genericApiResponse.getData().get(0), TransferProcess.class);
        assertEquals("dataset-1", process.getDatasetId());
        assertEquals(TransferState.REQUESTED, process.getState());
        assertEquals(IConstants.ROLE_CONSUMER, process.getRole());
    }

    @Test
    @DisplayName("Filter with all new parameters")
    public void filterWithAllNewParameters() throws Exception {
        String testConsumerPid = createNewId();
        String testProviderPid = createNewId();

        TransferProcess matchingProcess = TransferProcess.Builder.newInstance()
                .consumerPid(testConsumerPid)
                .providerPid(testProviderPid)
                .datasetId("target-dataset")
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.REQUESTED)
                .build();

        TransferProcess nonMatchingProcess = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("other-dataset")
                .role(IConstants.ROLE_PROVIDER)
                .state(TransferState.STARTED)
                .build();

        transferProcessRepository.saveAll(List.of(matchingProcess, nonMatchingProcess));

        MvcResult result = mockMvc.perform(
                        authenticatedGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1, TestUtil.API_USER)
                                .param("datasetId", "target-dataset")
                                .param("providerPid", testProviderPid)
                                .param("consumerPid", testConsumerPid)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<List<TransferProcess>> genericApiResponse = parseResponse(json);

        assertNotNull(genericApiResponse);
        assertTrue(genericApiResponse.isSuccess());
        assertEquals(1, genericApiResponse.getData().size());

        TransferProcess process = jsonMapper.convertValue(genericApiResponse.getData().get(0), TransferProcess.class);
        assertEquals("target-dataset", process.getDatasetId());
        assertEquals(testProviderPid, process.getProviderPid());
        assertEquals(testConsumerPid, process.getConsumerPid());
    }

    @Test
    @DisplayName("Transfer process ID takes priority over filters")
    @Disabled("Disabled since this test is not applicable to the current API design")
    public void transferProcessIdTakesPriority() throws Exception {
        TransferProcess process = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("actual-dataset")
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.REQUESTED)
                .build();

        transferProcessRepository.save(process);

        MvcResult result = mockMvc.perform(
                        authenticatedGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + process.getId(), TestUtil.API_USER)
                                .param("datasetId", "different-dataset")  // Should be ignored
                                .param("state", TransferState.COMPLETED.name())  // Should be ignored
                                .param("role", IConstants.ROLE_PROVIDER)         // Should be ignored
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<TransferProcess> genericApiResponse = TransferSerializer.deserializePlain(json,
                new TypeReference<GenericApiResponse<TransferProcess>>() {
                });

        assertNotNull(genericApiResponse);
        assertTrue(genericApiResponse.isSuccess());
        TransferProcess returnedProcess = genericApiResponse.getData();
        assertEquals(process.getId(), returnedProcess.getId());
        // Verify actual values are returned, not filter values
        assertEquals("actual-dataset", returnedProcess.getDatasetId());
        assertEquals(TransferState.REQUESTED, returnedProcess.getState());
        assertEquals(IConstants.ROLE_CONSUMER, returnedProcess.getRole());
    }

    @Test
    @DisplayName("Filter returns empty result when no matches")
    public void filterReturnsEmptyWhenNoMatches() throws Exception {
        TransferProcess process = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .datasetId("existing-dataset")
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.REQUESTED)
                .build();

        transferProcessRepository.save(process);

        MvcResult result = mockMvc.perform(
                        authenticatedGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1, TestUtil.API_USER)
                                .param("datasetId", "non-existent-dataset")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<List<TransferProcess>> genericApiResponse = parseResponse(json);

        assertNotNull(genericApiResponse);
        assertTrue(genericApiResponse.isSuccess());
        assertTrue(genericApiResponse.getData().isEmpty());
    }

    private GenericApiResponse<List<TransferProcess>> parseResponse(String json) throws Exception {
        JsonNode jsonNode = jsonMapper.readValue(json, JsonNode.class);
        boolean success = jsonNode.path("response").path("success").asBoolean();
        String message = jsonNode.path("response").path("message").asText();
        JsonNode data = jsonNode.path("response").path("data").path("content");
        List<TransferProcess> listData = TransferSerializer.deserializePlain(data.toPrettyString(),
                new TypeReference<List<TransferProcess>>() {
                });

        if (success) {
            return GenericApiResponse.success(listData, message);
        }
        return GenericApiResponse.error(listData, message);
    }


	/* TODO continue adding tests
	 * @PutMapping(path = "/{transferProcessId}/start")
	 * @PutMapping(path = "/{transferProcessId}/complete")
	 * @PutMapping(path = "/{transferProcessId}/suspend")
	 * @PutMapping(path = "/{transferProcessId}/terminate")
	@Test
	@DisplayName("Start transfer process - from requested")
	public void startTransferProcess_requested() throws Exception {
		// from init_data.json
		String transferProcessId = "urn:uuid:abc45798-5555-4932-8baf-ab7fd66ql4d5";

    	ResultActions transferProcessStarted = mockMvc.perform(
    			authenticatedPut(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessId + "/start", TestUtil.API_USER).contentType(MediaType.APPLICATION_JSON));
    	// check if status is STARTED
    	transferProcessStarted.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("$.data.state").value(TransferState.STARTED.name()))
    	.andExpect(jsonPath("$.data.role").value(IConstants.ROLE_CONSUMER))
    	.andExpect(jsonPath("$.data.format").isNotEmpty())
    	.andExpect(jsonPath("$.data.dataAddress").isNotEmpty());

    	ResultActions transferProcessError= mockMvc.perform(
    			authenticatedPut(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessId + "/start", TestUtil.API_USER).contentType(MediaType.APPLICATION_JSON));
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
						authenticatedGet(ApiEndpoints.NEGOTIATION_V1, TestUtil.CONNECTOR_USER)
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
