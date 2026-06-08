package it.eng.connector.integration.datatransfer;

import com.github.tomakehurst.wiremock.client.WireMock;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Connector integration coverage for suspend/resume operator flows and startup recovery.
 */
public class DataTransferSuspendResumeIT extends BaseIntegrationTest {

    private static final String FILE_NAME = "large-transfer.txt";
    private static final long AWAIT_TIMEOUT_MILLIS = 10_000L;
    private static final String LARGE_TRANSFER_FIXTURE = "../ci/docker/test-data/large-transfer.txt";
    private static final String LARGE_TRANSFER_GENERATOR = "../ci/docker/generate-test-data.sh";

    @Autowired
    private TransferProcessRepository transferProcessRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;

    @Autowired
    private PolicyEnforcementRepository policyEnforcementRepository;

    @Autowired
    private S3ClientService s3ClientService;

    @Autowired
    private S3BucketProvisionService s3BucketProvisionService;

    @Autowired
    private DataTransferAPIService dataTransferAPIService;

    @AfterEach
    public void cleanup() {
        transferProcessRepository.deleteAll();
        agreementRepository.deleteAll();
        contractNegotiationRepository.deleteAll();
        policyEnforcementRepository.deleteAll();
        deleteBucketObjects();
        wireMock.resetAll();
    }

    @Test
    @DisplayName("HTTP-PULL suspend/resume keeps the downloaded artifact viewable after completion")
    @WithUserDetails(TestUtil.API_USER)
    public void httpPullSuspendResume_keepsDownloadedArtifactViewable() throws Exception {
        String seedContent = readLargeSeedArtifact();
        String sourceObjectKey = "source-" + createNewId();
        uploadArtifact(sourceObjectKey, seedContent);
        Agreement agreement = insertAgreement();
        TransferProcess transferProcess = buildHttpPullTransferProcess(agreement.getId(), sourceObjectKey);
        insertContractNegotiation(agreement, transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        transferProcessRepository.save(transferProcess);

        stubDataPlaneLifecycle(transferProcess.getId());
        stubPeerLifecycle(transferProcess);

        mockMvc.perform(get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcess.getId() + "/download")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        postDataFlowCallback(transferProcess.getId(), DataFlowState.STARTED, null);

        mockMvc.perform(put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcess.getId() + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        TransferProcess suspended = awaitTransferState(transferProcess.getId(), TransferState.SUSPENDED);
        assertEquals("SUSPENDED", suspended.getDataFlowState());
        assertEquals(IConstants.ROLE_CONSUMER, suspended.getSuspendedBy());
        assertFalse(suspended.isDownloadInProgress());

        mockMvc.perform(put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcess.getId() + "/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        postDataFlowCallback(transferProcess.getId(), DataFlowState.STARTED, null);
        uploadArtifact(transferProcess.getId(), seedContent);
        postDataFlowCallback(transferProcess.getId(), DataFlowState.COMPLETED, null);

        TransferProcess completed = awaitTransferState(transferProcess.getId(), TransferState.COMPLETED);
        assertTrue(completed.isDownloaded(), "Resume completion must mark the artifact as downloaded");
        assertEquals(transferProcess.getId(), completed.getDataId());
        assertEquals(seedContent, downloadViewedArtifact(transferProcess.getId()));
    }

    @Test
    @DisplayName("HTTP-PUSH suspend/resume keeps the pushed artifact viewable after completion")
    @WithUserDetails(TestUtil.API_USER)
    public void httpPushSuspendResume_keepsDownloadedArtifactViewable() throws Exception {
        String seedContent = readLargeSeedArtifact();
        Agreement agreement = insertAgreement();
        TransferProcess transferProcess = buildHttpPushTransferProcess(agreement.getId());
        insertContractNegotiation(agreement, transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        transferProcessRepository.save(transferProcess);

        stubDataPlaneLifecycle(transferProcess.getId());
        stubPeerLifecycle(transferProcess);

        mockMvc.perform(get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcess.getId() + "/download")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        postDataFlowCallback(transferProcess.getId(), DataFlowState.STARTED, null);

        mockMvc.perform(put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcess.getId() + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        TransferProcess suspended = awaitTransferState(transferProcess.getId(), TransferState.SUSPENDED);
        assertEquals("SUSPENDED", suspended.getDataFlowState());
        assertEquals(IConstants.ROLE_PROVIDER, suspended.getSuspendedBy());
        assertFalse(suspended.isDownloadInProgress());

        mockMvc.perform(put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcess.getId() + "/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        postDataFlowCallback(transferProcess.getId(), DataFlowState.STARTED, null);
        uploadArtifact(transferProcess.getId(), seedContent);
        postDataFlowCallback(transferProcess.getId(), DataFlowState.COMPLETED, null);

        TransferProcess completed = awaitTransferState(transferProcess.getId(), TransferState.COMPLETED);
        assertTrue(completed.isDownloaded(), "Resume completion must mark the artifact as downloaded");
        assertEquals(transferProcess.getId(), completed.getDataId());
        assertEquals(seedContent, downloadViewedArtifact(transferProcess.getId()));
    }

    @Test
    @DisplayName("Suspend rollback restores STARTED when the peer rejects the suspension")
    @WithUserDetails(TestUtil.API_USER)
    public void suspendRollback_restoresStartedStateWhenPeerRejectsSuspension() throws Exception {
        String seedContent = readLargeSeedArtifact();
        String sourceObjectKey = "source-" + createNewId();
        uploadArtifact(sourceObjectKey, seedContent);
        Agreement agreement = insertAgreement();
        TransferProcess transferProcess = buildHttpPullTransferProcess(agreement.getId(), sourceObjectKey)
                .withIsDownloadInProgress(true);
        insertContractNegotiation(agreement, transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        transferProcessRepository.save(transferProcess);

        WireMock.stubFor(WireMock.post("/dataflows/" + transferProcess.getId() + "/suspend")
                .willReturn(aResponse().withStatus(200)));
        WireMock.stubFor(WireMock.post("/dataflows/" + transferProcess.getId() + "/resume")
                .willReturn(aResponse().withStatus(200)));
        WireMock.stubFor(WireMock.post("/transfers/" + transferProcess.getProviderPid() + "/suspension")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(500)
                        .withBody("{\"message\":\"peer suspend rejected\"}")));

        mockMvc.perform(put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcess.getId() + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        TransferProcess rolledBack = awaitTransferState(transferProcess.getId(), TransferState.STARTED);
        assertEquals("STARTED", rolledBack.getDataFlowState());
        assertTrue(rolledBack.isDownloadInProgress());
        assertNull(rolledBack.getSuspendedBy());
    }

    @Test
    @DisplayName("Startup recovery suspends resumable in-progress transfers")
    public void startupRecovery_suspendsResumableTransfer() throws Exception {
        String seedContent = readLargeSeedArtifact();
        String sourceObjectKey = "source-" + createNewId();
        uploadArtifact(sourceObjectKey, seedContent);
        Agreement agreement = insertAgreement();
        TransferProcess transferProcess = buildHttpPullTransferProcess(agreement.getId(), sourceObjectKey)
                .withIsDownloadInProgress(true);
        insertContractNegotiation(agreement, transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        transferProcessRepository.save(transferProcess);

        WireMock.stubFor(WireMock.get("/dataflows/" + transferProcess.getId() + "/status")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{\"processId\":\"" + transferProcess.getId() + "\",\"state\":\"SUSPENDED\",\"resumable\":true}")));
        WireMock.stubFor(WireMock.post("/transfers/" + transferProcess.getProviderPid() + "/suspension")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{}")));

        ReflectionTestUtils.invokeMethod(dataTransferAPIService, "resetStaleDownloadingFlags");

        TransferProcess recovered = awaitTransferState(transferProcess.getId(), TransferState.SUSPENDED);
        assertEquals("SUSPENDED", recovered.getDataFlowState());
        assertEquals(IConstants.ROLE_CONSUMER, recovered.getSuspendedBy());
        assertFalse(recovered.isDownloadInProgress());
    }

    @Test
    @DisplayName("Startup recovery terminates unrecoverable in-progress transfers")
    public void startupRecovery_terminatesUnrecoverableTransfer() throws Exception {
        String seedContent = readLargeSeedArtifact();
        String sourceObjectKey = "source-" + createNewId();
        uploadArtifact(sourceObjectKey, seedContent);
        Agreement agreement = insertAgreement();
        TransferProcess transferProcess = buildHttpPullTransferProcess(agreement.getId(), sourceObjectKey)
                .withIsDownloadInProgress(true);
        insertContractNegotiation(agreement, transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        transferProcessRepository.save(transferProcess);

        WireMock.stubFor(WireMock.get("/dataflows/" + transferProcess.getId() + "/status")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{\"processId\":\"" + transferProcess.getId() + "\",\"state\":\"STARTED\",\"resumable\":false}")));
        WireMock.stubFor(WireMock.post("/transfers/" + transferProcess.getProviderPid() + "/termination")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{}")));

        ReflectionTestUtils.invokeMethod(dataTransferAPIService, "resetStaleDownloadingFlags");

        TransferProcess recovered = awaitTransferState(transferProcess.getId(), TransferState.TERMINATED);
        assertEquals(TransferState.TERMINATED, recovered.getState());
        assertEquals("unrecoverable error, start a new data transfer", recovered.getDataFlowErrorMessage());
        assertFalse(recovered.isDownloadInProgress());
    }

    /**
     * Polls until the transfer reaches the expected state.
     *
     * @param transferProcessId the transfer process identifier
     * @param expectedState the expected state
     * @return the latest persisted transfer process
     * @throws InterruptedException if the waiting thread is interrupted
     */
    protected TransferProcess awaitTransferState(String transferProcessId, TransferState expectedState)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
        TransferProcess latest = null;
        while (System.currentTimeMillis() < deadline) {
            latest = transferProcessRepository.findById(transferProcessId).orElse(null);
            if (latest != null && expectedState.equals(latest.getState())) {
                return latest;
            }
            TimeUnit.MILLISECONDS.sleep(200L);
        }
        assertNotNull(latest, "Transfer process must still exist while awaiting state");
        assertEquals(expectedState, latest.getState(), "Transfer process did not reach the expected state");
        return latest;
    }

    /**
     * Builds a STARTED HTTP-PULL consumer transfer process backed by a real presigned source URL.
     *
     * @param agreementId the agreement identifier
     * @param sourceObjectKey the S3 object key exposed as the source artifact
     * @return the unsaved transfer process fixture
     */
    protected TransferProcess buildHttpPullTransferProcess(String agreementId, String sourceObjectKey) {
        String sourceUrl = s3ClientService.generateGetPresignedUrl(
                s3Properties.getBucketName(),
                sourceObjectKey,
                Duration.ofMinutes(10));
        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(sourceUrl)
                .endpointType("https://w3id.org/idsa/v4.1/HTTP")
                .endpointProperties(List.of(
                        EndpointProperty.Builder.newInstance()
                                .name("https://w3id.org/edc/v0.0.1/ns/endpoint")
                                .value(sourceUrl)
                                .build(),
                        EndpointProperty.Builder.newInstance()
                                .name("https://w3id.org/edc/v0.0.1/ns/endpointType")
                                .value("https://w3id.org/idsa/v4.1/HTTP")
                                .build()))
                .build();

        return TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .agreementId(agreementId)
                .callbackAddress(wireMock.baseUrl())
                .dataAddress(dataAddress)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PULL.format())
                .role(IConstants.ROLE_CONSUMER)
                .tenantId(TENANT_ID)
                .datasetId("dataset-" + createNewId())
                .build();
    }

    /**
     * Builds a STARTED HTTP-PUSH provider transfer process using the test bucket as destination.
     *
     * @param agreementId the agreement identifier
     * @return the unsaved transfer process fixture
     */
    protected TransferProcess buildHttpPushTransferProcess(String agreementId) {
        String transferObjectKey = "push-destination-" + createNewId();
        List<EndpointProperty> endpointProperties = new ArrayList<>();
        for (Map.Entry<String, String> entry : createS3EndpointProperties(transferObjectKey).entrySet()) {
            endpointProperties.add(EndpointProperty.Builder.newInstance()
                    .name(entry.getKey())
                    .value(entry.getValue())
                    .build());
        }

        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpointProperties(endpointProperties)
                .build();

        return TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .agreementId(agreementId)
                .callbackAddress(wireMock.baseUrl())
                .dataAddress(dataAddress)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(TENANT_ID)
                .datasetId("dataset-" + createNewId())
                .build();
    }

    /**
     * Saves an agreement plus matching policy-enforcement counter so {@code viewData} is allowed.
     *
     * @return the persisted agreement
     */
    protected Agreement insertAgreement() {
        Agreement agreement = Agreement.Builder.newInstance()
                .id(createNewId())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(Instant.now().toString())
                .permission(List.of(Permission.Builder.newInstance()
                        .action(Action.USE)
                        .constraint(List.of(NegotiationMockObjectUtil.CONSTRAINT))
                        .build()))
                .build();
        agreementRepository.save(agreement);
        policyEnforcementRepository.save(new PolicyEnforcement(createNewId(), agreement.getId(), 0, null));
        return agreement;
    }

    /**
     * Inserts the finalized contract negotiation required by policy enforcement.
     *
     * @param agreement the persisted agreement
     * @param consumerPid the consumer PID bound to the transfer
     * @param providerPid the provider PID bound to the transfer
     */
    protected void insertContractNegotiation(Agreement agreement, String consumerPid, String providerPid) {
        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .id(createNewId())
                .agreement(agreement)
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .state(ContractNegotiationState.FINALIZED)
                .build();
        contractNegotiationRepository.save(contractNegotiation);
    }

    /**
     * Stubs the local dataplane start, suspend, and resume endpoints for the given transfer.
     *
     * @param transferProcessId the transfer process identifier
     */
    protected void stubDataPlaneLifecycle(String transferProcessId) {
        WireMock.stubFor(WireMock.post("/dataflows/start")
                .willReturn(aResponse().withStatus(200)));
        WireMock.stubFor(WireMock.post("/dataflows/" + transferProcessId + "/suspend")
                .willReturn(aResponse().withStatus(200)));
        WireMock.stubFor(WireMock.post("/dataflows/" + transferProcessId + "/resume")
                .willReturn(aResponse().withStatus(200)));
    }

    /**
     * Stubs peer DSP lifecycle endpoints for start, suspend, completion, and termination.
     *
     * @param transferProcess the transfer process under test
     */
    protected void stubPeerLifecycle(TransferProcess transferProcess) {
        String peerProcessId = IConstants.ROLE_CONSUMER.equals(transferProcess.getRole())
                ? transferProcess.getProviderPid()
                : transferProcess.getConsumerPid();
        WireMock.stubFor(WireMock.post("/transfers/" + peerProcessId + "/start")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{}")));
        WireMock.stubFor(WireMock.post("/transfers/" + peerProcessId + "/suspension")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{}")));
        WireMock.stubFor(WireMock.post("/transfers/" + peerProcessId + "/completion")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{}")));
        WireMock.stubFor(WireMock.post("/transfers/" + peerProcessId + "/termination")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{}")));
    }

    /**
     * Posts a canonical dataplane callback for the current transfer.
     *
     * @param transferProcessId the transfer process identifier
     * @param state the state to report
     * @param errorMessage optional error message
     * @throws Exception if the HTTP request fails
     */
    protected void postDataFlowCallback(String transferProcessId, DataFlowState state, String errorMessage) throws Exception {
        String endpoint = switch (state) {
            case STARTED -> ApiEndpoints.DATAFLOW_CALLBACK_STARTED;
            case COMPLETED -> ApiEndpoints.DATAFLOW_CALLBACK_COMPLETED;
            case TERMINATED -> ApiEndpoints.DATAFLOW_CALLBACK_ERRORED;
            default -> throw new IllegalArgumentException("Unsupported callback state: " + state);
        };
        ResultActions result = mockMvc.perform(
                post(endpoint.replace("{processId}", transferProcessId))
                        .header("X-Api-Key", DP_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildStatusMessageJson(transferProcessId, state, errorMessage)));
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    /**
     * Uploads an artifact to the shared test bucket.
     *
     * @param objectKey the destination object key
     * @param content the artifact content
     * @throws Exception if the upload fails
     */
    protected void uploadArtifact(String objectKey, String content) throws Exception {
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(FILE_NAME)
                .build();
        try (InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            s3ClientService.uploadFile(
                    inputStream,
                    createS3EndpointProperties(objectKey),
                    MediaType.TEXT_PLAIN_VALUE,
                    contentDisposition.toString()).get();
        }
    }

    /**
     * Reads the downloaded artifact back through the public view endpoint.
     *
     * @param transferProcessId the completed transfer identifier
     * @return the downloaded artifact body
     * @throws Exception if the view request or artifact fetch fails
     */
    protected String downloadViewedArtifact(String transferProcessId) throws Exception {
        ResultActions result = mockMvc.perform(
                get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessId + "/view")
                        .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        String presignedUrl = result.andReturn().getResponse().getContentAsString();
        URI.create(presignedUrl);
        return downloadUrl(presignedUrl);
    }

    /**
     * Downloads a presigned URL and returns the response body as UTF-8 text.
     *
     * @param presignedUrl the presigned URL to fetch
     * @return the downloaded body
     * @throws IOException if the HTTP request fails
     */
    protected String downloadUrl(String presignedUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(presignedUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        assertEquals(200, connection.getResponseCode(), "Presigned artifact URL must be downloadable");
        try (InputStream inputStream = connection.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Ensures the deterministic large transfer fixture exists and returns its content.
     *
     * @return the large test artifact content
     * @throws Exception if the fixture cannot be generated or read
     */
    protected String readLargeSeedArtifact() throws Exception {
        Path fixturePath = Path.of(LARGE_TRANSFER_FIXTURE).normalize();
        if (!Files.exists(fixturePath)) {
            Process process = new ProcessBuilder("bash", LARGE_TRANSFER_GENERATOR)
                    .directory(Path.of(".").toAbsolutePath().normalize().toFile())
                    .inheritIO()
                    .start();
            int exitCode = process.waitFor();
            assertEquals(0, exitCode, "Large transfer fixture generator must exit successfully");
        }
        return Files.readString(fixturePath, StandardCharsets.UTF_8);
    }

    /**
     * Removes every object from the shared test bucket.
     */
    protected void deleteBucketObjects() {
        if (!s3BucketProvisionService.bucketExists(s3Properties.getBucketName())) {
            return;
        }
        List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());
        if (files == null) {
            return;
        }
        for (String file : files) {
            s3ClientService.deleteFile(s3Properties.getBucketName(), file);
        }
    }

    /**
     * Builds the minimal JSON payload accepted by the canonical dataplane callback endpoints.
     *
     * @param processId the transfer process identifier
     * @param state the dataplane state
     * @param errorMessage optional error message
     * @return serialized JSON payload
     */
    protected String buildStatusMessageJson(String processId, DataFlowState state, String errorMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"processId\":\"").append(processId).append("\"");
        builder.append(",\"state\":\"").append(state.name()).append("\"");
        if (errorMessage != null) {
            builder.append(",\"errorMessage\":\"").append(errorMessage).append("\"");
        }
        builder.append("}");
        return builder.toString();
    }
}
