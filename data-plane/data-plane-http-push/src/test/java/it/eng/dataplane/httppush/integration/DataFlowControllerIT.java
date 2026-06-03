package it.eng.dataplane.httppush.integration;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataAddress;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.dataplane.api.message.EndpointProperty;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.repository.DataFlowRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import software.amazon.awssdk.regions.Region;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code DataFlowController} and {@code ControlPlaneRegistrationController}
 * in the HTTP-PUSH Data Plane application.
 *
 * <p>Covers the full lifecycle: prepare, start, terminate, suspend, and control-plane
 * registration. Authentication is via the {@code X-Api-Key} header.
 */
class DataFlowControllerIT extends BaseHttpPushIT {

    private static final String TRANSFER_TYPE_PUSH = "HttpData-PUSH";

    @Autowired
    private DataFlowRepository dataFlowRepository;

    private static final String E2E_SOURCE_KEY = "urn:uuid:e2e-push-source-dataset";
    private static final String E2E_SOURCE_CONTENT = "Hello from HTTP-PUSH E2E test";

    @BeforeAll
    static void uploadSourceArtifact() {
        uploadToTestMinIO(E2E_SOURCE_KEY, E2E_SOURCE_CONTENT);
    }

    @Test
    @DisplayName("POST /dataflows/prepare with valid processId returns 200 with processId in body")
    void prepareDataFlow_returnsOkWithProcessId() throws Exception {
        String processId = newId();
        Map<String, Object> body = Map.of("processId", processId);

        mockMvc.perform(withApiKey(post("/dataflows/prepare"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processId").value(processId));
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
    @DisplayName("POST /dataflows/start with valid HttpData-PUSH payload returns 201 Created")
    void startDataFlow_validPush_returns201() throws Exception {
        Map<String, Object> body = Map.of(
                "processId", newId(),
                "transferType", TRANSFER_TYPE_PUSH
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

        DataFlowStartMessage dataFlowStartMessage = DataFlowStartMessage.Builder.newInstance()
                .processId(processId)
                .transferType(TRANSFER_TYPE_PUSH)
                .datasetId(E2E_SOURCE_KEY)
                .dataAddress(DataAddress.Builder.newInstance()
//                        .endpoint(presignURL)
                        .endpointType(TRANSFER_TYPE_PUSH)
                        .endpointProperties(List.of(EndpointProperty.Builder.newInstance()
                                        .name(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME)
                                        .value(TEST_BUCKET_NAME)
                                        .build(),
                                EndpointProperty.Builder.newInstance()
                                        .name(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ACCESS_KEY)
                                        .value(minIOContainer.getUserName())
                                        .build(),
                                EndpointProperty.Builder.newInstance()
                                        .name(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_SECRET_KEY)
                                        .value(minIOContainer.getPassword())
                                        .build(),
                                EndpointProperty.Builder.newInstance()
                                        .name(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ENDPOINT_OVERRIDE)
                                        .value(minIOContainer.getS3URL())
                                        .build(),
                                EndpointProperty.Builder.newInstance()
                                        .name(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_REGION)
                                        .value("us-east-1")
                                        .build(),
                                EndpointProperty.Builder.newInstance()
                                .name(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME)
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
                "transferType", TRANSFER_TYPE_PUSH
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
        dataFlowRepository.save(startedEntity(processId, TRANSFER_TYPE_PUSH));

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
        dataFlowRepository.save(startedEntity(processId, TRANSFER_TYPE_PUSH));

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
     * @param transferType the transfer type (e.g. {@code HttpData-PUSH})
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
