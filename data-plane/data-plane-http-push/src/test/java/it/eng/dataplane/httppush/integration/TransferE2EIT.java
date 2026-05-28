package it.eng.dataplane.httppush.integration;

import it.eng.dataplane.api.DataPlaneConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the HTTP-PUSH Data Plane transfer flow.
 *
 * <p>These tests verify that a real file is correctly pushed from the provider's S3 bucket
 * to the destination bucket using a temporary IAM user with a scoped PutObject policy.
 * Both source and destination reside in the same MinIO Testcontainer instance to keep
 * tests self-contained.</p>
 *
 * <p>The two tested paths are:
 * <ol>
 *   <li><strong>prepare → start (e2e via prepare API)</strong>: the Control Plane calls
 *       {@code /dataflows/prepare} to obtain temp IAM credentials, then calls
 *       {@code /dataflows/start} with those credentials and a source {@code datasetId};
 *       the DP downloads the artifact from a presigned URL and pushes to the consumer bucket.</li>
 *   <li><strong>direct temp-user → start (e2e via test helper)</strong>: the test creates
 *       the temp user directly using the admin MinIO client (mirroring what the consumer CP
 *       does), then calls {@code /dataflows/start} with those credentials; verifies that the
 *       same push mechanism works when credentials are supplied externally.</li>
 * </ol>
 * </p>
 */
class TransferE2EIT extends BaseHttpPushIT {

    private static final String TRANSFER_TYPE_PUSH = "HttpData-PUSH";
    private static final String E2E_SOURCE_KEY = "urn:uuid:e2e-push-source-dataset";
    private static final String E2E_SOURCE_CONTENT = "Hello from HTTP-PUSH E2E test";

    /**
     * Uploads the source artifact to the provider's MinIO bucket before any e2e test runs.
     * The artifact key matches the {@code datasetId} field sent in the start message, so that
     * {@code HttpPushTransferProtocol} can generate a valid presigned URL for it.
     */
    @BeforeAll
    static void uploadSourceArtifact() {
        uploadToTestMinIO(E2E_SOURCE_KEY, E2E_SOURCE_CONTENT);
    }

    @Test
    @DisplayName("E2E (prepare → start): artifact is pushed to provider bucket; content matches source")
    void pushTransfer_viaPrepareEndpoint_fileIsPushedWithMatchingContent() throws Exception {
        String processId = newId();

        // Step 1: prepare — DP creates temp IAM user with PutObject access to bucket/processId
        Map<String, Object> prepareBody = Map.of("processId", processId);
        String prepareJson = mockMvc.perform(withApiKey(post("/dataflows/prepare"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(prepareBody)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> prepareResponse = objectMapper.readValue(prepareJson, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> dataAddress = (Map<String, String>) prepareResponse.get("dataAddress");
        assertNotNull(dataAddress, "prepare response must include dataAddress");
        assertNotNull(dataAddress.get("accessKey"), "prepare must return accessKey");
        assertNotNull(dataAddress.get("secretKey"), "prepare must return secretKey");

        // Step 2: start — DP downloads artifact via presigned URL and pushes to consumer bucket
        Map<String, Object> startBody = Map.of(
                "processId", processId,
                "transferType", TRANSFER_TYPE_PUSH,
                "datasetId", E2E_SOURCE_KEY,
                "dataAddress", toStartDataAddress(processId, dataAddress)
        );
        mockMvc.perform(withApiKey(post("/dataflows/start"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(startBody)))
                .andExpect(status().isCreated());

        // Step 3: wait for async push to complete, then verify
        awaitObjectExists(processId, 15);
        String actualContent = downloadContentFromMinIO(processId);
        assertEquals(E2E_SOURCE_CONTENT, actualContent,
                "Pushed file content must match the source artifact");
    }

    @Test
    @DisplayName("E2E (direct temp-user → start): artifact is pushed using externally created temp credentials")
    void pushTransfer_viaDirectTempUser_fileIsPushedWithMatchingContent() throws Exception {
        String processId = newId();

        // Step 1: create temp user directly via admin MinIO client (mirrors consumer CP behaviour)
        Map<String, String> credentials = createTempUserAndPolicy(processId, TEST_BUCKET_NAME, processId);

        // Step 2: start — supply credentials from helper; DP generates presigned URL for source
        Map<String, Object> startBody = Map.of(
                "processId", processId,
                "transferType", TRANSFER_TYPE_PUSH,
                "datasetId", E2E_SOURCE_KEY,
                "dataAddress", toStartDataAddress(processId, credentials)
        );
        mockMvc.perform(withApiKey(post("/dataflows/start"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(startBody)))
                .andExpect(status().isCreated());

        // Step 3: wait and verify
        awaitObjectExists(processId, 15);
        String actualContent = downloadContentFromMinIO(processId);
        assertEquals(E2E_SOURCE_CONTENT, actualContent,
                "Pushed file content must match the source artifact");
    }

    /**
     * Converts flat prepare/direct-temp-user credentials into the schema-aligned start {@code DataAddress}.
     *
     * @param processId the transfer process ID
     * @param sinkDataAddress flat sink credential map from prepare or helper
     * @return JSON-ready schema-aligned data address map
     */
    private Map<String, Object> toStartDataAddress(String processId, Map<String, String> sinkDataAddress) {
        Map<String, String> sourceProperties = new LinkedHashMap<>();
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, TEST_BUCKET_NAME);
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, E2E_SOURCE_KEY);
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_REGION, "us-east-1");
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ACCESS_KEY, minIOContainer.getUserName());
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_SECRET_KEY, minIOContainer.getPassword());
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ENDPOINT_OVERRIDE, minIOContainer.getS3URL());

        Map<String, String> sinkProperties = new LinkedHashMap<>();
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, sinkDataAddress.get("bucketName"));
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY,
                sinkDataAddress.getOrDefault("objectKey", processId));
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, sinkDataAddress.get("region"));
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, sinkDataAddress.get("accessKey"));
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, sinkDataAddress.get("secretKey"));
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE,
                sinkDataAddress.get("endpointOverride"));

        List<Map<String, String>> endpointProperties = java.util.stream.Stream
                .concat(sourceProperties.entrySet().stream(), sinkProperties.entrySet().stream())
                .map(entry -> Map.of("name", entry.getKey(), "value", entry.getValue()))
                .toList();

        return Map.of(
                "endpointType", "https://w3id.org/idsa/v4.1/HTTP",
                "endpointProperties", endpointProperties
        );
    }
}
