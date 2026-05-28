package it.eng.dataplane.httppull.integration;

import it.eng.dataplane.api.DataPlaneConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the HTTP-PULL Data Plane transfer flow.
 *
 * <p>These tests verify that a real file is correctly downloaded from a presigned URL
 * and stored in the consumer's S3 bucket, with contents matching the original source.</p>
 *
 * <p>Test flow:
 * <ol>
 *   <li>A source artifact is uploaded to the consumer MinIO bucket using admin credentials.</li>
 *   <li>{@code /dataflows/prepare} is called with the artifact's {@code datasetId} to obtain
 *       a schema-aligned HTTP data address (provider-side prepare, no VIEW mode).</li>
 *   <li>{@code /dataflows/start} is called with a new {@code processId} and the presigned URL
 *       plus consumer sink properties in a schema-aligned {@code DataAddress}.</li>
 *   <li>The test polls MinIO until the object keyed by {@code processId} appears, then asserts
 *       the content matches the original source.</li>
 * </ol>
 * </p>
 */
class TransferE2EIT extends BaseHttpPullIT {

    private static final String TRANSFER_TYPE_PULL = "HttpData-PULL";
    private static final String E2E_SOURCE_KEY = "urn:uuid:e2e-pull-source-dataset";
    private static final String E2E_SOURCE_CONTENT = "Hello from HTTP-PULL E2E test";

    /**
     * Uploads the source artifact to the consumer MinIO bucket before any e2e test runs.
     * The key is used as {@code datasetId} in the prepare request so that the DP generates
     * a presigned URL pointing to this object.
     */
    @BeforeAll
    static void uploadSourceArtifact() {
        uploadToTestMinIO(E2E_SOURCE_KEY, E2E_SOURCE_CONTENT);
    }

    @Test
    @DisplayName("E2E: HTTP-PULL downloads artifact from presigned URL and stores it in consumer bucket with matching content")
    void pullTransfer_e2e_fileIsStoredInConsumerBucketWithMatchingContent() throws Exception {
        // Step 1: prepare — DP generates a presigned GET URL for the source artifact
        Map<String, Object> prepareBody = Map.of(
                "processId", newId(),
                "datasetId", E2E_SOURCE_KEY
        );
        String prepareJson = mockMvc.perform(withApiKey(post("/dataflows/prepare"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(prepareBody)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> prepareResponse = objectMapper.readValue(prepareJson, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> prepareDataAddress = (Map<String, String>) prepareResponse.get("dataAddress");
        assertNotNull(prepareDataAddress, "prepare response must include dataAddress");
        String endpoint = prepareDataAddress.get(DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT);
        assertNotNull(endpoint, "prepare must return an endpoint");
        String endpointType = prepareDataAddress.get(DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT_TYPE);
        assertNotNull(endpointType, "prepare must return an endpointType");

        // Step 2: start — DP downloads from the presigned URL and uploads to consumer bucket
        String processId = newId();
        Map<String, Object> startBody = Map.of(
                "processId", processId,
                "transferType", TRANSFER_TYPE_PULL,
                "dataAddress", Map.of(
                        "endpoint", endpoint,
                        "endpointType", endpointType,
                        "endpointProperties", List.of(
                                Map.of("name", DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "value", TEST_BUCKET_NAME),
                                Map.of("name", DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, "value", processId),
                                Map.of("name", DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "value", "us-east-1"),
                                Map.of("name", DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "value", minIOContainer.getUserName()),
                                Map.of("name", DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "value", minIOContainer.getPassword()),
                                Map.of("name", DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE, "value", minIOContainer.getS3URL())
                        ))
        );
        mockMvc.perform(withApiKey(post("/dataflows/start"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(startBody)))
                .andExpect(status().isCreated());

        // Step 3: wait for async transfer to complete, then verify the stored object
        awaitObjectExists(processId, 15);
        String actualContent = downloadContentFromMinIO(processId);
        assertEquals(E2E_SOURCE_CONTENT, actualContent,
                "Downloaded and stored file content must match the source artifact");
    }
}
