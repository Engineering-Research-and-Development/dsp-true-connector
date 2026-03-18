package it.eng.connector.integration.negotiation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Offer;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.ApplicationConnector;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.service.S3ClientService;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that starts two real Spring Boot application instances — one acting as
 * Consumer (port 8181) and one as Provider (port 8282) — each backed by its own MongoDB
 * database on a shared Testcontainers MongoDB container.
 *
 * <p>The test triggers automatic negotiation by sending a consumer API request and then
 * polls both instances until the {@code ContractNegotiation} reaches {@code FINALIZED}
 * state, verifying the full happy-path flow end-to-end.
 */
@Slf4j
@Testcontainers
public class AutomaticNegotiationIT {

    private static final int CONSUMER_PORT = 8181;
    private static final int PROVIDER_PORT = 8282;
    /** Port used by the second consumer that routes its callback address through WireMock. */
    private static final int WIREMOCK_CONSUMER_PORT = 8383;
    /** Port WireMock listens on — intercepting provider→consumer protocol messages. */
    private static final int WIREMOCK_PORT = 9099;

    private static final String CONSUMER_BASE_URL          = "http://localhost:" + CONSUMER_PORT;
    private static final String PROVIDER_BASE_URL          = "http://localhost:" + PROVIDER_PORT;
    private static final String WIREMOCK_CONSUMER_BASE_URL = "http://localhost:" + WIREMOCK_CONSUMER_PORT;

    // Basic auth credentials matching initial_data.json
    private static final String ADMIN_CREDENTIALS =
            Base64.getEncoder().encodeToString("admin@mail.com:password".getBytes(StandardCharsets.UTF_8));

    private static final int POLL_TIMEOUT_SECONDS = 30;
    private static final int POLL_INTERVAL_MS     = 500;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> mongoDBContainer =
            new GenericContainer<>(DockerImageName.parse("mongo:7.0.12"))
                    .withExposedPorts(27017)
                    .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))
                    .withReuse(false);

    private static final MinIOContainer providerMinIO =
            new MinIOContainer(DockerImageName.parse("minio/minio"))
                    .withReuse(false);

    private static ConfigurableApplicationContext consumerCtx;
    private static ConfigurableApplicationContext providerCtx;
    /**
     * Consumer instance whose {@code application.callback.address} points to WireMock.
     * The provider sends protocol messages (e.g. ContractAgreementMessage) back to this
     * address, which WireMock intercepts and returns an error — triggering retry logic.
     */
    private static ConfigurableApplicationContext wiremockConsumerCtx;

    /** Standalone WireMock server that intercepts provider→consumer protocol messages. */
    private static WireMockServer wireMockServer;

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // Configured with annotations enabled — matches BaseIntegrationTest.jsonMapper —
    // required for GenericApiResponse<ContractNegotiation> deserialization to honour
    // @JsonDeserialize on ContractNegotiation.
    private static final JsonMapper jsonMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .configure(MapperFeature.USE_ANNOTATIONS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    // ── lifecycle ────────────────────────────────────────────────────────────────

    @BeforeAll
    static void startApplications() {
        mongoDBContainer.start();
        providerMinIO.start();

        String mongoHost = mongoDBContainer.getHost();
        int    mongoPort = mongoDBContainer.getMappedPort(27017);

        // ── WireMock — intercepts provider→consumer protocol messages ─────────────
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
        log.info("WireMock started on port {}", WIREMOCK_PORT);

        // ── Provider instance first — needs S3 properties ─────────────────────────
        providerCtx = startInstance(mongoHost, mongoPort, PROVIDER_PORT,
                "provider", "provider_db", PROVIDER_BASE_URL,
                providerMinIO.getS3URL(), providerMinIO.getUserName(), providerMinIO.getPassword());

        // ── Consumer instance — no S3 needed for negotiation flow ─────────────────
        consumerCtx = startInstance(mongoHost, mongoPort, CONSUMER_PORT,
                "consumer", "consumer_db", CONSUMER_BASE_URL,
                null, null, null);

        // ── WireMock consumer — callbackAddress points to WireMock, not the real consumer ──
        // Provider will send ContractAgreementMessage to http://localhost:WIREMOCK_PORT/consumer/...
        // WireMock intercepts that request and returns an error → triggers provider's retry logic.
        wiremockConsumerCtx = startInstance(mongoHost, mongoPort, WIREMOCK_CONSUMER_PORT,
                "consumer-wiremock", "consumer_wiremock_db",
                "http://localhost:" + WIREMOCK_PORT,
                null, null, null);

        // Populate provider catalog so offer validation succeeds
        populateProviderCatalog();
    }

    /**
     * Starts a single Spring Boot application instance with all properties supplied via a
     * high-priority {@code MapPropertySource} added directly to the {@code Environment}
     * before any other source is consulted. This overrides the hardcoded MongoDB and S3
     * coordinates in {@code application.properties} on the test classpath.
     *
     * @param s3Endpoint MinIO S3 URL — may be {@code null} for instances that don't need S3
     * @param s3AccessKey MinIO access key — may be {@code null}
     * @param s3SecretKey MinIO secret key — may be {@code null}
     */
    private static ConfigurableApplicationContext startInstance(String mongoHost, int mongoPort,
                                                                int serverPort, String appName,
                                                                String database, String callbackAddress,
                                                                String s3Endpoint, String s3AccessKey,
                                                                String s3SecretKey) {
        // System properties have the absolute highest priority in Spring Boot's property
        // source order — they are resolved before ANY bean (including InitialDataLoader)
        // is instantiated, ensuring the Testcontainers coordinates win over application.properties.
        System.setProperty("server.port", String.valueOf(serverPort));
        System.setProperty("spring.application.name", appName);
        System.setProperty("spring.data.mongodb.host", mongoHost);
        System.setProperty("spring.data.mongodb.port", String.valueOf(mongoPort));
        System.setProperty("spring.data.mongodb.database", database);
        System.setProperty("application.callback.address", callbackAddress);
        System.setProperty("application.automatic.negotiation", "true");
        System.setProperty("application.automatic.negotiation.retry.max", "3");
        System.setProperty("application.automatic.negotiation.retry.delay.ms", "500");
        System.setProperty("server.ssl.enabled", "false");
        System.setProperty("application.usagecontrol.enabled", "false");

        if (s3Endpoint != null) {
            System.setProperty("s3.endpoint", s3Endpoint);
            System.setProperty("s3.externalPresignedEndpoint", s3Endpoint);
            System.setProperty("s3.accessKey", s3AccessKey);
            System.setProperty("s3.secretKey", s3SecretKey);
            System.setProperty("s3.region", "us-east-1");
            System.setProperty("s3.bucketName", "provider-bucket");
        }

        try {
            var app = new SpringApplicationBuilder(ApplicationConnector.class)
                    .addCommandLineProperties(false)
                    .build();
            return app.run();
        } finally {
            // Clear system properties so they don't leak into the next context started
            // in this JVM (consumer starts after provider with different values).
            System.clearProperty("server.port");
            System.clearProperty("spring.application.name");
            System.clearProperty("spring.data.mongodb.host");
            System.clearProperty("spring.data.mongodb.port");
            System.clearProperty("spring.data.mongodb.database");
            System.clearProperty("application.callback.address");
            System.clearProperty("application.automatic.negotiation");
            System.clearProperty("application.automatic.negotiation.retry.max");
            System.clearProperty("application.automatic.negotiation.retry.delay.ms");
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
    }

    // ── catalog setup ─────────────────────────────────────────────────────────────

    /**
     * Saves a catalog with a dataset and matching offer into the provider's MongoDB and
     * uploads the artifact file to the provider's MinIO instance — mirroring the setup
     * in {@code CatalogIT} so that offer validation and catalog serving both succeed.
     */
    private static void populateProviderCatalog() {
        var catalogRepository      = providerCtx.getBean(CatalogRepository.class);
        var datasetRepository      = providerCtx.getBean(DatasetRepository.class);
        var dataServiceRepository  = providerCtx.getBean(DataServiceRepository.class);
        var distributionRepository = providerCtx.getBean(DistributionRepository.class);
        var artifactRepository     = providerCtx.getBean(ArtifactRepository.class);
        var s3ClientService        = providerCtx.getBean(S3ClientService.class);
        var s3Properties           = providerCtx.getBean(it.eng.tools.s3.properties.S3Properties.class);

        Catalog catalog = CatalogMockObjectUtil.createNewCatalog();
        Dataset dataset = catalog.getDataset().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No dataset in catalog"));

        // Persist catalog data into provider MongoDB
        catalogRepository.save(catalog);
        datasetRepository.saveAll(catalog.getDataset());
        dataServiceRepository.saveAll(catalog.getService());
        distributionRepository.saveAll(catalog.getDistribution());
        if (dataset.getArtifact() != null) {
            artifactRepository.save(dataset.getArtifact());
        }

        // Bucket is already created by InitialDataLoader on application startup —
        // just upload the artifact file using the provider's S3Properties bean,
        // exactly as CatalogIT.uploadFile() does via createS3EndpointProperties().
        Map<String, String> destinationS3Properties = Map.of(
                it.eng.tools.s3.util.S3Utils.OBJECT_KEY,        dataset.getId(),
                it.eng.tools.s3.util.S3Utils.BUCKET_NAME,       s3Properties.getBucketName(),
                it.eng.tools.s3.util.S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint(),
                it.eng.tools.s3.util.S3Utils.REGION,            s3Properties.getRegion(),
                it.eng.tools.s3.util.S3Utils.ACCESS_KEY,        s3Properties.getAccessKey(),
                it.eng.tools.s3.util.S3Utils.SECRET_KEY,        s3Properties.getSecretKey()
        );

        try {
            var content = new ByteArrayInputStream("artifact-content".getBytes(StandardCharsets.UTF_8));
            s3ClientService.uploadFile(content, destinationS3Properties,
                    MediaType.TEXT_PLAIN_VALUE,
                    ContentDisposition.attachment().filename("artifact.txt").build().toString()).get();
            log.info("Provider artifact uploaded to S3 with key '{}'", dataset.getId());
            Thread.sleep(2000); // wait for S3 upload to complete, as done in CatalogIT
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload provider artifact to MinIO", e);
        }

        log.info("Provider catalog populated — dataset='{}', bucket='{}'",
                dataset.getId(), s3Properties.getBucketName());
    }

    // ── test ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Automatic negotiation - consumer initiates, both sides reach FINALIZED")
    void automaticNegotiation_consumerInitiated_reachesFinalizedOnBothSides() throws Exception {
        // Retrieve the offer id and dataset id from the provider catalog
        var datasetRepository = providerCtx.getBean(DatasetRepository.class);
        Dataset dataset = datasetRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No dataset found in provider catalog"));
        Offer catalogOffer = dataset.getHasPolicy().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No offer found on dataset"));

        // Build the offer the consumer will send — use the exact permission from the
        // catalog offer so it passes the provider's offer validation unchanged
        Offer offer = Offer.Builder.newInstance()
                .id(catalogOffer.getId())
                .target(dataset.getId())
                .permission(catalogOffer.getPermission())
                .build();

        // Consumer sends API request to initiate negotiation toward the provider
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("Forward-To", PROVIDER_BASE_URL);
        requestBody.put("offer", NegotiationSerializer.serializePlainJsonNode(offer));

        HttpResponse<String> initiateResponse = post(
                CONSUMER_BASE_URL + ApiEndpoints.NEGOTIATION_V1 + "/request",
                NegotiationSerializer.serializePlain(requestBody),
                ADMIN_CREDENTIALS);

        assertEquals(200, initiateResponse.statusCode(),
                "Consumer initiate request failed: " + initiateResponse.body());

        var javaType = jsonMapper.getTypeFactory()
                .constructParametricType(GenericApiResponse.class, ContractNegotiation.class);
        GenericApiResponse<ContractNegotiation> apiResponse =
                jsonMapper.readValue(initiateResponse.body(), javaType);
        assertTrue(apiResponse.isSuccess(),
                "Expected success response but got: " + apiResponse.getMessage());
        ContractNegotiation consumerCn = apiResponse.getData();
        assertNotNull(consumerCn, "Consumer ContractNegotiation must not be null");
        String consumerCnId = consumerCn.getId();
        log.info("Negotiation started - consumerCnId={}", consumerCnId);

        // Poll consumer until FINALIZED
        ContractNegotiation finalConsumerCn = pollUntilState(
                CONSUMER_BASE_URL + ApiEndpoints.NEGOTIATION_V1 + "/" + consumerCnId,
                ADMIN_CREDENTIALS, ContractNegotiationState.FINALIZED, "consumer");

        // Poll provider until FINALIZED (look up by providerPid stored on consumer side)
        String providerPid = finalConsumerCn.getProviderPid();
        assertNotNull(providerPid, "ProviderPid must be set on consumer-side CN after negotiation");

        ContractNegotiationRepository providerRepo = providerCtx.getBean(ContractNegotiationRepository.class);
        ContractNegotiation providerCn = pollUntilFinalizedFromRepo(providerRepo, providerPid, "provider");

        // ── verify both sides ────────────────────────────────────────────────────
        assertEquals(ContractNegotiationState.FINALIZED, finalConsumerCn.getState(),
                "Consumer CN must be FINALIZED");
        assertEquals(ContractNegotiationState.FINALIZED, providerCn.getState(),
                "Provider CN must be FINALIZED");

        assertNotNull(finalConsumerCn.getAgreement(), "Consumer CN must have an agreement");
        assertNotNull(providerCn.getAgreement(), "Provider CN must have an agreement");
        assertEquals(finalConsumerCn.getAgreement().getId(), providerCn.getAgreement().getId(),
                "Both sides must reference the same agreement id");

        log.info("Automatic negotiation completed successfully - agreementId={}",
                finalConsumerCn.getAgreement().getId());
    }

    /**
     * Verifies that <strong>both</strong> the provider and the wiremock-consumer CNs reach
     * {@code TERMINATED} when the provider's outbound {@code ContractAgreementMessage} is
     * intercepted by WireMock and answered with HTTP 500.
     *
     * <h3>Flow</h3>
     * <ol>
     *   <li>The wiremock-consumer (port 8383) initiates negotiation toward the real provider
     *       (port 8282), embedding {@code callbackAddress = http://localhost:WIREMOCK_PORT/consumer}
     *       in the {@code ContractRequestMessage}.</li>
     *   <li>The provider validates the offer and creates a CN in {@code REQUESTED} state.</li>
     *   <li>The provider's automatic negotiation fires and tries to send
     *       {@code ContractAgreementMessage} to
     *       {@code http://localhost:WIREMOCK_PORT/consumer/negotiations/{consumerPid}/agreement}.
     *       WireMock intercepts this and returns HTTP 500.</li>
     *   <li>The provider's {@link it.eng.negotiation.service.AutomaticNegotiationService}
     *       exhausts its retry budget and calls {@code terminateGracefully}, which sends a
     *       {@code ContractNegotiationTerminationMessage} to WireMock.</li>
     *   <li>WireMock proxies the termination message to the real wiremock-consumer (port 8383),
     *       which processes it and saves its CN as {@code TERMINATED}.</li>
     *   <li>The provider saves its own CN as {@code TERMINATED} upon successful delivery of the
     *       termination message.</li>
     * </ol>
     */
    @Test
    @DisplayName("Automatic negotiation - WireMock intercepts agreement, both provider and consumer CN reach TERMINATED after retry exhaustion")
    void automaticNegotiation_providerUnreachable_consumerReachesTerminated() throws Exception {
        // ── configure WireMock stubs ───────────────────────────────────────────────
        // 1. Intercept ContractAgreementMessage with 500 → triggers provider retry loop.
        wireMockServer.stubFor(
                WireMock.post(urlPathMatching("/consumer/negotiations/.+/agreement"))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"reason\": \"simulated consumer unreachable\"}")));

        // 2. Proxy ContractNegotiationTerminationMessage to the real wiremock-consumer so
        //    its CN is also transitioned to TERMINATED via the normal protocol handler.
        wireMockServer.stubFor(
                WireMock.post(urlPathMatching("/consumer/negotiations/.+/termination"))
                        .willReturn(aResponse()
                                .proxiedFrom("http://localhost:" + WIREMOCK_CONSUMER_PORT)));

        // ── retrieve offer from provider catalog ───────────────────────────────────
        var datasetRepository = providerCtx.getBean(DatasetRepository.class);
        Dataset dataset = datasetRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No dataset found in provider catalog"));
        Offer catalogOffer = dataset.getHasPolicy().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No offer found on dataset"));

        Offer offer = Offer.Builder.newInstance()
                .id(catalogOffer.getId())
                .target(dataset.getId())
                .permission(catalogOffer.getPermission())
                .build();

        // ── wiremock-consumer sends ContractRequestMessage to real provider ────────
        // The consumer's callbackAddress is http://localhost:WIREMOCK_PORT/consumer, so
        // the provider will route all subsequent protocol messages through WireMock.
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("Forward-To", PROVIDER_BASE_URL);
        requestBody.put("offer", NegotiationSerializer.serializePlainJsonNode(offer));

        HttpResponse<String> initiateResponse = post(
                WIREMOCK_CONSUMER_BASE_URL + ApiEndpoints.NEGOTIATION_V1 + "/request",
                NegotiationSerializer.serializePlain(requestBody),
                ADMIN_CREDENTIALS);

        assertEquals(200, initiateResponse.statusCode(),
                "WireMock-consumer initiate request failed: " + initiateResponse.body());

        var javaType = jsonMapper.getTypeFactory()
                .constructParametricType(GenericApiResponse.class, ContractNegotiation.class);
        GenericApiResponse<ContractNegotiation> apiResponse =
                jsonMapper.readValue(initiateResponse.body(), javaType);
        assertTrue(apiResponse.isSuccess(),
                "Expected success response but got: " + apiResponse.getMessage());

        ContractNegotiation wiremockConsumerCn = apiResponse.getData();
        assertNotNull(wiremockConsumerCn, "WireMock-consumer ContractNegotiation must not be null");
        String providerPid = wiremockConsumerCn.getProviderPid();
        String consumerCnId = wiremockConsumerCn.getId();
        assertNotNull(providerPid, "ProviderPid must be returned after initial request");
        log.info("Negotiation started (WireMock intercept) — providerPid={}, consumerCnId={}",
                providerPid, consumerCnId);

        // ── poll provider repository until provider CN reaches TERMINATED ──────────
        // Provider sends ContractAgreementMessage → WireMock → 500
        // After retry exhaustion provider sends termination → WireMock proxies to consumer
        // → provider saves its CN as TERMINATED.
        ContractNegotiationRepository providerRepo = providerCtx.getBean(ContractNegotiationRepository.class);
        ContractNegotiation providerCn = pollUntilStateFromRepo(
                providerRepo, providerPid, ContractNegotiationState.TERMINATED, "provider");

        assertEquals(ContractNegotiationState.TERMINATED, providerCn.getState(),
                "Provider CN must be TERMINATED after WireMock returns 500 for all agreement messages");
        log.info("Provider CN correctly reached TERMINATED — providerPid={}", providerPid);

        // ── poll wiremock-consumer repository until consumer CN reaches TERMINATED ──
        // The termination message was proxied by WireMock to the real wiremock-consumer,
        // which processes it via ContractNegotiationConsumerCallbackController and saves
        // its CN as TERMINATED.
        ContractNegotiationRepository wiremockConsumerRepo =
                wiremockConsumerCtx.getBean(ContractNegotiationRepository.class);
        ContractNegotiation consumerCn = pollUntilStateFromRepo(
                wiremockConsumerRepo, providerPid, ContractNegotiationState.TERMINATED, "wiremock-consumer");

        assertEquals(ContractNegotiationState.TERMINATED, consumerCn.getState(),
                "WireMock-consumer CN must be TERMINATED after provider proxied termination through WireMock");
        log.info("WireMock-consumer CN correctly reached TERMINATED — consumerCnId={}", consumerCnId);

        // ── reset WireMock stubs so subsequent tests are not affected ─────────────
        wireMockServer.resetAll();
    }

    /**
     * Verifies that <strong>both</strong> the consumer and the provider CNs reach
     * {@code TERMINATED} when the consumer's outbound
     * {@code ContractAgreementVerificationMessage} (AGREED → VERIFIED transition) is
     * intercepted by WireMock and answered with HTTP 500.
     *
     * <p>The consumer uses {@code http://localhost:WIREMOCK_PORT} as its provider callback
     * address (via {@code Forward-To}), so every subsequent protocol message the consumer
     * sends to the "provider" goes through WireMock first.
     *
     * <h3>Flow</h3>
     * <ol>
     *   <li>The real consumer (port 8181) sends {@code ContractRequestMessage} to
     *       {@code http://localhost:WIREMOCK_PORT/negotiations/request}.
     *       WireMock <em>proxies</em> it to the real provider (port 8282).</li>
     *   <li>The provider creates a CN in {@code REQUESTED} and auto-sends
     *       {@code ContractAgreementMessage} directly to the consumer's real callback
     *       address (port 8181, not through WireMock).</li>
     *   <li>The consumer receives the agreement and moves to {@code AGREED}. Automatic
     *       negotiation then fires {@code sendContractAgreementVerificationMessage}, which
     *       POSTs to {@code http://localhost:WIREMOCK_PORT/negotiations/{providerPid}/agreement/verification}.
     *       WireMock intercepts this and returns HTTP 500.</li>
     *   <li>The consumer's {@link it.eng.negotiation.service.AutomaticNegotiationService}
     *       exhausts its retry budget and calls {@code terminateGracefully}, which sends a
     *       {@code ContractNegotiationTerminationMessage} to
     *       {@code http://localhost:WIREMOCK_PORT/negotiations/{providerPid}/termination}.
     *       WireMock <em>proxies</em> this to the real provider (port 8282).</li>
     *   <li>The provider processes the termination and saves its CN as {@code TERMINATED}.</li>
     *   <li>The consumer saves its own CN as {@code TERMINATED} upon successful delivery of
     *       the termination message.</li>
     * </ol>
     */
    @Test
    @DisplayName("Automatic negotiation - WireMock intercepts verification, both consumer and provider CN reach TERMINATED after retry exhaustion")
    void automaticNegotiation_consumerUnreachable_providerReachesTerminated() throws Exception {
        // ── configure WireMock stubs ───────────────────────────────────────────────
        // 1. Proxy the initial ContractRequestMessage through to the real provider so the
        //    negotiation is bootstrapped correctly on both sides.
        wireMockServer.stubFor(
                WireMock.post(urlPathMatching("/negotiations/request"))
                        .willReturn(aResponse()
                                .proxiedFrom("http://localhost:" + PROVIDER_PORT)));

        // 2. Intercept ContractAgreementVerificationMessage with 500 → triggers consumer
        //    retry loop (AGREED → VERIFIED transition fails repeatedly).
        wireMockServer.stubFor(
                WireMock.post(urlPathMatching("/negotiations/.+/agreement/verification"))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"reason\": \"simulated provider unreachable\"}")));

        // 3. Proxy ContractNegotiationTerminationMessage to the real provider so its CN is
        //    also transitioned to TERMINATED via the normal protocol handler.
        wireMockServer.stubFor(
                WireMock.post(urlPathMatching("/negotiations/.+/termination"))
                        .willReturn(aResponse()
                                .proxiedFrom("http://localhost:" + PROVIDER_PORT)));

        // ── retrieve offer from provider catalog ───────────────────────────────────
        var datasetRepository = providerCtx.getBean(DatasetRepository.class);
        Dataset dataset = datasetRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No dataset found in provider catalog"));
        Offer catalogOffer = dataset.getHasPolicy().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No offer found on dataset"));

        Offer offer = Offer.Builder.newInstance()
                .id(catalogOffer.getId())
                .target(dataset.getId())
                .permission(catalogOffer.getPermission())
                .build();

        // ── consumer sends ContractRequestMessage through WireMock to the provider ──
        // By setting Forward-To to WireMock's address the consumer stores
        // http://localhost:WIREMOCK_PORT as its callbackAddress for the provider,
        // so all subsequent consumer→provider protocol messages go through WireMock.
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("Forward-To", "http://localhost:" + WIREMOCK_PORT);
        requestBody.put("offer", NegotiationSerializer.serializePlainJsonNode(offer));

        HttpResponse<String> initiateResponse = post(
                CONSUMER_BASE_URL + ApiEndpoints.NEGOTIATION_V1 + "/request",
                NegotiationSerializer.serializePlain(requestBody),
                ADMIN_CREDENTIALS);

        assertEquals(200, initiateResponse.statusCode(),
                "Consumer initiate request failed: " + initiateResponse.body());

        var javaType = jsonMapper.getTypeFactory()
                .constructParametricType(GenericApiResponse.class, ContractNegotiation.class);
        GenericApiResponse<ContractNegotiation> apiResponse =
                jsonMapper.readValue(initiateResponse.body(), javaType);
        assertTrue(apiResponse.isSuccess(),
                "Expected success response but got: " + apiResponse.getMessage());

        ContractNegotiation consumerCn = apiResponse.getData();
        assertNotNull(consumerCn, "Consumer ContractNegotiation must not be null");
        String consumerCnId = consumerCn.getId();
        String providerPid = consumerCn.getProviderPid();
        assertNotNull(providerPid, "ProviderPid must be returned after initial request");
        log.info("Negotiation started (consumer→WireMock→provider) — consumerCnId={}, providerPid={}",
                consumerCnId, providerPid);

        // ── poll consumer repository until consumer CN reaches TERMINATED ──────────
        // Consumer receives ContractAgreementMessage (direct from provider) → AGREED
        // Consumer sends ContractAgreementVerificationMessage → WireMock → 500
        // After retry exhaustion consumer sends termination → WireMock proxies to provider
        // → consumer saves its CN as TERMINATED.
        ContractNegotiationRepository consumerRepo = consumerCtx.getBean(ContractNegotiationRepository.class);
        ContractNegotiation terminatedConsumerCn = pollUntilStateFromRepo(
                consumerRepo, providerPid, ContractNegotiationState.TERMINATED, "consumer");

        assertEquals(ContractNegotiationState.TERMINATED, terminatedConsumerCn.getState(),
                "Consumer CN must be TERMINATED after WireMock returns 500 for all verification messages");
        log.info("Consumer CN correctly reached TERMINATED — consumerCnId={}", consumerCnId);

        // ── poll provider repository until provider CN reaches TERMINATED ──────────
        // Provider received ContractNegotiationTerminationMessage (proxied through WireMock)
        // and saved its CN as TERMINATED.
        ContractNegotiationRepository providerRepo = providerCtx.getBean(ContractNegotiationRepository.class);
        ContractNegotiation terminatedProviderCn = pollUntilStateFromRepo(
                providerRepo, providerPid, ContractNegotiationState.TERMINATED, "provider");

        assertEquals(ContractNegotiationState.TERMINATED, terminatedProviderCn.getState(),
                "Provider CN must be TERMINATED after consumer proxied termination through WireMock");
        log.info("Provider CN correctly reached TERMINATED — providerPid={}", providerPid);

        // ── reset WireMock stubs so subsequent tests are not affected ─────────────
        wireMockServer.resetAll();
    }

    // ── helpers ────────────────────────────────────────────────────────────────────

    /**
     * Polls the given API endpoint until the returned {@code ContractNegotiation} has the
     * expected {@code targetState} or the timeout is exceeded.
     *
     * @param url         the API endpoint to poll
     * @param credentials Base64-encoded Basic Auth credentials
     * @param targetState the {@link ContractNegotiationState} to wait for
     * @param label       human-readable label used in log messages
     * @return the {@link ContractNegotiation} once it reaches {@code targetState}
     * @throws Exception if polling times out or an HTTP/parse error occurs
     */
    private ContractNegotiation pollUntilState(String url, String credentials,
                                               ContractNegotiationState targetState, String label)
            throws Exception {
        long deadline = System.currentTimeMillis() + (POLL_TIMEOUT_SECONDS * 1000L);
        var javaType = jsonMapper.getTypeFactory()
                .constructParametricType(GenericApiResponse.class, ContractNegotiation.class);
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> response = get(url, credentials);
            if (response.statusCode() == 200) {
                GenericApiResponse<ContractNegotiation> apiResponse =
                        jsonMapper.readValue(response.body(), javaType);
                ContractNegotiation cn = apiResponse.getData();
                if (cn != null && targetState.equals(cn.getState())) {
                    log.info("[{}] reached {}", label, targetState);
                    return cn;
                }
                log.debug("[{}] current state={}", label, cn != null ? cn.getState() : "null");
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new AssertionError("[" + label + "] did not reach " + targetState + " within "
                + POLL_TIMEOUT_SECONDS + "s");
    }

    /**
     * Polls the provider's MongoDB repository directly until the {@code ContractNegotiation}
     * with the given {@code providerPid} has state {@code FINALIZED} or the timeout is exceeded.
     *
     * @param repository the provider's {@link ContractNegotiationRepository}
     * @param providerPid the provider PID to look up
     * @param label       human-readable label used in log messages
     * @return the finalized {@link ContractNegotiation}
     * @throws InterruptedException if the polling sleep is interrupted
     */
    private ContractNegotiation pollUntilFinalizedFromRepo(ContractNegotiationRepository repository,
                                                           String providerPid, String label)
            throws InterruptedException {
        return pollUntilStateFromRepo(repository, providerPid, ContractNegotiationState.FINALIZED, label);
    }

    /**
     * Polls the provider's MongoDB repository directly until the {@code ContractNegotiation}
     * with the given {@code providerPid} has the {@code targetState} or the timeout is exceeded.
     *
     * @param repository  the {@link ContractNegotiationRepository} to query
     * @param providerPid the provider PID to look up
     * @param targetState the state to wait for
     * @param label       human-readable label used in log messages
     * @return the {@link ContractNegotiation} once it has {@code targetState}
     * @throws InterruptedException if the polling sleep is interrupted
     */
    private ContractNegotiation pollUntilStateFromRepo(ContractNegotiationRepository repository,
                                                       String providerPid,
                                                       ContractNegotiationState targetState,
                                                       String label)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + (POLL_TIMEOUT_SECONDS * 1000L);
        while (System.currentTimeMillis() < deadline) {
            Optional<ContractNegotiation> opt = repository.findByProviderPid(providerPid);
            if (opt.isPresent() && targetState.equals(opt.get().getState())) {
                log.info("[{}] reached {}", label, targetState);
                return opt.get();
            }
            log.debug("[{}] current state={}", label,
                    opt.map(cn -> cn.getState().toString()).orElse("not found yet"));
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new AssertionError("[" + label + "] did not reach " + targetState + " within "
                + POLL_TIMEOUT_SECONDS + "s");
    }

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

    /**
     * Sends an HTTP GET request with Basic Auth.
     *
     * @param url         the target URL
     * @param credentials Base64-encoded Basic Auth credentials
     * @return the HTTP response
     * @throws Exception on I/O or interrupt errors
     */
    private HttpResponse<String> get(String url, String credentials) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

