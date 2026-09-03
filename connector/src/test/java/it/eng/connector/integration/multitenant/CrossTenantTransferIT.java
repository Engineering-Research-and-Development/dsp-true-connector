package it.eng.connector.integration.multitenant;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Offer;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.service.AuthService;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test that seeds two tenants on a single connector instance and
 * exercises the full multi-tenant stack: catalog &rarr; automatic negotiation &rarr;
 * automatic HTTP-PULL data transfer &rarr; S3 storage.
 *
 * <p>The provider tenant hosts a catalog with a FILE artifact and both tenants have
 * automatic negotiation and automatic transfer enabled, so once the consumer initiates
 * negotiation and requests the transfer, the rest of the flow (agreement, transfer start,
 * download and completion) happens without further manual API calls.
 */
@Slf4j
public class CrossTenantTransferIT extends BaseIntegrationTest {

    private static final String PROVIDER_TENANT = "provider-mt4-tenant";
    private static final String CONSUMER_TENANT = "consumer-mt4-tenant";

    private static final int POLL_TIMEOUT_SECONDS = 30;
    private static final long POLL_INTERVAL_MS = 1000;

    @Autowired
    private CatalogRepository catalogRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DataServiceRepository dataServiceRepository;
    @Autowired
    private DistributionRepository distributionRepository;
    @Autowired
    private ArtifactRepository artifactRepository;
    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;
    @Autowired
    private TransferProcessRepository transferProcessRepository;
    @Autowired
    private S3ClientService s3ClientService;
    @Autowired
    private TenantService tenantService;

    @Autowired
    private PolicyEnforcementRepository policyEnforcementRepository;

    private String providerBucketName;
    private String consumerBucketName;
    private Catalog providerCatalog;
    private Dataset providerDataset;

    /** Populated during the test method — used for targeted cleanup in {@code @AfterEach}. */
    private String consumerPid;
    private String providerPid;
    private String agreementId;
    private String consumerTransferProcessId;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthService authService;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    public void seedTenantsAndProviderCatalog() throws Exception {
        consumerPid = null;
        providerPid = null;
        agreementId = null;
        consumerTransferProcessId = null;

        providerBucketName = ensureTenant(PROVIDER_TENANT);
        consumerBucketName = ensureTenant(CONSUMER_TENANT);
        assertNotEquals(providerBucketName, consumerBucketName, "Provider and consumer bucket names must be distinct");

        userRepository.save(User.builder()
                .id("consumer@example.com")
                .firstName("consumer first name")
                .lastName("consumer last name")
                .email("consumer@example.com")
                .password(encoder.encode("secret"))
                .enabled(true)
                .role(Role.ADMIN)
                .tenantId(CONSUMER_TENANT)
                .build());

        seedProviderCatalog();
    }

    @AfterEach
    public void cleanup() {
        TenantContextHolder.clear();

        if (providerDataset != null && providerBucketName != null
                && s3ClientService.fileExists(providerBucketName, providerDataset.getId())) {
            s3ClientService.deleteFile(providerBucketName, providerDataset.getId());
        }
        if (consumerTransferProcessId != null && consumerBucketName != null
                && s3ClientService.fileExists(consumerBucketName, consumerTransferProcessId)) {
            s3ClientService.deleteFile(consumerBucketName, consumerTransferProcessId);
        }

        if (consumerTransferProcessId != null) {
            transferProcessRepository.findByIdAndTenantId(consumerTransferProcessId, CONSUMER_TENANT)
                    .ifPresent(tp -> transferProcessRepository.deleteById(tp.getId()));
        }
        if (agreementId != null) {
            transferProcessRepository.findByAgreementIdAndTenantId(agreementId, PROVIDER_TENANT)
                    .ifPresent(tp -> transferProcessRepository.deleteById(tp.getId()));
        }
        if (consumerPid != null) {
            contractNegotiationRepository.findByConsumerPidAndTenantId(consumerPid, CONSUMER_TENANT)
                    .ifPresent(cn -> contractNegotiationRepository.deleteById(cn.getId()));
        }
        if (providerPid != null) {
            contractNegotiationRepository.findByProviderPidAndTenantId(providerPid, PROVIDER_TENANT)
                    .ifPresent(cn -> contractNegotiationRepository.deleteById(cn.getId()));
        }

        if (providerCatalog != null) {
            catalogRepository.deleteById(providerCatalog.getId());
        }
        if (providerDataset != null) {
            datasetRepository.deleteById(providerDataset.getId());
            distributionRepository.deleteAll(providerDataset.getDistribution());
            if (providerDataset.getArtifact() != null) {
                artifactRepository.deleteById(providerDataset.getArtifact().getId());
            }
            providerDataset.getDistribution().stream()
                    .map(distribution -> distribution.getAccessService())
                    .filter(java.util.Objects::nonNull)
                    .forEach(dataService -> dataServiceRepository.deleteById(dataService.getId()));
        }

        tenantRepository.deleteById(PROVIDER_TENANT);
        tenantRepository.deleteById(CONSUMER_TENANT);

        userRepository.deleteById("consumer@example.com");
    }

    @Test
    @DisplayName("Two tenants on a single connector instance complete automatic negotiation "
            + "and automatic HTTP-PULL transfer; artifact lands in consumer's S3 bucket")
    void crossTenantAutomaticNegotiationAndHttpPullTransfer_completesEndToEnd() throws Exception {
        // Consumer builds an offer that matches the provider's catalog offer exactly,
        // so it passes the provider's offer validation unchanged.
        Offer catalogOffer = providerDataset.getHasPolicy().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No offer found on provider dataset"));
        Offer offer = Offer.Builder.newInstance()
                .id(catalogOffer.getId())
                .target(providerDataset.getId())
                .permission(catalogOffer.getPermission())
                .build();

        // Consumer initiates negotiation toward the provider tenant's protocol endpoint.
        Map<String, Object> negotiationRequest = new LinkedHashMap<>();
        negotiationRequest.put("Forward-To", "http://localhost:8080/" + PROVIDER_TENANT);
        negotiationRequest.put("offer", NegotiationSerializer.serializePlainJsonNode(offer));

        String jwt = authService.login("consumer@example.com", "secret").accessToken();

        ResultActions negotiationResult = mockMvc.perform(
                post(ApiEndpoints.NEGOTIATION_V1 + "/request")
                        .header("Authorization", "Bearer " + jwt)
                        .content(NegotiationSerializer.serializePlain(negotiationRequest))
                        .contentType(MediaType.APPLICATION_JSON));

        negotiationResult.andExpect(status().isOk());
        ContractNegotiation initiatedNegotiation = readNegotiationResponse(negotiationResult);
        consumerPid = initiatedNegotiation.getConsumerPid();
        assertNotNull(consumerPid, "Consumer PID must be assigned after initiating negotiation");

        // Poll the consumer's negotiation record until automatic negotiation reaches FINALIZED
        // on both sides (consumer negotiation, provider negotiation).
        ContractNegotiation finalizedConsumerCn = pollNegotiationUntilState(
                () -> contractNegotiationRepository.findByConsumerPidAndTenantId(consumerPid, CONSUMER_TENANT),
                ContractNegotiationState.FINALIZED, "consumer");
        providerPid = finalizedConsumerCn.getProviderPid();
        agreementId = finalizedConsumerCn.getAgreement().getId();
        assertNotNull(agreementId, "Consumer negotiation must have an agreement once FINALIZED");

        pollNegotiationUntilState(
                () -> contractNegotiationRepository.findByProviderPidAndTenantId(providerPid, PROVIDER_TENANT),
                ContractNegotiationState.FINALIZED, "provider");

        // Automatic negotiation FINALIZE handling on the consumer side publishes
        // InitializeTransferProcess, creating an INITIALIZED TransferProcess for this agreement.
        TransferProcess initializedTransferProcess = pollTransferProcess(
                () -> transferProcessRepository.findByAgreementIdAndTenantId(agreementId, CONSUMER_TENANT),
                tp -> true, "consumer transfer process initialized");
        consumerTransferProcessId = initializedTransferProcess.getId();

        // Consumer requests the HTTP-PULL transfer.
        String transferRequestBody = """
                {"transferProcessId": "%s", "format": "%s"}
                """.formatted(consumerTransferProcessId, DataTransferFormat.HTTP_PULL.format());

        PolicyEnforcement peConsumer = policyEnforcementRepository.findByAgreementIdAndTenantId(agreementId, CONSUMER_TENANT)
                .orElseThrow(() -> new IllegalStateException("No PolicyEnforcement found for consumer agreement " + agreementId));
        log.info("Count from policy enforcement consumer {}", peConsumer.getCount());
        PolicyEnforcement peProvider = policyEnforcementRepository.findByAgreementIdAndTenantId(agreementId, PROVIDER_TENANT)
                .orElseThrow(() -> new IllegalStateException("No PolicyEnforcement found for provider agreement " + agreementId));
        log.info("Count from policy enforcement provider {}", peProvider.getCount());

        ResultActions transferResult = mockMvc.perform(
                post(ApiEndpoints.TRANSFER_DATATRANSFER_V1)
//                        .header(ApiTenantContextFilter.HEADER_X_TENANT_ID, CONSUMER_TENANT)
                        .header("Authorization", "Bearer " + jwt)
                        .content(transferRequestBody)
                        .contentType(MediaType.APPLICATION_JSON));

        transferResult.andExpect(status().isOk());

        // Automatic transfer chain: provider auto-starts, consumer auto-downloads, both
        // reach COMPLETED without further manual API calls.
        TransferProcess completedConsumerTp = pollTransferProcess(
                () -> transferProcessRepository.findByIdAndTenantId(consumerTransferProcessId, CONSUMER_TENANT),
                tp -> TransferState.COMPLETED.equals(tp.getState()), "consumer transfer completed");
        pollTransferProcess(
                () -> transferProcessRepository.findByAgreementIdAndTenantId(agreementId, PROVIDER_TENANT),
                tp -> TransferState.COMPLETED.equals(tp.getState()), "provider transfer completed");

        assertEquals(TransferState.COMPLETED, completedConsumerTp.getState(), "Consumer TP must be COMPLETED");
        assertTrue(completedConsumerTp.isDownloaded(), "Consumer TP isDownloaded must be true after HTTP_PULL download");

        // Artifact landed in the consumer tenant's own S3 bucket, stored under the
        // transfer process id, and NOT in the provider's bucket.
        assertTrue(s3ClientService.fileExists(consumerBucketName, consumerTransferProcessId),
                "Artifact must exist in consumer tenant's S3 bucket after automatic HTTP-PULL download");

        // simulate view artifact request from consumer API endpoint to verify that policyEnforcement will increase for consumer but not for provider
        ResultActions viewConsumerArtifactResult = mockMvc.perform(
                get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + completedConsumerTp.getId() + "/view")
//                        .header(ApiTenantContextFilter.HEADER_X_TENANT_ID, CONSUMER_TENANT)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // wait two seconds so that async event is processes - increase count access
        Thread.sleep(2000);

        PolicyEnforcement policyEnforcementConsumerAfterView = policyEnforcementRepository.findByAgreementIdAndTenantId(agreementId, CONSUMER_TENANT)
                .orElseThrow(() -> new IllegalStateException("No PolicyEnforcement CONSUMER found for consumer agreement " + agreementId));
        PolicyEnforcement policyEnforcementProviderAfterView = policyEnforcementRepository.findByAgreementIdAndTenantId(agreementId, PROVIDER_TENANT)
                .orElseThrow(() -> new IllegalStateException("No PolicyEnforcement PROVIDER found for consumer agreement " + agreementId));

        assertEquals(2, policyEnforcementConsumerAfterView.getCount());
        // Provider policyEnforcement should remain to 1 (default)
        assertEquals(1, policyEnforcementProviderAfterView.getCount());

        // check if we can download file using presignURL response
        String response = viewConsumerArtifactResult.andReturn().getResponse().getContentAsString();

        // response is presignedUrl for download data
        assertNotNull(response);
        URL url = URI.create(response).toURL();

        ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        while (readableByteChannel.read(buffer) != -1) {
            buffer.flip();
            out.write(buffer.array(), 0, buffer.limit());
            buffer.clear();
        }

        String channelString = out.toString(StandardCharsets.UTF_8);
        assertEquals("hello world", channelString);
    }

    // ---- setup helpers ----

    /**
     * Idempotently creates a tenant with automatic negotiation and automatic transfer
     * enabled, provisioning a dedicated S3 bucket via {@link TenantService#saveTenant(Tenant)}.
     *
     * @param tenantId the tenant identifier to ensure
     * @return the tenant's provisioned bucket name
     */
    private String ensureTenant(String tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getBucketName)
                .orElseGet(() -> tenantService.saveTenant(Tenant.Builder.newInstance()
                        .id(tenantId)
                        .name(tenantId)
                        .participantId("urn:example:" + tenantId)
                        .automaticNegotiation(true)
                        .automaticTransfer(true)
                        .enabled(true)
                        .build()).getBucketName());
    }

    /**
     * Seeds the provider tenant's catalog with a dataset backed by a FILE artifact and
     * uploads the artifact bytes to the provider's S3 bucket under the dataset id.
     *
     * @throws Exception if the artifact upload fails
     */
    private void seedProviderCatalog() throws Exception {
        providerCatalog = CatalogMockObjectUtil.createNewCatalog(PROVIDER_TENANT);
        providerDataset = providerCatalog.getDataset().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No dataset in provider catalog"));
        assertEquals(ArtifactType.FILE, providerDataset.getArtifact().getArtifactType(),
                "Provider dataset must use a FILE artifact for HTTP-PULL transfer");

        catalogRepository.save(providerCatalog);
        datasetRepository.saveAll(providerCatalog.getDataset());
        dataServiceRepository.saveAll(providerCatalog.getService());
        distributionRepository.saveAll(providerCatalog.getDistribution());
        artifactRepository.save(providerDataset.getArtifact());

        Map<String, String> destinationS3Properties = Map.of(
                S3Utils.OBJECT_KEY, providerDataset.getId(),
                S3Utils.BUCKET_NAME, providerBucketName,
                S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint(),
                S3Utils.REGION, s3Properties.getRegion(),
                S3Utils.ACCESS_KEY, s3Properties.getAccessKey(),
                S3Utils.SECRET_KEY, s3Properties.getSecretKey());

        var content = new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8));
        s3ClientService.uploadFile(content, destinationS3Properties,
                MediaType.TEXT_PLAIN_VALUE,
                ContentDisposition.attachment().filename("artifact.txt").build().toString()).get();
        Thread.sleep(2000); // wait for S3 upload to complete, as done elsewhere in the test suite
        log.info("Provider artifact uploaded to bucket '{}' with key '{}'", providerBucketName, providerDataset.getId());
    }

    // ---- polling helpers ----

    /** Functional supplier of an optional entity, re-evaluated on every polling iteration. */
    @FunctionalInterface
    private interface PollingSource<T> {
        java.util.Optional<T> get();
    }

    /**
     * Polls the given source until it returns a {@link ContractNegotiation} in the expected
     * state or the timeout is exceeded.
     *
     * @param source      supplier re-queried on every polling iteration
     * @param targetState the {@link ContractNegotiationState} to wait for
     * @param label       human-readable label used in the timeout assertion message
     * @return the {@link ContractNegotiation} once it reaches {@code targetState}
     * @throws InterruptedException if the polling sleep is interrupted
     */
    private ContractNegotiation pollNegotiationUntilState(PollingSource<ContractNegotiation> source,
            ContractNegotiationState targetState, String label) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (POLL_TIMEOUT_SECONDS * 1000L);
        while (System.currentTimeMillis() < deadline) {
            var found = source.get();
            if (found.isPresent() && targetState.equals(found.get().getState())) {
                return found.get();
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        fail("[" + label + "] contract negotiation did not reach " + targetState
                + " within " + POLL_TIMEOUT_SECONDS + "s");
        return null; // unreachable
    }

    /**
     * Polls the given source until it returns a {@link TransferProcess} satisfying the
     * given predicate or the timeout is exceeded.
     *
     * @param source    supplier re-queried on every polling iteration
     * @param predicate condition the found {@link TransferProcess} must satisfy
     * @param label     human-readable label used in the timeout assertion message
     * @return the {@link TransferProcess} once {@code predicate} is satisfied
     * @throws InterruptedException if the polling sleep is interrupted
     */
    private TransferProcess pollTransferProcess(PollingSource<TransferProcess> source,
            java.util.function.Predicate<TransferProcess> predicate, String label) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (POLL_TIMEOUT_SECONDS * 1000L);
        while (System.currentTimeMillis() < deadline) {
            var found = source.get();
            if (found.isPresent() && predicate.test(found.get())) {
                return found.get();
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        fail("[" + label + "] transfer process did not satisfy the expected condition "
                + "within " + POLL_TIMEOUT_SECONDS + "s");
        return null; // unreachable
    }

    /**
     * Parses the {@code /api/v1/negotiations/request} response body into a
     * {@link ContractNegotiation}.
     *
     * @param result the MockMvc result of the negotiation request call
     * @return the deserialized {@link ContractNegotiation}
     * @throws Exception if the response body cannot be parsed
     */
    private ContractNegotiation readNegotiationResponse(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        var javaType = jsonMapper.getTypeFactory()
                .constructParametricType(GenericApiResponse.class, ContractNegotiation.class);
        GenericApiResponse<ContractNegotiation> genericApiResponse = jsonMapper.readValue(json, javaType);
        assertNotNull(genericApiResponse, "Negotiation request response must not be null");
        assertTrue(genericApiResponse.isSuccess(), "Negotiation request must succeed");
        assertNotNull(genericApiResponse.getData(), "Negotiation request response must contain data");
        return genericApiResponse.getData();
    }
}
