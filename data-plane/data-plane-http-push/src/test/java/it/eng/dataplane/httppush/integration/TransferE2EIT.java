package it.eng.dataplane.httppush.integration;

import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.model.DataFlowCheckpoint;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.repository.DataFlowCheckpointRepository;
import it.eng.dataplane.core.repository.DataFlowRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Autowired
    private DataFlowRepository dataFlowRepository;

    @Autowired
    private DataFlowCheckpointRepository dataFlowCheckpointRepository;

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
                "dataAddress", dataAddress
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
                "dataAddress", credentials
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

    @Test
    @DisplayName("E2E (resume from checkpoint): suspended transfer is resumed and artifact is fully pushed")
    void resumeTransfer_fromInjectedCheckpoint_fileIsPushedWithMatchingContent() throws Exception {
        String processId = newId();
        String entityId = UUID.randomUUID().toString();

        // Step 1: create temp MinIO user and provision consumer credentials (reused on resume)
        Map<String, String> credentials = createTempUserAndPolicy(processId, TEST_BUCKET_NAME, processId);

        // Step 2: create a real MinIO multipart upload to get a valid uploadId
        // (the resume logic will continue this upload from part 0 since confirmedBytes=0 and completedParts=[])
        String uploadId;
        try (S3Client s3 = buildAdminS3Client()) {
            CreateMultipartUploadResponse mpu = s3.createMultipartUpload(
                    CreateMultipartUploadRequest.builder()
                            .bucket(TEST_BUCKET_NAME)
                            .key(processId)
                            .build());
            uploadId = mpu.uploadId();
        }

        // Step 3: inject a SUSPENDED DataFlowEntity into MongoDB
        Instant now = Instant.now();
        DataFlowEntity suspendedEntity = DataFlowEntity.Builder.newInstance()
                .id(entityId)
                .processId(processId)
                .transferType(TRANSFER_TYPE_PUSH)
                .datasetId(E2E_SOURCE_KEY)
                .callbackAddress(wireMock.baseUrl() + "/callback")
                .state(DataFlowState.SUSPENDED)
                .dataAddress(credentials)
                .createdAt(now)
                .updatedAt(now)
                .build();
        dataFlowRepository.save(suspendedEntity);

        // Step 4: inject a DataFlowCheckpoint with the uploadId and confirmedBytes=0
        // (simulates a transfer that was paused before any part completed)
        DataFlowCheckpoint checkpoint = DataFlowCheckpoint.Builder.newInstance()
                .processId(processId)
                .dataFlowId(entityId)
                .transferType(TRANSFER_TYPE_PUSH)
                .uploadId(uploadId)
                .destinationBucket(TEST_BUCKET_NAME)
                .destinationObjectKey(processId)
                .completedParts(List.of())
                .partSizes(Map.of())
                .partETags(Map.of())
                .confirmedBytes(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        dataFlowCheckpointRepository.save(checkpoint);

        // Step 5: abort the dangling multipart upload after the test (if resume does not complete it)
        // The resume endpoint will re-use or abort it internally; if it fails, clean up manually.
        try {
            // Step 5: call the resume endpoint — DP should re-download the artifact and push it
            mockMvc.perform(withApiKey(post("/dataflows/{id}/resume", processId)))
                    .andExpect(status().isOk());

            // Step 6: wait for async push to complete, then verify
            awaitObjectExists(processId, 20);
            String actualContent = downloadContentFromMinIO(processId);
            assertEquals(E2E_SOURCE_CONTENT, actualContent,
                    "Resumed push content must match the source artifact");
        } finally {
            // Best-effort cleanup of any leftover multipart upload
            try (S3Client s3 = buildAdminS3Client()) {
                s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .key(processId)
                        .uploadId(uploadId)
                        .build());
            } catch (Exception ignored) {
                // Already completed or aborted by the protocol
            }
        }
    }
}
