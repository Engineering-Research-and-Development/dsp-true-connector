package it.eng.tools.auth.keycloak;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import it.eng.tools.auth.M2mTokenCache;
import it.eng.tools.auth.M2mTokenUnavailableException;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link KeycloakAuthenticationService} against a local stand-in for the {@code platform} realm.
 */
class KeycloakAuthenticationServiceTest {

    private static final String CLIENT_ID = "dsp-connector-backend";
    private static final String CLIENT_SECRET = "very-secret-client-value";
    private static final String REMOVED_REALM_PATH = "/realms/dsp-connector/";

    private FakeKeycloakServer keycloak;
    private KeycloakAuthenticationProperties properties;
    private M2mTokenCache tokenCache;
    private KeycloakAuthenticationService service;

    private Logger serviceLogger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() throws IOException {
        keycloak = new FakeKeycloakServer();
        KeycloakTrustProperties trustProperties = new KeycloakTrustProperties();
        trustProperties.setBaseUrl(keycloak.baseUrl());
        trustProperties.init();
        properties = new KeycloakAuthenticationProperties();
        properties.setClientId(CLIENT_ID);
        properties.setClientSecret(CLIENT_SECRET);
        tokenCache = new M2mTokenCache();
        service = new KeycloakAuthenticationService(properties, trustProperties,
                new KeycloakDiscoveryClient(trustProperties), tokenCache, new OkHttpClient());

        serviceLogger = (Logger) LoggerFactory.getLogger(KeycloakAuthenticationService.class);
        previousLevel = serviceLogger.getLevel();
        serviceLogger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(appender);
        serviceLogger.setLevel(previousLevel);
        keycloak.close();
    }

    @Test
    @DisplayName("The token endpoint comes from the platform discovery document, never from the removed realm")
    void tokenEndpointFromDiscovery() {
        String token = service.getConnectorToken();

        assertNotNull(token);
        assertEquals(List.of(keycloak.discoveryPath(), keycloak.tokenPath()), keycloak.requestPaths());
        assertTrue(keycloak.requestPaths().stream().noneMatch(path -> path.contains(REMOVED_REALM_PATH)));
        String form = keycloak.tokenRequestBodies().getFirst();
        assertTrue(form.contains("grant_type=client_credentials"));
        assertTrue(form.contains("client_id=" + CLIENT_ID));
    }

    @Test
    @DisplayName("A valid connector token is cached and the endpoint is resolved once")
    void connectorTokenCached() {
        String first = service.getConnectorToken();
        String second = service.getConnectorToken();

        assertEquals(first, second);
        assertEquals(1, keycloak.count(keycloak.discoveryPath()));
        assertEquals(1, keycloak.count(keycloak.tokenPath()));
    }

    @Test
    @DisplayName("A token within the 30 s refresh margin of its expiry is not reused")
    void nearExpiryTokenNotReused() {
        keycloak.issueTokens(Duration.ofSeconds(20));

        String first = service.getConnectorToken();
        String second = service.getConnectorToken();

        assertNotEquals(first, second);
        assertEquals(2, keycloak.count(keycloak.tokenPath()));
        assertEquals(1, keycloak.count(keycloak.discoveryPath()));
    }

    @Test
    @DisplayName("An evicted connector token is fetched again")
    void evictedTokenRefetched() {
        String first = service.getConnectorToken();
        service.invalidateConnectorToken();
        String second = service.getConnectorToken();

        assertNotEquals(first, second);
        assertEquals(2, keycloak.count(keycloak.tokenPath()));
    }

    @Test
    @DisplayName("Evicting before any token was fetched is a no-op")
    void invalidateBeforeFirstFetchIsNoop() {
        service.invalidateConnectorToken();

        assertTrue(keycloak.requestPaths().isEmpty());
    }

    @Test
    @DisplayName("The cache key is built from client id and token endpoint and never collides with the INTERNAL slot")
    void cacheKeyIsolatedFromInternalSlot() {
        service.getConnectorToken();

        tokenCache.invalidate("connector-m2m");
        service.getConnectorToken();
        assertEquals(1, keycloak.count(keycloak.tokenPath()));

        tokenCache.invalidate(KeycloakAuthenticationService.CACHE_KEY_PREFIX + CLIENT_ID + "@" + keycloak.tokenEndpoint());
        service.getConnectorToken();
        assertEquals(2, keycloak.count(keycloak.tokenPath()));
    }

    @Test
    @DisplayName("Wrong secret: clear error with status and OAuth error, no secret logged, nothing cached, next call retries without a new discovery")
    void wrongSecretFailsClearly() {
        keycloak.tokenResponse(401,
                "{\"error\":\"invalid_client\",\"error_description\":\"Invalid client or Invalid client credentials\"}");

        M2mTokenUnavailableException exception = assertThrows(M2mTokenUnavailableException.class,
                () -> service.getConnectorToken());

        assertTrue(exception.getMessage().contains("HTTP 401"));
        assertTrue(exception.getMessage().contains("invalid_client"));
        assertFalse(exception.getMessage().contains(CLIENT_SECRET));
        ILoggingEvent error = appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .findFirst()
                .orElseThrow();
        assertTrue(error.getFormattedMessage().contains("HTTP 401"));
        assertTrue(error.getFormattedMessage().contains("error=invalid_client"));
        assertTrue(error.getFormattedMessage().contains("Invalid client or Invalid client credentials"));
        assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains(CLIENT_SECRET)));

        keycloak.issueTokens(Duration.ofMinutes(5));
        String token = service.getConnectorToken();

        assertNotNull(token);
        assertEquals(2, keycloak.count(keycloak.tokenPath()));
        // An OAuth client error keeps the resolved endpoint: no new discovery request.
        assertEquals(1, keycloak.count(keycloak.discoveryPath()));
        assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains(token)));
    }

    @Test
    @DisplayName("A 400 unauthorized_client keeps the resolved endpoint")
    void oauthClientErrorKeepsEndpoint() {
        keycloak.tokenResponse(400, "{\"error\":\"unauthorized_client\"}");

        assertThrows(M2mTokenUnavailableException.class, () -> service.getConnectorToken());
        assertThrows(M2mTokenUnavailableException.class, () -> service.getConnectorToken());

        assertEquals(2, keycloak.count(keycloak.tokenPath()));
        assertEquals(1, keycloak.count(keycloak.discoveryPath()));
    }

    @Test
    @DisplayName("A 404 from the token endpoint resolves the endpoint again on the next call")
    void notFoundReResolvesEndpoint() {
        keycloak.tokenResponse(404, "{}");
        assertThrows(M2mTokenUnavailableException.class, () -> service.getConnectorToken());

        keycloak.issueTokens(Duration.ofMinutes(5));
        assertNotNull(service.getConnectorToken());

        assertEquals(2, keycloak.count(keycloak.discoveryPath()));
    }

    @Test
    @DisplayName("A 5xx from the token endpoint resolves the endpoint again on the next call")
    void serverErrorReResolvesEndpoint() {
        keycloak.tokenResponse(503, "{}");
        assertThrows(M2mTokenUnavailableException.class, () -> service.getConnectorToken());

        keycloak.issueTokens(Duration.ofMinutes(5));
        assertNotNull(service.getConnectorToken());

        assertEquals(2, keycloak.count(keycloak.discoveryPath()));
    }

    @Test
    @DisplayName("A transport failure resolves the endpoint again on the next call")
    void transportFailureReResolvesEndpoint() {
        keycloak.tokenEndpointOverride("http://127.0.0.1:1/token");
        assertThrows(M2mTokenUnavailableException.class, () -> service.getConnectorToken());

        keycloak.tokenEndpointOverride(null);
        assertNotNull(service.getConnectorToken());

        assertEquals(2, keycloak.count(keycloak.discoveryPath()));
        assertEquals(1, keycloak.count(keycloak.tokenPath()));
    }

    @Test
    @DisplayName("A non-JSON error body is reported with the status only")
    void nonJsonErrorBody() {
        keycloak.tokenResponse(502, "<html>Bad gateway</html>");

        M2mTokenUnavailableException exception = assertThrows(M2mTokenUnavailableException.class,
                () -> service.getConnectorToken());

        assertTrue(exception.getMessage().endsWith("failed with HTTP 502"));
        assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains("Bad gateway")));
    }

    @Test
    @DisplayName("A success response without access_token is a failure")
    void responseWithoutAccessToken() {
        keycloak.tokenResponse(200, "{\"token_type\":\"Bearer\"}");

        assertThrows(M2mTokenUnavailableException.class, () -> service.getConnectorToken());
    }

    @Test
    @DisplayName("An unreachable token endpoint is a failure")
    void unreachableTokenEndpoint() {
        keycloak.tokenEndpointOverride("http://127.0.0.1:1/token");

        M2mTokenUnavailableException exception = assertThrows(M2mTokenUnavailableException.class,
                () -> service.getConnectorToken());

        assertTrue(exception.getMessage().contains("http://127.0.0.1:1/token"));
    }

    @Test
    @DisplayName("A failed discovery request is a failure and no token request is sent")
    void discoveryFailure() {
        keycloak.discoveryStatus(500);

        assertThrows(M2mTokenUnavailableException.class, () -> service.getConnectorToken());
        assertEquals(0, keycloak.count(keycloak.tokenPath()));
    }

    @Test
    @DisplayName("A discovery document without token_endpoint is a failure")
    void discoveryWithoutTokenEndpoint() {
        keycloak.declareTokenEndpoint(false);

        assertThrows(M2mTokenUnavailableException.class, () -> service.getConnectorToken());
        assertEquals(0, keycloak.count(keycloak.tokenPath()));
    }

    @Test
    @DisplayName("A missing client secret is a failure and neither discovery nor a token request is sent")
    void missingClientSecret() {
        properties.setClientSecret(null);

        assertThrows(M2mTokenUnavailableException.class, () -> service.getConnectorToken());
        assertEquals(List.of(), keycloak.requestPaths());
    }

    @Test
    @DisplayName("With token caching disabled every connector call requests a new token")
    void tokenCachingDisabled() {
        properties.setTokenCaching(false);

        service.getConnectorToken();
        service.getConnectorToken();

        assertEquals(2, keycloak.count(keycloak.tokenPath()));
    }
}
