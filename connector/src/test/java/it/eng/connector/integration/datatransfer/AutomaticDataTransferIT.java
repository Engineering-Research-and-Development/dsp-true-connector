package it.eng.connector.integration.datatransfer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.ApplicationConnector;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that starts two real Spring Boot application instances — one acting as
 * Consumer (port 8184) and one as Provider (port 8285) — each backed by its own MongoDB
 * database on a shared Testcontainers MongoDB container, and each with its own MinIO instance.
 *
 * <p>The test inserts pre-initialized {@link TransferProcess} records on both sides, then
 * triggers automatic transfer via the consumer's API and polls both repositories until the
 * expected terminal state ({@code COMPLETED} or {@code TERMINATED}) is reached.
 *
 * <p>A third "WireMock consumer" instance (port 8386) is used for failure scenarios where
 * the provider cannot reach the consumer callback — the WireMock server (port 9100)
 * intercepts and returns HTTP 500, exhausting the provider's retry budget.
 */
@Slf4j
@Testcontainers
public class AutomaticDataTransferIT {

    // ── ports ─────────────────────────────────────────────────────────────────────
    private static final int CONSUMER_PORT          = 8184;
    private static final int PROVIDER_PORT          = 8285;
    private static final int WIREMOCK_CONSUMER_PORT = 8386;
    private static final int WIREMOCK_PORT          = 9100;

    private static final String CONSUMER_BASE_URL          = "http://localhost:" + CONSUMER_PORT;
    private static final String PROVIDER_BASE_URL          = "http://localhost:" + PROVIDER_PORT;
    private static final String WIREMOCK_CONSUMER_BASE_URL = "http://localhost:" + WIREMOCK_CONSUMER_PORT;

    // Basic auth credentials matching initial_data.json
    private static final String ADMIN_CREDENTIALS =
            Base64.getEncoder().encodeToString("admin@mail.com:password".getBytes(StandardCharsets.UTF_8));

    private static final int POLL_TIMEOUT_SECONDS = 60;
    private static final int POLL_INTERVAL_MS     = 500;

    // ── containers ────────────────────────────────────────────────────────────────
    @SuppressWarnings("resource")
    private static final GenericContainer<?> mongoDBContainer =
            new GenericContainer<>(DockerImageName.parse("mongo:7.0.12"))
                    .withExposedPorts(27017)
                    .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))
                    .withReuse(false);

    private static final MinIOContainer providerMinIO =
            new MinIOContainer(DockerImageName.parse("minio/minio"))
                    .withReuse(false);

    private static final MinIOContainer consumerMinIO =
            new MinIOContainer(DockerImageName.parse("minio/minio"))
                    .withReuse(false);

    // ── Spring Boot contexts ──────────────────────────────────────────────────────
    private static ConfigurableApplicationContext consumerCtx;
    private static ConfigurableApplicationContext providerCtx;

    /**
     * Consumer instance whose {@code application.callback.address} points to WireMock.
     * The provider sends protocol messages (e.g. TransferStartMessage) back to WireMock,
     * which intercepts and returns an error — triggering retry logic.
     */
    private static ConfigurableApplicationContext wiremockConsumerCtx;

    // ── WireMock ──────────────────────────────────────────────────────────────────
    /** Standalone WireMock server that intercepts provider→consumer protocol messages. */
    private static WireMockServer wireMockServer;

    // ── HTTP client ───────────────────────────────────────────────────────────────
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    /** DatasetId from the provider catalog — set in {@link #populateProviderCatalog()}. */
    private static String datasetId;

    // ── lifecycle ────────────────────────────────────────────────────────────────

    @BeforeAll
    static void startApplications() {
        mongoDBContainer.start();
        providerMinIO.start();
        consumerMinIO.start();

        String mongoHost = mongoDBContainer.getHost();
        int    mongoPort = mongoDBContainer.getMappedPort(27017);

        // ── WireMock — intercepts provider→consumer protocol messages ─────────────
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
        log.info("WireMock started on port {}", WIREMOCK_PORT);

        // ── Provider — source artifact lives in providerMinIO ─────────────────────
        providerCtx = startInstance(mongoHost, mongoPort, PROVIDER_PORT,
                "provider", "provider_db", PROVIDER_BASE_URL,
                providerMinIO.getS3URL(), providerMinIO.getUserName(), providerMinIO.getPassword(),
                "provider-bucket");

        // ── Consumer — downloaded artifact will land in consumerMinIO ─────────────
        consumerCtx = startInstance(mongoHost, mongoPort, CONSUMER_PORT,
                "consumer", "consumer_db", CONSUMER_BASE_URL,
                consumerMinIO.getS3URL(), consumerMinIO.getUserName(), consumerMinIO.getPassword(),
                "consumer-bucket");

        // ── WireMock consumer — callbackAddress points to WireMock ────────────────
        // Provider sends TransferStartMessage to http://localhost:WIREMOCK_PORT/consumer/transfers/{pid}/start.
        // WireMock intercepts and returns HTTP 500 → triggers provider's retry loop.
        wiremockConsumerCtx = startInstance(mongoHost, mongoPort, WIREMOCK_CONSUMER_PORT,
                "consumer-wiremock", "consumer_wiremock_db",
                "http://localhost:" + WIREMOCK_PORT,
                consumerMinIO.getS3URL(), consumerMinIO.getUserName(), consumerMinIO.getPassword(),
                "consumer-bucket");

        populateProviderCatalog();
    }

    /**
     * Starts a single Spring Boot application instance with all properties supplied via
     * system properties — the highest-priority source in Spring Boot's resolution order.
     *
     * @param mongoHost       MongoDB host
     * @param mongoPort       MongoDB mapped port
     * @param serverPort      HTTP port for this instance
     * @param appName         Spring application name
     * @param database        MongoDB database name
     * @param callbackAddress callback base address for this instance
     * @param s3Endpoint      MinIO S3 URL — may be {@code null}
     * @param s3AccessKey     MinIO access key — may be {@code null}
     * @param s3SecretKey     MinIO secret key — may be {@code null}
     * @param bucketName      S3 bucket name — may be {@code null}
     * @return the running {@link ConfigurableApplicationContext}
     */
    private static ConfigurableApplicationContext startInstance(String mongoHost, int mongoPort,
                                                                int serverPort, String appName,
                                                                String database, String callbackAddress,
                                                                String s3Endpoint, String s3AccessKey,
                                                                String s3SecretKey, String bucketName) {
        System.setProperty("server.port", String.valueOf(serverPort));
        System.setProperty("spring.application.name", appName);
        System.setProperty("spring.data.mongodb.host", mongoHost);
        System.setProperty("spring.data.mongodb.port", String.valueOf(mongoPort));
        System.setProperty("spring.data.mongodb.database", database);
        System.setProperty("application.callback.address", callbackAddress);
        System.setProperty("application.automatic.negotiation", "true");
        System.setProperty("application.automatic.negotiation.retry.max", "3");
        System.setProperty("application.automatic.negotiation.retry.delay.ms", "500");
        System.setProperty("application.automatic.transfer", "true");
        System.setProperty("application.automatic.transfer.retry.max", "3");
        System.setProperty("application.automatic.transfer.retry.delay.ms", "500");
        System.setProperty("server.ssl.enabled", "false");
        System.setProperty("application.usagecontrol.enabled", "false");

        if (s3Endpoint != null) {
            System.setProperty("s3.endpoint", s3Endpoint);
            System.setProperty("s3.externalPresignedEndpoint", s3Endpoint);
            System.setProperty("s3.accessKey", s3AccessKey);
            System.setProperty("s3.secretKey", s3SecretKey);
            System.setProperty("s3.region", "us-east-1");
            System.setProperty("s3.bucketName", bucketName != null ? bucketName : "provider-bucket");
        }

        try {
            var app = new SpringApplicationBuilder(ApplicationConnector.class)
                    .addCommandLineProperties(false)
                    .build();
            return app.run();
        } finally {
            // Clear system properties so they don't leak into the next context started in this JVM.
            System.clearProperty("server.port");
            System.clearProperty("spring.application.name");
            System.clearProperty("spring.data.mongodb.host");
            System.clearProperty("spring.data.mongodb.port");
            System.clearProperty("spring.data.mongodb.database");
            System.clearProperty("application.callback.address");
            System.clearProperty("application.automatic.negotiation");
            System.clearProperty("application.automatic.negotiation.retry.max");
            System.clearProperty("application.automatic.negotiation.retry.delay.ms");
            System.clearProperty("application.automatic.transfer");
            System.clearProperty("application.automatic.transfer.retry.max");
            System.clearProperty("application.automatic.transfer.retry.delay.ms");
            System.clearProperty("server.ssl.enabled");
            System.clearProperty("application.usagecontrol.enabled");
            System.clearProperty("s3.endpoint");
            System.clearProperty("s3.externalPresignedEndpoint");
            System.clearProperty("s3.accessKey");
            System.clearProperty("s3.secretKey");
            System.clearProperty("s3.region");
            System.clearProperty("s3.bucketName");
        }
    }

    @AfterAll
    static void stopApplications() {
        if (consumerCtx != null) {
            consumerCtx.close();
        }
        if (wiremockConsumerCtx != null) {
            wiremockConsumerCtx.close();
        }
        if (providerCtx != null) {
            providerCtx.close();
        }
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
        mongoDBContainer.stop();
        providerMinIO.stop();
        consumerMinIO.stop();
    }

    // ── catalog + artifact setup ──────────────────────────────────────────────────

    /**
     * Saves a catalog with a dataset and matching offer into the provider's MongoDB and
     * uploads the artifact file to the provider's MinIO instance. Sets the static
     * {@link #datasetId} field for use in per-test {@link TransferProcess} fixtures.
     */
    private static void populateProviderCatalog() {
        var catalogRepository      = providerCtx.getBean(CatalogRepository.class);
        var datasetRepository      = providerCtx.getBean(DatasetRepository.class);
        var dataServiceRepository  = providerCtx.getBean(DataServiceRepository.class);
        var distributionRepository = providerCtx.getBean(DistributionRepository.class);
        var artifactRepository     = providerCtx.getBean(ArtifactRepository.class);
        var s3ClientService        = providerCtx.getBean(S3ClientService.class);
        var s3Properties           = providerCtx.getBean(S3Properties.class);

        Catalog catalog = CatalogMockObjectUtil.createNewCatalog();
        Dataset dataset = catalog.getDataset().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No dataset in catalog"));

        datasetId = dataset.getId();
        log.info("Provider catalog populated — datasetId='{}'", datasetId);

        // Persist catalog data into provider MongoDB
        catalogRepository.save(catalog);
        datasetRepository.saveAll(catalog.getDataset());
        dataServiceRepository.saveAll(catalog.getService());
        distributionRepository.saveAll(catalog.getDistribution());
        if (dataset.getArtifact() != null) {
            artifactRepository.save(dataset.getArtifact());
        }

        // Add an HTTP_PUSH distribution to the dataset so that checkSupportedFormats
        // passes for HTTP_PUSH transfer requests as well as HTTP_PULL.
        Distribution existingDist = dataset.getDistribution().iterator().next();
        Distribution httpPushDistribution = Distribution.Builder.newInstance()
                .format(DataTransferFormat.HTTP_PUSH.format())
                .accessService(existingDist.getAccessService())
                .build();
        distributionRepository.save(httpPushDistribution);

        // Rebuild the dataset with both distributions (HTTP_PULL + HTTP_PUSH) and re-save.
        var bothDistributions = new HashSet<>(dataset.getDistribution());
        bothDistributions.add(httpPushDistribution);
        Dataset datasetWithBothDists = Dataset.Builder.newInstance()
                .hasPolicy(dataset.getHasPolicy())
                .distribution(bothDistributions)
                .build();
        Dataset updatedDataset = dataset.updateInstance(datasetWithBothDists);
        datasetRepository.save(updatedDataset);
        log.info("Added HTTP_PUSH distribution to dataset '{}'", datasetId);

        // Upload artifact to provider MinIO with key = datasetId
        // (DataTransferAPIService.startTransfer generates a presigned URL using this key)
        Map<String, String> destinationS3Properties = Map.of(
                S3Utils.OBJECT_KEY,        datasetId,
                S3Utils.BUCKET_NAME,       s3Properties.getBucketName(),
                S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint(),
                S3Utils.REGION,            s3Properties.getRegion(),
                S3Utils.ACCESS_KEY,        s3Properties.getAccessKey(),
                S3Utils.SECRET_KEY,        s3Properties.getSecretKey()
        );

        try {
            var content = new ByteArrayInputStream("artifact-content".getBytes(StandardCharsets.UTF_8));
            s3ClientService.uploadFile(content, destinationS3Properties,
                    MediaType.TEXT_PLAIN_VALUE,
                    ContentDisposition.attachment().filename("artifact.txt").build().toString()).get();
            log.info("Provider artifact uploaded to S3 with key '{}'", datasetId);
            Thread.sleep(2000); // wait for S3 upload to complete
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload provider artifact to MinIO", e);
        }
    }

    // ── fixture helper ────────────────────────────────────────────────────────────

    /**
     * Creates INITIALIZED {@link TransferProcess} records on both Provider and Consumer sides,
     * using a shared {@code agreementId} so the provider can match the incoming
     * {@code TransferRequestMessage} to its pre-initialized record via
     * {@code findByAgreementId}.
     *
     * <p>Provider TP: {@code role=PROVIDER}, {@code consumerPid=TEMPORARY_CONSUMER_PID},
     * a generated {@code providerPid}, {@code callbackAddress=consumerCallbackUrl/consumer}
     * (placeholder — overwritten by {@code initiateDataTransfer} with the actual value from
     * the {@code TransferRequestMessage}), and {@code datasetId} for artifact lookup.
     *
     * <p>Consumer TP: {@code role=CONSUMER}, a generated {@code consumerPid},
     * {@code providerPid=TEMPORARY_PROVIDER_PID}, {@code callbackAddress=PROVIDER_BASE_URL}
     * (where the consumer sends the {@code TransferRequestMessage}).
     *
     * @param providerRepository  provider's {@link TransferProcessRepository} bean
     * @param consumerRepository  consumer's (or wiremock-consumer's) {@link TransferProcessRepository} bean
     * @param agreementId         unique agreement id for this test run
     * @param consumerCallbackUrl consumer callback base URL — only used as placeholder
     *                            for the provider's pre-initialized TP {@code callbackAddress}
     * @return the consumer-side {@link TransferProcess} internal MongoDB id (used to trigger the API)
     */
    private static String createTransferProcessFixture(
            TransferProcessRepository providerRepository,
            TransferProcessRepository consumerRepository,
            String agreementId,
            String consumerCallbackUrl) {

        String consumerPid = "urn:uuid:" + UUID.randomUUID();
        String providerPid = "urn:uuid:" + UUID.randomUUID();

        // Provider side — INITIALIZED state so initiateDataTransfer can find it by agreementId.
        // The callbackAddress here is a safe placeholder: initiateDataTransfer overwrites it
        // with TransferRequestMessage.callbackAddress = consumerInstance.consumerCallbackAddress().
        TransferProcess providerTp = TransferProcess.Builder.newInstance()
                .agreementId(agreementId)
                .callbackAddress(consumerCallbackUrl + "/consumer")
                .datasetId(datasetId)
                .state(TransferState.INITIALIZED)
                .role(IConstants.ROLE_PROVIDER)
                .consumerPid(IConstants.TEMPORARY_CONSUMER_PID)
                .providerPid(providerPid)
                .build();
        providerRepository.save(providerTp);
        log.info("Provider TP created — id='{}', agreementId='{}'", providerTp.getId(), agreementId);

        // Consumer side — callbackAddress is the provider's base URL where the consumer
        // sends TransferRequestMessage (via DataTransferCallback.getConsumerDataTransferRequest).
        TransferProcess consumerTp = TransferProcess.Builder.newInstance()
                .agreementId(agreementId)
                .callbackAddress(PROVIDER_BASE_URL)
                .datasetId(datasetId)
                .state(TransferState.INITIALIZED)
                .role(IConstants.ROLE_CONSUMER)
                .consumerPid(consumerPid)
                .providerPid(IConstants.TEMPORARY_PROVIDER_PID)
                .build();
        consumerRepository.save(consumerTp);
        log.info("Consumer TP created — id='{}', consumerPid='{}', agreementId='{}'",
                consumerTp.getId(), consumerPid, agreementId);

        return consumerTp.getId();
    }

    // ── tests ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Automatic data transfer - HTTP_PULL - both consumer and provider reach COMPLETED")
    void automaticDataTransfer_httpPull_reachesCompletedOnBothSides() throws Exception {
        var providerTpRepo = providerCtx.getBean(TransferProcessRepository.class);
        var consumerTpRepo = consumerCtx.getBean(TransferProcessRepository.class);
        String agreementId = "urn:uuid:auto-transfer-happy-" + UUID.randomUUID();

        // ── create INITIALIZED TPs on both sides ──────────────────────────────────
        String consumerTpId = createTransferProcessFixture(
                providerTpRepo, consumerTpRepo, agreementId, CONSUMER_BASE_URL);

        // ── Consumer triggers data transfer via API ────────────────────────────────
        // DataTransferAPIService.requestTransfer sends TransferRequestMessage to provider
        // Provider's initiateDataTransfer (auto) fires AutoTransferStartEvent → startTransfer
        // Consumer receives TransferStartMessage → AutoTransferDownloadEvent → downloadData → COMPLETED
        // Provider receives TransferCompletionMessage → COMPLETED
        String requestBody = """
                {"transferProcessId": "%s", "format": "HttpData-PULL"}
                """.formatted(consumerTpId);

        HttpResponse<String> response = post(
                CONSUMER_BASE_URL + ApiEndpoints.TRANSFER_DATATRANSFER_V1,
                requestBody, ADMIN_CREDENTIALS);

        assertEquals(200, response.statusCode(),
                "Consumer requestTransfer API failed: " + response.body());
        log.info("Transfer requested — consumerTpId='{}'", consumerTpId);

        // ── Poll Consumer TP until COMPLETED ──────────────────────────────────────
        TransferProcess completedConsumerTp = pollUntilTransferState(
                consumerTpRepo, consumerTpId, TransferState.COMPLETED, "consumer");

        // ── Poll Provider TP until COMPLETED ──────────────────────────────────────
        // Look up by agreementId since only the consumer TP id is known upfront.
        TransferProcess completedProviderTp = pollUntilTransferStateByAgreementId(
                providerTpRepo, agreementId, TransferState.COMPLETED, "provider");

        // ── Assertions ────────────────────────────────────────────────────────────
        assertEquals(TransferState.COMPLETED, completedConsumerTp.getState(),
                "Consumer TP must be COMPLETED");
        assertEquals(TransferState.COMPLETED, completedProviderTp.getState(),
                "Provider TP must be COMPLETED");
        assertTrue(completedConsumerTp.isDownloaded(),
                "Consumer TP isDownloaded must be true after HTTP_PULL download");
        assertNotNull(completedConsumerTp.getDataId(),
                "Consumer TP dataId must be set after successful download");

        // Verify artifact landed in Consumer's MinIO.
        // HttpPullTransferStrategy stores the artifact with key = transferProcess.getId() = consumerTpId.
        var consumerS3      = consumerCtx.getBean(S3ClientService.class);
        var consumerS3Props = consumerCtx.getBean(S3Properties.class);
        assertTrue(consumerS3.fileExists(consumerS3Props.getBucketName(), consumerTpId),
                "Artifact must exist in Consumer MinIO after download");

        log.info("Automatic HTTP_PULL transfer completed successfully — agreementId='{}'", agreementId);
    }

    @Test
    @DisplayName("Automatic data transfer - HTTP_PUSH - both consumer and provider reach COMPLETED")
    void automaticDataTransfer_httpPush_reachesCompletedOnBothSides() throws Exception {
        var providerTpRepo = providerCtx.getBean(TransferProcessRepository.class);
        var consumerTpRepo = consumerCtx.getBean(TransferProcessRepository.class);
        String agreementId = "urn:uuid:auto-transfer-push-" + UUID.randomUUID();

        // ── create INITIALIZED TPs on both sides ──────────────────────────────────
        String consumerTpId = createTransferProcessFixture(
                providerTpRepo, consumerTpRepo, agreementId, CONSUMER_BASE_URL);

        // ── Consumer triggers data transfer via API with HTTP_PUSH format ──────────
        // DataTransferAPIService.requestTransfer builds consumer S3 dataAddress (bucket name,
        // region, objectKey = consumerTpId, credentials) and includes it in the
        // TransferRequestMessage sent to the provider.
        // Provider's initiateDataTransfer fires AutoTransferStartEvent → processStart:
        //   1. startTransfer: sends TransferStartMessage to consumer → Provider STARTED.
        //   2. HTTP_PUSH chain: HttpPushTransferStrategy generates a presigned GET URL for
        //      the artifact in provider MinIO, downloads it, then uploads it to consumer MinIO
        //      using the dataAddress from the request → provider sends TransferCompletionMessage
        //      → Provider COMPLETED.
        // Consumer receives TransferStartMessage → STARTED (no auto-download for HTTP_PUSH).
        // Consumer receives TransferCompletionMessage → COMPLETED.
        String requestBody = """
                {"transferProcessId": "%s", "format": "HttpData-PUSH"}
                """.formatted(consumerTpId);

        HttpResponse<String> response = post(
                CONSUMER_BASE_URL + ApiEndpoints.TRANSFER_DATATRANSFER_V1,
                requestBody, ADMIN_CREDENTIALS);

        assertEquals(200, response.statusCode(),
                "Consumer requestTransfer API failed: " + response.body());
        log.info("HTTP_PUSH Transfer requested — consumerTpId='{}'", consumerTpId);

        // ── Poll Consumer TP until COMPLETED ──────────────────────────────────────
        // Consumer transitions: INITIALIZED → REQUESTED → STARTED → COMPLETED
        // (COMPLETED is set when the consumer receives TransferCompletionMessage from provider)
        TransferProcess completedConsumerTp = pollUntilTransferState(
                consumerTpRepo, consumerTpId, TransferState.COMPLETED, "consumer");

        // ── Poll Provider TP until COMPLETED ──────────────────────────────────────
        // Provider transitions: INITIALIZED → REQUESTED → STARTED → COMPLETED
        // (COMPLETED is set after HttpPushTransferStrategy pushes data and sends completion)
        TransferProcess completedProviderTp = pollUntilTransferStateByAgreementId(
                providerTpRepo, agreementId, TransferState.COMPLETED, "provider");

        // ── Assertions ────────────────────────────────────────────────────────────
        assertEquals(TransferState.COMPLETED, completedConsumerTp.getState(),
                "Consumer TP must be COMPLETED");
        assertEquals(TransferState.COMPLETED, completedProviderTp.getState(),
                "Provider TP must be COMPLETED");
        assertTrue(completedConsumerTp.isDownloaded(),
                "Consumer TP isDownloaded must be true after HTTP_PUSH");
        assertNotNull(completedConsumerTp.getDataId(),
                "Consumer TP dataId must be set after HTTP_PUSH");

        // For HTTP_PUSH the provider pushes the artifact directly into consumer MinIO.
        // DataTransferAPIService.requestTransfer sets objectKey = consumerTpId (the
        // INITIALIZED TP's MongoDB id), so that is the key under which the file lands.
        var consumerS3      = consumerCtx.getBean(S3ClientService.class);
        var consumerS3Props = consumerCtx.getBean(S3Properties.class);
        assertTrue(consumerS3.fileExists(consumerS3Props.getBucketName(), consumerTpId),
                "Artifact must exist in Consumer MinIO after HTTP_PUSH");

        log.info("Automatic HTTP_PUSH transfer completed successfully — agreementId='{}'", agreementId);
    }

    @Test
    @DisplayName("Automatic data transfer - WireMock intercepts TransferStartMessage, "
               + "both provider and wiremock-consumer reach TERMINATED after retry exhaustion")
    void automaticDataTransfer_providerCannotSendStartMessage_bothReachTerminated() throws Exception {
        // ── configure WireMock stubs ──────────────────────────────────────────────
        // 1. Intercept TransferStartMessage with 500 → triggers provider retry loop.
        wireMockServer.stubFor(
                WireMock.post(urlPathMatching("/consumer/transfers/.+/start"))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"reason\": \"simulated consumer unreachable\"}")));

        // 2. Proxy TransferTerminationMessage to the real WireMock-consumer (port 8386)
        //    so its TP is also transitioned to TERMINATED via the normal protocol handler.
        wireMockServer.stubFor(
                WireMock.post(urlPathMatching("/consumer/transfers/.+/termination"))
                        .willReturn(aResponse()
                                .proxiedFrom("http://localhost:" + WIREMOCK_CONSUMER_PORT)));

        var providerTpRepo   = providerCtx.getBean(TransferProcessRepository.class);
        var wmConsumerTpRepo = wiremockConsumerCtx.getBean(TransferProcessRepository.class);
        String agreementId   = "urn:uuid:auto-transfer-retry-" + UUID.randomUUID();

        // ── create INITIALIZED TPs ────────────────────────────────────────────────
        // WireMock-consumer's application.callback.address = http://localhost:WIREMOCK_PORT.
        // Its consumerCallbackAddress() = http://localhost:9100/consumer.
        // Provider will send TransferStartMessage to http://localhost:9100/consumer/transfers/{pid}/start
        // which WireMock intercepts → 500. After retry exhaustion provider terminates gracefully
        // → sends termination to http://localhost:9100/consumer/transfers/{pid}/termination
        // which WireMock proxies to the real WireMock-consumer at port 8386.
        String wmConsumerTpId = createTransferProcessFixture(
                providerTpRepo, wmConsumerTpRepo, agreementId,
                "http://localhost:" + WIREMOCK_CONSUMER_PORT); // placeholder only

        // ── WireMock-consumer triggers data transfer via API ──────────────────────
        String requestBody = """
                {"transferProcessId": "%s", "format": "HttpData-PULL"}
                """.formatted(wmConsumerTpId);

        HttpResponse<String> response = post(
                WIREMOCK_CONSUMER_BASE_URL + ApiEndpoints.TRANSFER_DATATRANSFER_V1,
                requestBody, ADMIN_CREDENTIALS);

        assertEquals(200, response.statusCode(),
                "WireMock-consumer requestTransfer API failed: " + response.body());
        log.info("Transfer requested (WireMock intercept) — wmConsumerTpId='{}'", wmConsumerTpId);

        // ── Poll Provider TP until TERMINATED ─────────────────────────────────────
        TransferProcess terminatedProviderTp = pollUntilTransferStateByAgreementId(
                providerTpRepo, agreementId, TransferState.TERMINATED, "provider");

        assertEquals(TransferState.TERMINATED, terminatedProviderTp.getState(),
                "Provider TP must be TERMINATED after WireMock returns 500 for all start messages");
        log.info("Provider TP correctly reached TERMINATED — agreementId='{}'", agreementId);

        // ── Poll WireMock-consumer TP until TERMINATED ────────────────────────────
        // Provider's termination message was proxied by WireMock to the real WireMock-consumer,
        // which processes it via ConsumerDataTransferCallbackController and saves TP as TERMINATED.
        TransferProcess terminatedConsumerTp = pollUntilTransferState(
                wmConsumerTpRepo, wmConsumerTpId, TransferState.TERMINATED, "wiremock-consumer");

        assertEquals(TransferState.TERMINATED, terminatedConsumerTp.getState(),
                "WireMock-consumer TP must be TERMINATED after provider proxied termination");
        log.info("WireMock-consumer TP correctly reached TERMINATED");

        // ── reset WireMock stubs so subsequent tests are not affected ─────────────
        wireMockServer.resetAll();
    }

    // ── polling helpers ───────────────────────────────────────────────────────────

    /**
     * Polls the given {@link TransferProcessRepository} by internal TP id until the
     * {@link TransferProcess} reaches {@code targetState} or the timeout is exceeded.
     *
     * @param repository  the repository to query
     * @param tpId        the internal MongoDB id of the {@link TransferProcess}
     * @param targetState the state to wait for
     * @param label       human-readable label for log messages
     * @return the {@link TransferProcess} once it has {@code targetState}
     * @throws AssertionError if the state is not reached within {@link #POLL_TIMEOUT_SECONDS}
     */
    private TransferProcess pollUntilTransferState(TransferProcessRepository repository,
                                                   String tpId,
                                                   TransferState targetState,
                                                   String label) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (POLL_TIMEOUT_SECONDS * 1000L);
        while (System.currentTimeMillis() < deadline) {
            Optional<TransferProcess> opt = repository.findById(tpId);
            if (opt.isPresent() && targetState.equals(opt.get().getState())) {
                log.info("[{}] reached {}", label, targetState);
                return opt.get();
            }
            log.debug("[{}] current state={}", label,
                    opt.map(tp -> tp.getState().toString()).orElse("not found yet"));
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new AssertionError("[" + label + "] did not reach " + targetState
                + " within " + POLL_TIMEOUT_SECONDS + "s");
    }

    /**
     * Polls the given {@link TransferProcessRepository} by agreement id until the
     * {@link TransferProcess} reaches {@code targetState} or the timeout is exceeded.
     * Used on the Provider side where the internal TP id is not known upfront.
     *
     * @param repository  the repository to query
     * @param agreementId the agreement id to look up
     * @param targetState the state to wait for
     * @param label       human-readable label for log messages
     * @return the {@link TransferProcess} once it has {@code targetState}
     * @throws AssertionError if the state is not reached within {@link #POLL_TIMEOUT_SECONDS}
     */
    private TransferProcess pollUntilTransferStateByAgreementId(TransferProcessRepository repository,
                                                                String agreementId,
                                                                TransferState targetState,
                                                                String label) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (POLL_TIMEOUT_SECONDS * 1000L);
        while (System.currentTimeMillis() < deadline) {
            Optional<TransferProcess> opt = repository.findByAgreementId(agreementId);
            if (opt.isPresent() && targetState.equals(opt.get().getState())) {
                log.info("[{}] reached {}", label, targetState);
                return opt.get();
            }
            log.debug("[{}] current state={}", label,
                    opt.map(tp -> tp.getState().toString()).orElse("not found yet"));
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new AssertionError("[" + label + "] did not reach " + targetState
                + " within " + POLL_TIMEOUT_SECONDS + "s");
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────────

    /**
     * Sends an HTTP POST request with JSON body and Basic Auth.
     *
     * @param url         the target URL
     * @param body        the JSON request body
     * @param credentials Base64-encoded Basic Auth credentials
     * @return the HTTP response
     * @throws Exception on I/O or interrupt errors
     */
    private HttpResponse<String> post(String url, String body, String credentials) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
