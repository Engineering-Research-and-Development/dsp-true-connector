package it.eng.connector.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.wiremock.spring.InjectWireMock;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how the outbound connector-to-connector token of {@code KEYCLOAK} mode behaves when the
 * {@code dsp-connector-backend} secret is rotated in Keycloak but not yet in the connector's configuration.
 *
 * <p>The outbound call is a real {@link OkHttpRestClient} protocol request with
 * {@link CredentialUtils#getConnectorCredentials()}, sent to WireMock so the test sees whether a request went
 * out and which token it carried. Tokens come from the real {@code platform} realm of the shared Keycloak
 * container. To reach the cache expiry quickly, the client's access token lifespan is shortened through the
 * admin API; the secret and the lifespan are restored after each test.
 *
 * <p>Expected behaviour: a token issued before the rotation is used until its cache entry goes stale
 * (30 s before {@code exp}) or is evicted after a 401; after that the next call requests a new token, which
 * fails with HTTP 401 and the OAuth error logged at ERROR, without the secret or a token in any log line, and
 * the request is not sent. Once the secret is restored in Keycloak, the next call gets a new token, without
 * a restart.
 */
class KeycloakSecretRotationIT extends BaseKeycloakIntegrationTest {

    private static final String PLATFORM_REALM = "platform";
    private static final String BACKEND_CLIENT_ID = "dsp-connector-backend";
    private static final String CONFIGURED_SECRET = "dsp-connector-backend-secret";
    private static final String LIFESPAN_ATTRIBUTE = "access.token.lifespan";
    /** Short enough that the cache entry (refreshed 30 s before exp) goes stale after about 5 s. */
    private static final int SHORT_LIFESPAN_SECONDS = 35;
    private static final long CACHE_REFRESH_MARGIN_SECONDS = 30;
    private static final String PEER_PATH = "/rotation-peer/catalog/request";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private OkHttpRestClient okHttpRestClient;
    @Autowired
    private CredentialUtils credentialUtils;

    @InjectWireMock
    private WireMockServer wireMock;

    private ListAppender<ILoggingEvent> logAppender;
    private String originalLifespan;

    @BeforeEach
    void shortenTokenLifespanAndCaptureLogs() {
        ClientResource client = backendClient();
        ClientRepresentation representation = client.toRepresentation();
        originalLifespan = Optional.ofNullable(representation.getAttributes()).map(a -> a.get(LIFESPAN_ATTRIBUTE))
                .orElse(null);
        setLifespan(client, String.valueOf(SHORT_LIFESPAN_SECONDS));
        // Other Keycloak ITs share this context: start from an empty outbound cache.
        credentialUtils.invalidateCachedCredentials();

        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger().addAppender(logAppender);
    }

    @AfterEach
    void restoreKeycloakAndLogs() throws Exception {
        rootLogger().detachAppender(logAppender);
        ClientResource client = backendClient();
        restoreSecret(client);
        // An empty value removes the attribute, falling back to the realm's lifespan.
        setLifespan(client, originalLifespan == null ? "" : originalLifespan);
        credentialUtils.invalidateCachedCredentials();
        wireMock.resetAll();
        // The shared container is back to the seed: the seed secret works and the lifespan is the realm's.
        DecodedJWT restored = JWT.decode(connectorAccessToken());
        assertThat(Duration.between(restored.getIssuedAtAsInstant(), restored.getExpiresAtAsInstant()))
                .isNotEqualTo(Duration.ofSeconds(SHORT_LIFESPAN_SECONDS));
    }

    @Test
    @DisplayName("Rotated secret: cached token used until stale, then a clear 401 failure, recovery after restore")
    void rotatedSecretFailsClearlyAfterExpiryAndRecoversWithoutRestart() throws Exception {
        stubPeer(200);
        assertThat(send().isSuccess()).as("call before the rotation").isTrue();
        DecodedJWT issuedBeforeRotation = lastToken();
        assertThat(issuedBeforeRotation.getIssuer()).isEqualTo(realmUrl(PLATFORM_REALM));
        assertThat(Duration.between(issuedBeforeRotation.getIssuedAtAsInstant(),
                issuedBeforeRotation.getExpiresAtAsInstant())).as("precondition: shortened lifespan")
                .isEqualTo(Duration.ofSeconds(SHORT_LIFESPAN_SECONDS));

        String rotatedSecret = backendClient().generateNewSecret().getValue();
        assertThat(rotatedSecret).isNotEqualTo(CONFIGURED_SECRET);

        // Still fresh in the cache: the token issued before the rotation stays valid and is reused.
        assertThat(send().isSuccess()).as("call with the cached token").isTrue();
        assertThat(lastToken().getToken()).isEqualTo(issuedBeforeRotation.getToken());
        int requestsBeforeFailure = peerRequests().size();

        waitUntilStale(issuedBeforeRotation);
        GenericApiResponse<String> failed = send();
        assertThat(failed.isSuccess()).as("call after the cache entry went stale").isFalse();
        assertThat(peerRequests()).as("no request sent without a fresh token").hasSize(requestsBeforeFailure);
        assertThat(send().isSuccess()).as("no stale token reused on the next call either").isFalse();
        assertThat(peerRequests()).hasSize(requestsBeforeFailure);
        assertTokenRequestFailureLogged();
        assertNoSecretOrTokenLogged(rotatedSecret, issuedBeforeRotation.getToken());

        restoreSecret(backendClient());
        assertThat(send().isSuccess()).as("call after the secret is restored").isTrue();
        assertThat(peerRequests()).hasSize(requestsBeforeFailure + 1);
        DecodedJWT issuedAfterRestore = lastToken();
        assertThat(issuedAfterRestore.getToken()).isNotEqualTo(issuedBeforeRotation.getToken());
        assertThat(issuedAfterRestore.getClaim("azp").asString()).isEqualTo(BACKEND_CLIENT_ID);
    }

    @Test
    @DisplayName("Rotated secret: after a peer's 401 the cached token is evicted and not reused")
    void peerRejectionEvictsTokenAndRetryFailsWithoutStaleReuse() throws Exception {
        stubPeer(200);
        assertThat(send().isSuccess()).isTrue();
        String issuedBeforeRotation = lastToken().getToken();

        String rotatedSecret = backendClient().generateNewSecret().getValue();
        stubPeer(401);
        int requestsBefore = peerRequests().size();

        GenericApiResponse<String> rejected = send();
        assertThat(rejected.isSuccess()).isFalse();
        // One attempt with the cached token; the retry after eviction needs a new token, which cannot be had.
        assertThat(peerRequests()).hasSize(requestsBefore + 1);
        assertThat(lastToken().getToken()).isEqualTo(issuedBeforeRotation);
        assertTokenRequestFailureLogged();
        assertNoSecretOrTokenLogged(rotatedSecret, issuedBeforeRotation);

        restoreSecret(backendClient());
        stubPeer(200);
        assertThat(send().isSuccess()).as("call after the secret is restored").isTrue();
        assertThat(lastToken().getToken()).isNotEqualTo(issuedBeforeRotation);
    }

    private GenericApiResponse<String> send() {
        JsonNode body = OBJECT_MAPPER.createObjectNode().put("@type", "CatalogRequestMessage");
        return okHttpRestClient.sendRequestProtocol(wireMock.baseUrl() + PEER_PATH, body,
                credentialUtils::getConnectorCredentials);
    }

    private void stubPeer(int status) {
        wireMock.stubFor(WireMock.post(PEER_PATH).willReturn(WireMock.aResponse()
                .withStatus(status)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withBody("{}")));
    }

    private List<LoggedRequest> peerRequests() {
        return wireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(PEER_PATH)));
    }

    private DecodedJWT lastToken() {
        List<LoggedRequest> requests = peerRequests();
        assertThat(requests).isNotEmpty();
        String authorization = requests.get(requests.size() - 1).getHeader(HttpHeaders.AUTHORIZATION);
        assertThat(authorization).startsWith(BEARER_PREFIX);
        return JWT.decode(authorization.substring(BEARER_PREFIX.length()));
    }

    private static void waitUntilStale(DecodedJWT token) throws InterruptedException {
        Instant staleAt = token.getExpiresAtAsInstant().minusSeconds(CACHE_REFRESH_MARGIN_SECONDS).plusSeconds(1);
        long waitMillis = Duration.between(Instant.now(), staleAt).toMillis();
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
    }

    // The failed token request is logged at ERROR by the outbound Keycloak client, with the HTTP status and
    // the OAuth error code returned by Keycloak.
    private void assertTokenRequestFailureLogged() {
        assertThat(logAppender.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR
                        && KeycloakAuthenticationService.class.getName().equals(event.getLoggerName()))
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("client " + BACKEND_CLIENT_ID)
                        .contains("HTTP 401")
                        .contains("error=unauthorized_client"));
        assertThat(logAppender.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR
                        && OkHttpRestClient.class.getName().equals(event.getLoggerName()))
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("not sent").contains("HTTP 401"));
    }

    // Every log line of the connector, whatever its logger and level; the test peer's own request journal
    // (WireMock) is not connector output and echoes the headers it received, so it is left out.
    private void assertNoSecretOrTokenLogged(String rotatedSecret, String token) {
        List<ILoggingEvent> connectorEvents = logAppender.list.stream()
                .filter(event -> !event.getLoggerName().toLowerCase(Locale.ROOT).contains("wiremock"))
                .toList();
        assertThat(connectorEvents).isNotEmpty();
        for (ILoggingEvent event : connectorEvents) {
            String text = event.getFormattedMessage() + throwableText(event.getThrowableProxy());
            assertThat(text).doesNotContain(CONFIGURED_SECRET, rotatedSecret, token);
        }
    }

    private static String throwableText(IThrowableProxy proxy) {
        StringBuilder text = new StringBuilder();
        for (IThrowableProxy current = proxy; current != null; current = current.getCause()) {
            text.append(' ').append(current.getMessage());
        }
        return text.toString();
    }

    private static ClientResource backendClient() {
        RealmResource realm = keycloakAdminClient().realm(PLATFORM_REALM);
        String id = realm.clients().findByClientId(BACKEND_CLIENT_ID).get(0).getId();
        return realm.clients().get(id);
    }

    private static void restoreSecret(ClientResource client) {
        ClientRepresentation representation = client.toRepresentation();
        representation.setSecret(CONFIGURED_SECRET);
        client.update(representation);
    }

    private static void setLifespan(ClientResource client, String value) {
        ClientRepresentation representation = client.toRepresentation();
        Map<String, String> attributes = new HashMap<>(Optional.ofNullable(representation.getAttributes()).orElse(Map.of()));
        attributes.put(LIFESPAN_ATTRIBUTE, value);
        representation.setAttributes(attributes);
        client.update(representation);
    }

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
