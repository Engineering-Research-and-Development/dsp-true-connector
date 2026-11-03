package it.eng.connector.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.model.Offer;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.ApplicationConnector;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.service.TenantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the connector-to-connector flow between two real connector instances started in this JVM: a consumer
 * and a provider, each with its own MongoDB database and S3 storage, talking over real HTTP on fixed ports.
 *
 * <p>The flow is the one an operator runs through the consumer's API:
 * <ol>
 *   <li>catalog request ({@code /api/v1/proxy/catalogs}) and dataset formats
 *       ({@code /api/v1/proxy/datasets/{id}/formats}), sent to the provider's protocol routes</li>
 *   <li>contract negotiation ({@code /api/v1/negotiations/request}); the provider validates the offer over its
 *       {@code /internal} zone and both tenants negotiate automatically up to {@code FINALIZED}</li>
 *   <li>transfer ({@code /api/v1/transfers}, {@code HttpData-PULL}); the provider checks the dataset formats
 *       and looks the artifact up over {@code /internal} and starts automatically, and the consumer enforces
 *       the agreement over its own {@code /internal} before it downloads</li>
 * </ol>
 *
 * <p>Two artifacts cover the two download paths. A FILE artifact is downloaded from a presigned URL of the
 * provider's S3 storage, and both sides reach {@code COMPLETED}; the provider does not enforce the agreement
 * on that path. An EXTERNAL artifact is downloaded from the provider's {@code /artifacts/{transactionId}}
 * route, where the provider enforces the agreement over {@code /internal}; see
 * {@link #externalArtifactIsServedAfterInternalEnforcement()} for the one step the test performs itself.
 *
 * <p>Protocol calls carry the mode's real connector token, loopback calls carry the real internal-call token,
 * and both are checked by the receiving instance's real security chains. Only the external data source of the
 * EXTERNAL artifact is a stub (WireMock). Each instance gets a test-only servlet filter that records method,
 * path, status, TCP peer and the {@code iss}, {@code azp} and role claims of the bearer token of every request
 * it receives (never the token itself), so the test can assert which zone handled each step and with which
 * token.
 *
 * <p>Subclasses supply the authentication mode: its properties, the consumer API token, and mode-specific
 * assertions on the recorded protocol tokens and on the outbound token cache.
 */
@Slf4j
abstract class ConnectorToConnectorFlowSupport {

    /** Default tenant: the DSP protocol path segment and the API tenant of both instances. */
    protected static final String TENANT_ID = "engineering-a";

    private static final int S3_PORT = 9000;
    private static final int POLL_TIMEOUT_SECONDS = 90;
    private static final int POLL_INTERVAL_MS = 500;
    private static final int UPLOAD_ATTEMPTS = 20;
    private static final String PULL_FORMAT = "HttpData-PULL";
    private static final String ARTIFACT_CONTENT = "connector-to-connector-artifact";
    private static final String PROVIDER_S3_ACCESS_KEY = "rustfs-c2c-provider";
    private static final String PROVIDER_S3_SECRET_KEY = "rustfs-c2c-provider-secret";
    private static final String CONSUMER_S3_ACCESS_KEY = "rustfs-c2c-consumer";
    private static final String CONSUMER_S3_SECRET_KEY = "rustfs-c2c-consumer-secret";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    // Annotations enabled, as GenericApiResponse<ContractNegotiation> relies on @JsonDeserialize.
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .configure(MapperFeature.USE_ANNOTATIONS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private static final String FORWARD_TO = "Forward-To";
    private static final String EXTERNAL_DATA_PATH = "/external-data";

    private static GenericContainer<?> providerS3Storage;
    /** Serves the data of the EXTERNAL artifact, which the provider fetches when the consumer downloads it. */
    private static WireMockServer externalDataServer;
    private static String fileDatasetId;
    private static String externalDatasetId;
    private static GenericContainer<?> consumerS3Storage;

    /** The consumer instance. */
    protected static Instance consumer;
    /** The provider instance. */
    protected static Instance provider;

    /**
     * A running connector instance with the requests it received.
     *
     * @param name    instance label for logs
     * @param port    HTTP port, also used by the instance for its loopback calls
     * @param context the application context
     * @param calls   requests received by the instance, in arrival order
     */
    protected record Instance(String name, int port, ConfigurableApplicationContext context,
                              List<RecordedCall> calls) {

        String baseUrl() {
            return "http://localhost:" + port;
        }

        String protocolUrl() {
            return baseUrl() + "/" + TENANT_ID;
        }

        <T> T bean(Class<T> type) {
            return context.getBean(type);
        }

        List<RecordedCall> callsTo(String pathPrefix) {
            return calls.stream().filter(call -> call.path().startsWith(pathPrefix)).toList();
        }
    }

    /**
     * A request received by an instance. The token itself is never recorded.
     *
     * @param method           HTTP method
     * @param path             request URI
     * @param status           response status
     * @param remoteAddress    TCP peer address
     * @param issuer           {@code iss} of the bearer token, or {@code null}
     * @param authorizedParty  {@code azp} of the bearer token, or {@code null}
     * @param roles            {@code roles} claim of the bearer token (internal-call tokens), or {@code null}
     * @param realmRoles       {@code realm_access.roles} of the bearer token (connector tokens), or {@code null}
     * @param claimNames       names of all claims of the bearer token, empty when there is none
     */
    protected record RecordedCall(String method, String path, int status, String remoteAddress,
                                  String issuer, String authorizedParty, List<String> roles,
                                  List<String> realmRoles, Set<String> claimNames) {
    }

    /**
     * Starts the provider and the consumer for the given authentication mode and stores the provider's
     * catalog and artifact.
     *
     * @param modeLabel      short label used in the database names
     * @param consumerPort   consumer HTTP port
     * @param providerPort   provider HTTP port
     * @param modeProperties the authentication-mode properties for both instances
     */
    protected static void startInstances(String modeLabel, int consumerPort, int providerPort,
                                         Map<String, String> modeProperties) {
        startInstances(modeLabel, consumerPort, providerPort, modeProperties, () -> null);
    }

    /**
     * Starts the provider and the consumer as {@link #startInstances(String, int, int, Map)} does, and
     * registers the {@link BeanPostProcessor} that the supplier returns for each instance (a {@code null}
     * result registers none). Use it to wrap beans of an instance, for example to observe calls to them.
     *
     * @param modeLabel      short label used in the database names
     * @param consumerPort   consumer HTTP port
     * @param providerPort   provider HTTP port
     * @param modeProperties the authentication-mode properties for both instances
     * @param postProcessors supplies one bean post-processor per instance, or {@code null}
     */
    protected static void startInstances(String modeLabel, int consumerPort, int providerPort,
                                         Map<String, String> modeProperties,
                                         Supplier<BeanPostProcessor> postProcessors) {
        externalDataServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        externalDataServer.start();
        externalDataServer.stubFor(WireMock.get(EXTERNAL_DATA_PATH).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .withBody(ARTIFACT_CONTENT)));
        providerS3Storage = rustfs(PROVIDER_S3_ACCESS_KEY, PROVIDER_S3_SECRET_KEY);
        consumerS3Storage = rustfs(CONSUMER_S3_ACCESS_KEY, CONSUMER_S3_SECRET_KEY);
        providerS3Storage.start();
        consumerS3Storage.start();

        provider = startInstance("provider", providerPort, "c2c_" + modeLabel + "_provider", modeProperties,
                providerS3Storage, PROVIDER_S3_ACCESS_KEY, PROVIDER_S3_SECRET_KEY, "c2c-" + modeLabel + "-provider",
                postProcessors.get());
        consumer = startInstance("consumer", consumerPort, "c2c_" + modeLabel + "_consumer", modeProperties,
                consumerS3Storage, CONSUMER_S3_ACCESS_KEY, CONSUMER_S3_SECRET_KEY, "c2c-" + modeLabel + "-consumer",
                postProcessors.get());
    }

    @AfterAll
    static void stopInstances() {
        if (consumer != null) {
            consumer.context().close();
            consumer = null;
        }
        if (provider != null) {
            provider.context().close();
            provider = null;
        }
        if (consumerS3Storage != null) {
            consumerS3Storage.stop();
        }
        if (providerS3Storage != null) {
            providerS3Storage.stop();
        }
        if (externalDataServer != null) {
            externalDataServer.stop();
        }
    }

    /**
     * Returns a token that authenticates as the tenant admin of {@value #TENANT_ID} on the consumer's API.
     *
     * @return the raw access token
     * @throws Exception when the token cannot be obtained
     */
    protected abstract String consumerApiToken() throws Exception;

    /**
     * Asserts that a protocol request received by an instance carried the mode's connector token.
     *
     * @param call the recorded protocol request
     */
    protected abstract void assertConnectorToken(RecordedCall call);

    /**
     * Asserts the outbound connector-token cache of an instance after the flow.
     *
     * @param instance the instance
     */
    protected abstract void assertOutboundTokenCache(Instance instance);

    @Test
    @DisplayName("Catalog, negotiation and transfer of a FILE artifact (presigned S3 URL) complete on both sides")
    void fileArtifactFlowCompletesBetweenTwoConnectors() throws Exception {
        Dataset dataset = providerDataset(fileDatasetId);
        String apiToken = consumerApiToken();

        // 1. Catalog request and dataset formats, consumer → provider protocol routes.
        HttpResponse<String> catalogResponse = postToConsumer(ApiEndpoints.PROXY_V1 + "/catalogs",
                JSON_MAPPER.writeValueAsString(Map.of(FORWARD_TO, provider.protocolUrl())), apiToken);
        assertThat(catalogResponse.statusCode()).as("catalog request: %s", catalogResponse.body()).isEqualTo(200);
        assertThat(JSON_MAPPER.readTree(catalogResponse.body()).path("success").asBoolean()).isTrue();
        assertThat(catalogResponse.body()).contains(fileDatasetId, externalDatasetId);

        HttpResponse<String> formatsResponse = postToConsumer(
                ApiEndpoints.PROXY_V1 + "/datasets/" + fileDatasetId + "/formats",
                JSON_MAPPER.writeValueAsString(Map.of(FORWARD_TO, provider.protocolUrl())), apiToken);
        assertThat(formatsResponse.statusCode()).as("dataset formats: %s", formatsResponse.body()).isEqualTo(200);
        assertThat(formatsResponse.body()).contains(PULL_FORMAT);

        // 2. Negotiation, 3. HTTP pull transfer: the consumer downloads from a presigned URL of the provider's S3.
        String agreementId = negotiate(dataset, apiToken);
        TransferProcess consumerTp = transfer(agreementId, apiToken);
        S3Properties consumerS3 = consumer.bean(S3Properties.class);
        assertThat(consumer.bean(S3ClientService.class).fileExists(consumerS3.getBucketName(), consumerTp.getId()))
                .as("artifact stored in the consumer bucket").isTrue();

        assertThat(provider.callsTo("/" + TENANT_ID + "/catalog/request")).isNotEmpty();
        assertThat(provider.callsTo("/" + TENANT_ID + "/transfers/")).extracting(RecordedCall::path)
                .anySatisfy(path -> assertThat(path).endsWith("/completion"));
        assertProtocolCalls();
        assertInternalCalls(provider, "POST " + ApiEndpoints.INTERNAL_OFFERS_V1 + "/validate",
                "GET " + ApiEndpoints.INTERNAL_DATASETS_V1 + "/" + fileDatasetId + "/formats",
                "GET " + ApiEndpoints.INTERNAL_DATASETS_V1 + "/" + fileDatasetId + "/artifact");
        // The consumer enforces the agreement before it downloads.
        assertInternalCalls(consumer, "POST " + ApiEndpoints.INTERNAL_AGREEMENTS_V1 + "/" + agreementId + "/enforce");
        assertOutboundTokenCache(consumer);
        assertOutboundTokenCache(provider);
    }

    /**
     * Negotiates and starts the transfer of a dataset whose artifact is EXTERNAL, then downloads it from the
     * provider's {@code /artifacts/{transactionId}} route, where the provider enforces the agreement and looks
     * the artifact up over {@code /internal} before it fetches the external data.
     *
     * <p>The consumer's own automatic download is not used for that step: for an EXTERNAL artifact the
     * provider announces {@code <tenant callback>/artifacts/{transactionId}}, a tenant-prefixed address that
     * no controller serves (the route is mapped on {@code /artifacts/{transactionId}} only), so it gets 404
     * in every authentication mode. The test sends the download request to the mapped route itself, without
     * credentials, as the consumer's HTTP pull does.
     */
    @Test
    @DisplayName("Negotiation and transfer of an EXTERNAL artifact: provider enforces the agreement over /internal")
    void externalArtifactIsServedAfterInternalEnforcement() throws Exception {
        Dataset dataset = providerDataset(externalDatasetId);
        String apiToken = consumerApiToken();

        String agreementId = negotiate(dataset, apiToken);
        TransferProcess initializedTp = poll("consumer transfer process INITIALIZED",
                () -> consumer.bean(TransferProcessRepository.class).findByAgreementId(agreementId));
        HttpResponse<String> transferResponse = postToConsumer(ApiEndpoints.TRANSFER_DATATRANSFER_V1,
                JSON_MAPPER.writeValueAsString(Map.of("transferProcessId", initializedTp.getId(), "format", PULL_FORMAT)),
                apiToken);
        assertThat(transferResponse.statusCode()).as("transfer request: %s", transferResponse.body()).isEqualTo(200);
        TransferProcess providerTp = poll("provider transfer STARTED",
                () -> provider.bean(TransferProcessRepository.class).findByAgreementId(agreementId)
                        .filter(tp -> tp.getState() == TransferState.STARTED));
        String enforcePath = "POST " + ApiEndpoints.INTERNAL_AGREEMENTS_V1 + "/" + agreementId + "/enforce";
        // The consumer enforces the agreement over its /internal before its (failing) download attempt.
        poll("consumer agreement enforcement", () -> consumer.callsTo("/internal/").stream()
                .filter(call -> enforcePath.equals(call.method() + " " + call.path())).findFirst());

        String transactionId = Base64.encodeBase64URLSafeString(
                (providerTp.getConsumerPid() + "|" + providerTp.getProviderPid()).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> download = HTTP_CLIENT.send(HttpRequest.newBuilder()
                        .uri(URI.create(provider.baseUrl() + "/artifacts/" + transactionId)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(download.statusCode()).as("artifact download: %s", download.body()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(ARTIFACT_CONTENT);
        assertThat(externalDataServer.getAllServeEvents()).as("provider fetched the external data").isNotEmpty();

        assertProtocolCalls();
        assertInternalCalls(provider, "POST " + ApiEndpoints.INTERNAL_OFFERS_V1 + "/validate",
                "GET " + ApiEndpoints.INTERNAL_DATASETS_V1 + "/" + externalDatasetId + "/formats",
                enforcePath,
                "GET " + ApiEndpoints.INTERNAL_DATASETS_V1 + "/" + externalDatasetId + "/artifact");
        assertInternalCalls(consumer, enforcePath);
        assertOutboundTokenCache(consumer);
        assertOutboundTokenCache(provider);
    }

    private static Dataset providerDataset(String datasetId) {
        return provider.bean(DatasetRepository.class).findById(datasetId)
                .orElseThrow(() -> new IllegalStateException("Dataset " + datasetId + " not in the provider catalog"));
    }

    // Negotiates the dataset's offer from the consumer's API; returns the agreement id once both sides are
    // FINALIZED with the same agreement.
    private String negotiate(Dataset dataset, String apiToken) throws Exception {
        Offer catalogOffer = dataset.getHasPolicy().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No offer on the provider dataset"));
        Offer offer = Offer.Builder.newInstance()
                .id(catalogOffer.getId())
                .target(dataset.getId())
                .permission(catalogOffer.getPermission())
                .build();
        Map<String, Object> negotiationRequest = new LinkedHashMap<>();
        negotiationRequest.put(FORWARD_TO, provider.protocolUrl());
        negotiationRequest.put("offer", NegotiationSerializer.serializePlainJsonNode(offer));
        HttpResponse<String> negotiationResponse = postToConsumer(ApiEndpoints.NEGOTIATION_V1 + "/request",
                NegotiationSerializer.serializePlain(negotiationRequest), apiToken);
        assertThat(negotiationResponse.statusCode()).as("negotiation request: %s", negotiationResponse.body())
                .isEqualTo(200);
        GenericApiResponse<ContractNegotiation> negotiationApiResponse = JSON_MAPPER.readValue(
                negotiationResponse.body(), JSON_MAPPER.getTypeFactory()
                        .constructParametricType(GenericApiResponse.class, ContractNegotiation.class));
        String consumerCnId = negotiationApiResponse.getData().getId();

        ContractNegotiation consumerCn = poll("consumer negotiation FINALIZED",
                () -> consumer.bean(ContractNegotiationRepository.class).findById(consumerCnId)
                        .filter(cn -> cn.getState() == ContractNegotiationState.FINALIZED));
        ContractNegotiation providerCn = poll("provider negotiation FINALIZED",
                () -> provider.bean(ContractNegotiationRepository.class).findByProviderPid(consumerCn.getProviderPid())
                        .filter(cn -> cn.getState() == ContractNegotiationState.FINALIZED));
        assertThat(consumerCn.getAgreement()).isNotNull();
        assertThat(providerCn.getAgreement()).isNotNull();
        assertThat(providerCn.getAgreement().getId()).isEqualTo(consumerCn.getAgreement().getId());
        return consumerCn.getAgreement().getId();
    }

    // Requests an HTTP pull transfer of the agreement from the consumer's API; returns the consumer's transfer
    // process once both sides are COMPLETED and the consumer has downloaded the data.
    private TransferProcess transfer(String agreementId, String apiToken) throws Exception {
        TransferProcess initializedTp = poll("consumer transfer process INITIALIZED",
                () -> consumer.bean(TransferProcessRepository.class).findByAgreementId(agreementId));
        HttpResponse<String> transferResponse = postToConsumer(ApiEndpoints.TRANSFER_DATATRANSFER_V1,
                JSON_MAPPER.writeValueAsString(Map.of("transferProcessId", initializedTp.getId(), "format", PULL_FORMAT)),
                apiToken);
        assertThat(transferResponse.statusCode()).as("transfer request: %s", transferResponse.body()).isEqualTo(200);

        TransferProcess consumerTp = poll("consumer transfer COMPLETED",
                () -> consumer.bean(TransferProcessRepository.class).findById(initializedTp.getId())
                        .filter(tp -> tp.getState() == TransferState.COMPLETED));
        poll("provider transfer COMPLETED",
                () -> provider.bean(TransferProcessRepository.class).findByAgreementId(agreementId)
                        .filter(tp -> tp.getState() == TransferState.COMPLETED));
        assertThat(consumerTp.isDownloaded()).isTrue();
        return consumerTp;
    }

    // Every protocol request either instance received carried the mode's connector token and succeeded.
    // The one tolerated failure is a 404 on the agreement route that a later retry turned into a success:
    // the provider can send the agreement before the consumer has stored its negotiation, and the
    // negotiation retry delivers it again.
    private void assertProtocolCalls() {
        List<RecordedCall> providerProtocol = provider.callsTo("/" + TENANT_ID + "/").stream()
                .filter(call -> !call.path().startsWith("/" + TENANT_ID + "/artifacts/"))
                .toList();
        List<RecordedCall> consumerProtocol = consumer.callsTo("/" + TENANT_ID + "/consumer/");
        assertThat(providerProtocol).extracting(RecordedCall::path).contains(
                "/" + TENANT_ID + "/negotiations/request", "/" + TENANT_ID + "/transfers/request");
        assertThat(providerProtocol).extracting(RecordedCall::path)
                .anySatisfy(path -> assertThat(path).endsWith("/agreement/verification"));
        assertThat(consumerProtocol).extracting(RecordedCall::path)
                .anySatisfy(path -> assertThat(path).endsWith("/agreement"))
                .anySatisfy(path -> assertThat(path).endsWith("/events"))
                .anySatisfy(path -> assertThat(path).endsWith("/start"));
        for (List<RecordedCall> calls : List.of(providerProtocol, consumerProtocol)) {
            for (int i = 0; i < calls.size(); i++) {
                RecordedCall call = calls.get(i);
                if (!isRetriedNotFound(call, calls.subList(i + 1, calls.size()))) {
                    assertThat(call.status()).as("%s %s", call.method(), call.path()).isBetween(200, 299);
                }
                assertConnectorToken(call);
            }
        }
    }

    // A 404 on the consumer's agreement route followed by a successful call with the same method and path.
    private static boolean isRetriedNotFound(RecordedCall call, List<RecordedCall> later) {
        return call.status() == 404 && call.path().endsWith("/agreement") && later.stream().anyMatch(retry -> retry.method().equals(call.method())
                && retry.path().equals(call.path()) && retry.status() >= 200 && retry.status() < 300);
    }

    // The instance's loopback calls went through /internal with an internal-call token from loopback, and
    // succeeded. The /api routes that /internal replaced are never called by the connector itself.
    private static void assertInternalCalls(Instance instance, String... expected) {
        List<RecordedCall> internal = instance.callsTo("/internal/");
        assertThat(internal).extracting(call -> call.method() + " " + call.path()).contains(expected);
        for (RecordedCall call : internal) {
            assertThat(call.status()).as("%s %s", call.method(), call.path()).isEqualTo(200);
            assertThat(call.issuer()).isEqualTo("urn:true-connector:internal");
            assertThat(call.roles()).containsExactly("ROLE_INTERNAL");
            assertThat(call.remoteAddress()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
        }
        assertThat(instance.callsTo("/api/")).extracting(RecordedCall::path)
                .noneMatch(path -> path.endsWith("/validate") || path.endsWith("/enforce")
                        || path.endsWith("/artifact") || path.startsWith(ApiEndpoints.CATALOG_DATASETS_V1));
    }

    private HttpResponse<String> postToConsumer(String path, String body, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(consumer.baseUrl() + path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static <T> T poll(String what, Supplier<Optional<T>> probe) throws InterruptedException {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Optional<T> value = probe.get();
            if (value.isPresent()) {
                return value.get();
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new AssertionError(what + " not reached within " + POLL_TIMEOUT_SECONDS + " s");
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> rustfs(String accessKey, String secretKey) {
        return new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:1.0.0-rc.6"))
                .withEnv("RUSTFS_ACCESS_KEY", accessKey)
                .withEnv("RUSTFS_SECRET_KEY", secretKey)
                .withEnv("RUSTFS_ADDRESS", ":" + S3_PORT)
                .withExposedPorts(S3_PORT)
                .withCommand("/data")
                .waitingFor(Wait.forHttp("/health").forPort(S3_PORT));
    }

    private static Instance startInstance(String name, int port, String database, Map<String, String> modeProperties,
                                          GenericContainer<?> s3, String s3AccessKey, String s3SecretKey,
                                          String bucketName, BeanPostProcessor postProcessor) {
        String s3Url = "http://%s:%d".formatted(s3.getHost(), s3.getMappedPort(S3_PORT));
        Map<String, String> properties = new HashMap<>(modeProperties);
        properties.put("server.port", String.valueOf(port));
        properties.put("spring.application.name", "c2c-" + name);
        properties.put("spring.data.mongodb.host", BaseIntegrationTest.mongoDBContainer.getHost());
        properties.put("spring.data.mongodb.port", String.valueOf(BaseIntegrationTest.mongoDBContainer.getFirstMappedPort()));
        properties.put("spring.data.mongodb.database", database);
        properties.put("application.baseURL", "http://localhost:" + port);
        properties.put("application.automatic.negotiation", "true");
        properties.put("application.automatic.negotiation.retry.max", "3");
        properties.put("application.automatic.negotiation.retry.delay.ms", "500");
        properties.put("application.automatic.transfer", "true");
        properties.put("application.automatic.transfer.retry.max", "3");
        properties.put("application.automatic.transfer.retry.delay.ms", "500");
        properties.put("server.ssl.enabled", "false");
        // Usage control on: the agreement is enforced over /internal on both sides.
        properties.put("application.usagecontrol.enabled", "true");
        properties.put("s3.endpoint", s3Url);
        properties.put("s3.externalPresignedEndpoint", s3Url);
        properties.put("s3.accessKey", s3AccessKey);
        properties.put("s3.secretKey", s3SecretKey);
        properties.put("s3.region", "us-east-1");
        properties.put("s3.bucketName", bucketName);

        List<RecordedCall> calls = new CopyOnWriteArrayList<>();
        // System properties outrank application.properties on the test classpath; they are cleared right after
        // start so they do not leak into the next instance or into other test classes.
        properties.forEach(System::setProperty);
        try {
            ConfigurableApplicationContext context = new SpringApplicationBuilder(ApplicationConnector.class)
                    .addCommandLineProperties(false)
                    .initializers(ctx -> {
                        ctx.getBeanFactory().registerSingleton("c2cCallRecorder", recorderRegistration(calls));
                        if (postProcessor != null) {
                            ctx.getBeanFactory().addBeanPostProcessor(postProcessor);
                        }
                    })
                    .run();
            Tenant tenantUpdate = Tenant.Builder.newInstance()
                    .id(TENANT_ID)
                    .name("EngineeringA")
                    .participantId("urn:connector:engineering")
                    .automaticNegotiation(true)
                    .automaticTransfer(true)
                    .enabled(true)
                    .build();
            context.getBean(TenantService.class).updateTenant(TENANT_ID, tenantUpdate);
            Instance instance = new Instance(name, port, context, calls);
            if ("provider".equals(name)) {
                populateCatalog(instance);
            }
            return instance;
        } finally {
            properties.keySet().forEach(System::clearProperty);
        }
    }

    private static FilterRegistrationBean<OncePerRequestFilter> recorderRegistration(List<RecordedCall> calls) {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                try {
                    chain.doFilter(request, response);
                } finally {
                    calls.add(record(request, response.getStatus()));
                }
            }
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    private static RecordedCall record(HttpServletRequest request, int status) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String issuer = null;
        String authorizedParty = null;
        List<String> roles = null;
        List<String> realmRoles = null;
        Set<String> claimNames = Set.of();
        if (header != null && header.startsWith("Bearer ")) {
            try {
                DecodedJWT jwt = JWT.decode(header.substring("Bearer ".length()));
                issuer = jwt.getIssuer();
                claimNames = jwt.getClaims().keySet();
                authorizedParty = jwt.getClaim("azp").asString();
                roles = jwt.getClaim("roles").asList(String.class);
                Map<String, Object> realmAccess = jwt.getClaim("realm_access").asMap();
                if (realmAccess != null && realmAccess.get("roles") instanceof List<?> list) {
                    realmRoles = list.stream().map(String::valueOf).toList();
                }
            } catch (JWTDecodeException e) {
                issuer = "<not a JWT>";
            }
        }
        return new RecordedCall(request.getMethod(), request.getRequestURI(), status, request.getRemoteAddr(),
                issuer, authorizedParty, roles, realmRoles, claimNames);
    }

    // Stores a catalog with two datasets of tenant engineering-a: one with a FILE artifact uploaded to the
    // provider's S3 (as the automatic negotiation and transfer ITs do) and one with an EXTERNAL artifact served
    // by the external data server.
    private static void populateCatalog(Instance instance) {
        Catalog catalog = CatalogMockObjectUtil.createNewCatalog(TENANT_ID);
        Dataset dataset = catalog.getDataset().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No dataset in catalog"));
        Dataset externalTemplate = CatalogMockObjectUtil.createNewDataset(TENANT_ID);
        Artifact externalArtifact = Artifact.Builder.newInstance()
                .id("urn:uuid:" + UUID.randomUUID())
                .artifactType(ArtifactType.EXTERNAL)
                .value(externalDataServer.baseUrl() + EXTERNAL_DATA_PATH)
                .createdBy("admin@mail.com")
                .lastModifiedBy("admin@mail.com")
                .build();
        Dataset externalDataset = externalTemplate.updateInstance(Dataset.Builder.newInstance()
                .hasPolicy(externalTemplate.getHasPolicy())
                .distribution(externalTemplate.getDistribution())
                .artifact(externalArtifact)
                .build());
        catalog.getDataset().add(externalDataset);
        fileDatasetId = dataset.getId();
        externalDatasetId = externalDataset.getId();

        instance.bean(CatalogRepository.class).save(catalog);
        instance.bean(DatasetRepository.class).saveAll(catalog.getDataset());
        List<Distribution> distributions = new ArrayList<>(catalog.getDistribution());
        distributions.addAll(externalDataset.getDistribution());
        distributions.stream().map(Distribution::getAccessService).filter(Objects::nonNull)
                .forEach(instance.bean(DataServiceRepository.class)::save);
        instance.bean(DistributionRepository.class).saveAll(distributions);
        instance.bean(DataServiceRepository.class).saveAll(catalog.getService());
        instance.bean(ArtifactRepository.class).save(dataset.getArtifact());
        instance.bean(ArtifactRepository.class).save(externalArtifact);
        S3Properties s3Properties = instance.bean(S3Properties.class);
        Map<String, String> destination = Map.of(
                S3Utils.OBJECT_KEY, dataset.getId(),
                S3Utils.BUCKET_NAME, s3Properties.getBucketName(),
                S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint(),
                S3Utils.REGION, s3Properties.getRegion(),
                S3Utils.ACCESS_KEY, s3Properties.getAccessKey(),
                S3Utils.SECRET_KEY, s3Properties.getSecretKey());
        // The bucket is created asynchronously on startup: retry until it exists.
        Exception lastFailure = null;
        for (int attempt = 0; attempt < UPLOAD_ATTEMPTS; attempt++) {
            try {
                instance.bean(S3ClientService.class).uploadFile(
                        new ByteArrayInputStream(ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8)), destination,
                        MediaType.TEXT_PLAIN_VALUE,
                        ContentDisposition.attachment().filename("artifact.txt").build().toString()).get();
                lastFailure = null;
                break;
            } catch (Exception e) {
                lastFailure = e;
                sleepQuietly();
            }
        }
        if (lastFailure != null) {
            throw new IllegalStateException("Failed to upload the provider artifact", lastFailure);
        }
        log.info("Provider catalog stored, datasets {} (FILE) and {} (EXTERNAL)", fileDatasetId, externalDatasetId);
    }
}
