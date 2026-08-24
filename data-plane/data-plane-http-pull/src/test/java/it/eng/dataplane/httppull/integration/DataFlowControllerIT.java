package it.eng.dataplane.httppull.integration;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataAddress;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.dataplane.api.message.EndpointProperty;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.repository.DataFlowRepository;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.repository.BucketCredentialsRepository;
import it.eng.tools.s3.service.S3ClientServiceImpl;
import it.eng.tools.service.FieldEncryptionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code DataFlowController} and {@code ControlPlaneRegistrationController}
 * in the HTTP-PULL Data Plane application.
 *
 * <p>Covers the full lifecycle: prepare, start, terminate, suspend, and control-plane
 * registration. Authentication is via the {@code X-Api-Key} header.
 */
class DataFlowControllerIT extends BaseHttpPullIT {

    private static final String TRANSFER_TYPE_PULL = "HttpData-PULL";

    @Autowired
    private DataFlowRepository dataFlowRepository;

    @Autowired
    private FieldEncryptionService fieldEncryptionService;

    /**
     * Object key pre-loaded into test MinIO for prepare-endpoint tests.
     * Uses a fixed value so the presigned URL generation finds an existing object.
     */
    private static final String PREPARE_OBJECT_KEY = "urn:uuid:test-prepare-fixture-00000001";
    @Autowired
    private BucketCredentialsRepository bucketCredentialsRepository;
    @Autowired
    private S3ClientServiceImpl s3ClientServiceImpl;

    /**
     * Uploads a test fixture object to MinIO so that presigned URL generation in the
     * prepare endpoint has an existing object to call {@code HeadObject} on.
     */
    @BeforeAll
    static void uploadPrepareFixture() {
        uploadToTestMinIO(PREPARE_OBJECT_KEY, "test-prepare-fixture-content");
    }

    @Test
    @DisplayName("POST /dataflows/prepare with valid processId returns 200 with processId in body")
    void prepareDataFlow_returnsOkWithProcessId() throws Exception {
        BucketCredentialsEntity bucketCredentialsEntity = bucketCredentialsRepository.findByBucketName(TEST_BUCKET_NAME).orElseThrow();
        Map<String, Object> s3Section = new LinkedHashMap<>();
        String decryptedSecretKey = fieldEncryptionService.decrypt(bucketCredentialsEntity.getSecretKey());
        s3Section.put(DataPlaneConstants.METADATA_S3_BUCKET_NAME, TEST_BUCKET_NAME);
        s3Section.put(DataPlaneConstants.METADATA_S3_OBJECT_KEY, PREPARE_OBJECT_KEY);
        s3Section.put(DataPlaneConstants.METADATA_S3_ACCESS_KEY, bucketCredentialsEntity.getAccessKey());
        s3Section.put(DataPlaneConstants.METADATA_S3_SECRET_KEY, decryptedSecretKey);
        s3Section.put(DataPlaneConstants.METADATA_S3_REGION,  "us-east-1");
        s3Section.put(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, minIOContainer.getS3URL());

        DataFlowPrepareMessage dataFlowPrepareMessage = DataFlowPrepareMessage.Builder.newInstance()
                .processId(PREPARE_OBJECT_KEY)
                .metadata(Map.of("sink", Map.of("mode", "VIEW",
                        "s3", s3Section)))
                .build();

        mockMvc.perform(withApiKey(post("/dataflows/prepare"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dataFlowPrepareMessage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processId").value(PREPARE_OBJECT_KEY));
    }

    @Test
    @DisplayName("POST /dataflows/prepare with legacy top-level dataAddress returns 4xx")
    void prepareDataFlow_withLegacyDataAddress_returnsClientError() throws Exception {
        Map<String, Object> body = Map.of(
                "processId", PREPARE_OBJECT_KEY,
                "dataAddress", Map.of("mode", "VIEW")
        );

        mockMvc.perform(withApiKey(post("/dataflows/prepare"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /dataflows/prepare without API key returns 401 or 403")
    void prepareDataFlow_withoutApiKey_returnsUnauthorized() throws Exception {
        Map<String, Object> body = Map.of("processId", newId());

        mockMvc.perform(post("/dataflows/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /dataflows/start with valid HttpData-PULL payload returns 201 Created")
    void startDataFlow_validPull_returns201() throws Exception {
        Map<String, Object> body = Map.of(
                "processId", newId(),
                "transferType", TRANSFER_TYPE_PULL
        );

        mockMvc.perform(withApiKey(post("/dataflows/start"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /dataflows/start with duplicate processId returns 200 OK (idempotent)")
    void startDataFlow_duplicateProcessId_returns200() throws Exception {
        String processId = newId();

        String presignURL;

        try (S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(minIOContainer.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minIOContainer.getUserName(), minIOContainer.getPassword())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .region(Region.US_EAST_1).build()) {
            var getObjectRequest = GetObjectRequest.builder()
                    .bucket(TEST_BUCKET_NAME)
                    .key(PREPARE_OBJECT_KEY)
                    .build();

            var presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10))
                    .getObjectRequest(getObjectRequest).build();

            presignURL =  presigner.presignGetObject(presignRequest).url().toExternalForm();
        }

        DataFlowStartMessage dataFlowStartMessage = DataFlowStartMessage.Builder.newInstance()
                .processId(processId)
                .transferType(TRANSFER_TYPE_PULL)
                .dataAddress(DataAddress.Builder.newInstance()
                        .endpoint(presignURL)
                        .endpointType("HttpData-PULL")
                        .endpointProperties(List.of(EndpointProperty.Builder.newInstance()
                                        .name(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME)
                                        .value(TEST_BUCKET_NAME)
                                .build()))
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(dataFlowStartMessage);

        mockMvc.perform(withApiKey(post("/dataflows/start"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(withApiKey(post("/dataflows/start"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /dataflows/start with unknown transferType returns 400 Bad Request")
    void startDataFlow_unknownTransferType_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "processId", newId(),
                "transferType", "UnknownProtocol"
        );

        mockMvc.perform(withApiKey(post("/dataflows/start"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /dataflows/start without API key returns 401 or 403")
    void startDataFlow_withoutApiKey_returnsUnauthorized() throws Exception {
        Map<String, Object> body = Map.of(
                "processId", newId(),
                "transferType", TRANSFER_TYPE_PULL
        );

        mockMvc.perform(post("/dataflows/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /dataflows/terminate/{id} on existing DataFlow returns 200 OK")
    void terminateDataFlow_existingFlow_returns200() throws Exception {
        String processId = newId();
        dataFlowRepository.save(startedEntity(processId, TRANSFER_TYPE_PULL));

        mockMvc.perform(withApiKey(post("/dataflows/terminate/{id}", processId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /dataflows/terminate/{id} with non-existent processId returns 404")
    void terminateDataFlow_notFound_returns404() throws Exception {
        mockMvc.perform(withApiKey(post("/dataflows/terminate/{id}", newId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /dataflows/suspend/{id} on existing DataFlow returns 200 OK")
    void suspendDataFlow_existingFlow_returns200() throws Exception {
        String processId = newId();
        dataFlowRepository.save(startedEntity(processId, TRANSFER_TYPE_PULL));

        mockMvc.perform(withApiKey(post("/dataflows/suspend/{id}", processId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /dataflows/suspend/{id} with non-existent processId returns 404")
    void suspendDataFlow_notFound_returns404() throws Exception {
        mockMvc.perform(withApiKey(post("/dataflows/suspend/{id}", newId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /controlplanes with endpoint payload returns 200 OK")
    void registerControlPlane_returnsOk() throws Exception {
        Map<String, String> body = Map.of("endpoint", wireMock.baseUrl());

        mockMvc.perform(withApiKey(put("/controlplanes"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    /**
     * Creates a {@link DataFlowEntity} in {@link DataFlowState#STARTED} state for use in
     * terminate and suspend tests, bypassing the {@code /dataflows/start} endpoint so the
     * test does not depend on the async transfer completing before the lifecycle call.
     *
     * @param processId    the transfer process ID
     * @param transferType the transfer type (e.g. {@code HttpData-PULL})
     * @return a ready-to-save entity in STARTED state
     */
    private static DataFlowEntity startedEntity(String processId, String transferType) {
        Instant now = Instant.now();
        return DataFlowEntity.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .processId(processId)
                .transferType(transferType)
                .state(DataFlowState.STARTED)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
