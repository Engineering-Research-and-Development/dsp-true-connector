package it.eng.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import it.eng.tools.auth.keycloak.DiscoveryDocument;
import it.eng.tools.auth.keycloak.KeycloakDiscoveryClient;
import it.eng.tools.auth.keycloak.KeycloakDiscoveryException;
import it.eng.tools.auth.keycloak.KeycloakTrustProperties;
import it.eng.tools.event.realm.RealmCredentialsChangedEvent;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.UpstreamServiceException;
import it.eng.tools.exception.UserManagementNotConfiguredException;
import it.eng.tools.model.Tenant;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KeycloakUserService}.
 *
 * <p>A local HTTP server stands in for the token endpoint and the Keycloak Admin REST API of the tenant realm.
 */
class KeycloakUserServiceTest {

    private static final String TENANT_ID = "engineering-a";
    private static final String REALM = "tenant-EngineeringA";
    private static final String SECRET = "tenant-secret";
    private static final String USERS_PATH = "/admin/realms/" + REALM + "/users";
    private static final String TOKEN_PATH = "/realms/" + REALM + "/protocol/openid-connect/token";

    private TenantService tenantService;
    private KeycloakDiscoveryClient discoveryClient;
    private KeycloakTrustProperties properties;
    private HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger tokenCounter = new AtomicInteger();
    private volatile int usersStatus = 200;
    private volatile String usersBody = "[]";
    private volatile int tokenStatus = 200;
    private volatile String rejectedBearer;

    private KeycloakUserService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();

        properties = new KeycloakTrustProperties();
        properties.setBaseUrl(baseUrl + "/");

        tenantService = mock(TenantService.class);
        when(tenantService.findById(TENANT_ID)).thenReturn(tenant(REALM));
        when(tenantService.userManagementSecret(any(Tenant.class))).thenReturn(Optional.of(SECRET));

        discoveryClient = mock(KeycloakDiscoveryClient.class);
        when(discoveryClient.fetch(properties.issuerFor(REALM))).thenReturn(
                new DiscoveryDocument(properties.issuerFor(REALM), baseUrl + "/jwks", baseUrl + TOKEN_PATH));

        service = new KeycloakUserService(tenantService, properties, discoveryClient, HttpClient.newHttpClient());
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        server.stop(0);
    }

    @Test
    @DisplayName("Requests the token with the tenant client id and secret and lists the realm of the tenant")
    void findUsers_usesTenantClientAndRealm() {
        usersBody = "[{\"username\":\"alice\"},{\"username\":\"bob\"}]";

        Collection<JsonNode> users = service.findUsers(null);

        assertEquals(List.of("alice", "bob"), users.stream().map(user -> user.get("username").asText()).toList());
        RecordedRequest tokenRequest = requests.get(0);
        assertEquals("POST", tokenRequest.method());
        assertEquals(TOKEN_PATH, tokenRequest.path());
        assertTrue(tokenRequest.body().contains("grant_type=client_credentials"));
        assertTrue(tokenRequest.body().contains("client_id=connector-user-reader"));
        assertTrue(tokenRequest.body().contains("client_secret=" + SECRET));
        RecordedRequest usersRequest = requests.get(1);
        assertEquals(USERS_PATH, usersRequest.path());
        assertNull(usersRequest.query());
        assertEquals("Bearer token-1", usersRequest.authorization());
    }

    @Test
    @DisplayName("Adds an exact, encoded e-mail filter when an e-mail is given and ignores a blank one")
    void findUsers_emailFilter() {
        service.findUsers("alice+x@mail.com");
        service.findUsers("  ");

        List<RecordedRequest> userRequests = requests.stream().filter(r -> r.path().equals(USERS_PATH)).toList();
        assertEquals("email=alice%2Bx%40mail.com&exact=true", userRequests.get(0).query());
        assertNull(userRequests.get(1).query());
    }

    @Test
    @DisplayName("Caches the token and reads the tenant and the discovery document once per token request")
    void findUsers_cachesToken() {
        service.findUsers(null);
        service.findUsers(null);

        assertEquals(1, tokenCounter.get());
        verify(discoveryClient, times(1)).fetch(properties.issuerFor(REALM));
        verify(tenantService, times(2)).findById(TENANT_ID);
        verify(tenantService, never()).userManagementSecret(TENANT_ID);
    }

    @Test
    @DisplayName("A rotated secret never reuses the token obtained with the old secret, even without an event")
    void findUsers_rotatedSecret_requestsNewToken() {
        service.findUsers(null);
        when(tenantService.userManagementSecret(any(Tenant.class))).thenReturn(Optional.of("rotated-secret"));

        service.findUsers(null);

        assertEquals(2, tokenCounter.get());
        List<RecordedRequest> tokenRequests = requests.stream().filter(r -> r.path().equals(TOKEN_PATH)).toList();
        assertTrue(tokenRequests.get(1).body().contains("client_secret=rotated-secret"));
        List<RecordedRequest> userRequests = requests.stream().filter(r -> r.path().equals(USERS_PATH)).toList();
        assertEquals("Bearer token-2", userRequests.get(1).authorization());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401})
    @DisplayName("Only a 400 or 401 from the token endpoint means the credentials are not configured")
    void findUsers_tokenClientError_throwsNotConfigured(int status) {
        tokenStatus = status;

        assertThrows(UserManagementNotConfiguredException.class, () -> service.findUsers(null));
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 429, 500, 503})
    @DisplayName("Any other token endpoint failure is an upstream error, not a missing configuration")
    void findUsers_tokenOtherFailure_throwsUpstream(int status) {
        tokenStatus = status;

        UpstreamServiceException exception =
                assertThrows(UpstreamServiceException.class, () -> service.findUsers(null));

        assertFalse(exception.getMessage().contains("unauthorized_client"));
        assertTrue(requests.stream().noneMatch(r -> r.path().equals(USERS_PATH)));
    }

    @Test
    @DisplayName("An I/O failure reaching Keycloak is an upstream error")
    void findUsers_unreachable_throwsUpstream() {
        server.stop(0);

        assertThrows(UpstreamServiceException.class, () -> service.findUsers(null));
    }

    @Test
    @DisplayName("A discovery failure is an upstream error")
    void findUsers_discoveryFails_throwsUpstream() {
        when(discoveryClient.fetch(properties.issuerFor(REALM))).thenThrow(new KeycloakDiscoveryException("down"));

        assertThrows(UpstreamServiceException.class, () -> service.findUsers(null));
    }

    @Test
    @DisplayName("A server error from the users endpoint is an upstream error")
    void findUsers_usersServerError_throwsUpstream() {
        usersStatus = 502;

        assertThrows(UpstreamServiceException.class, () -> service.findUsers(null));
    }

    @Test
    @DisplayName("Evicts the cached token when the realm credentials of the tenant change")
    void onRealmCredentialsChanged_evictsToken() {
        service.findUsers(null);

        service.onRealmCredentialsChanged(new RealmCredentialsChangedEvent("other-tenant"));
        service.findUsers(null);
        assertEquals(1, tokenCounter.get());

        service.onRealmCredentialsChanged(new RealmCredentialsChangedEvent(TENANT_ID));
        service.findUsers(null);
        assertEquals(2, tokenCounter.get());
    }

    @Test
    @DisplayName("Evicts the token on a 401 from Keycloak and retries once with a fresh token")
    void findUsers_unauthorized_retriesOnceWithFreshToken() {
        rejectedBearer = "token-1";

        service.findUsers(null);

        assertEquals(2, tokenCounter.get());
        List<RecordedRequest> userRequests = requests.stream().filter(r -> r.path().equals(USERS_PATH)).toList();
        assertEquals(2, userRequests.size());
        assertEquals("Bearer token-2", userRequests.get(1).authorization());
    }

    @Test
    @DisplayName("Gives up after one retry when Keycloak keeps answering 401")
    void findUsers_unauthorizedTwice_throwsBadRequest() {
        usersStatus = 401;

        assertThrows(BadRequestException.class, () -> service.findUsers(null));

        assertEquals(2, requests.stream().filter(r -> r.path().equals(USERS_PATH)).count());
    }

    @Test
    @DisplayName("Throws BadRequestException (400) without a tenant in the context")
    void findUsers_noTenant_throwsBadRequest() {
        TenantContextHolder.clear();

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.findUsers(null));

        assertEquals("X-Tenant-Id required", exception.getMessage());
        assertTrue(requests.isEmpty());
    }

    @Test
    @DisplayName("Throws UserManagementNotConfiguredException (409) for a tenant without realm")
    void findUsers_noRealm_throwsNotConfigured() {
        when(tenantService.findById(TENANT_ID)).thenReturn(tenant(null));

        assertThrows(UserManagementNotConfiguredException.class, () -> service.findUsers(null));

        verify(tenantService, never()).userManagementSecret(any(Tenant.class));
        assertTrue(requests.isEmpty());
    }

    @Test
    @DisplayName("Throws UserManagementNotConfiguredException (409) for a tenant without stored secret")
    void findUsers_noSecret_throwsNotConfigured() {
        when(tenantService.userManagementSecret(any(Tenant.class))).thenReturn(Optional.empty());

        UserManagementNotConfiguredException exception =
                assertThrows(UserManagementNotConfiguredException.class, () -> service.findUsers(null));

        assertEquals("User management not configured for this tenant", exception.getMessage());
        assertTrue(requests.isEmpty());
    }

    @Test
    @DisplayName("Throws UserManagementNotConfiguredException when the realm rejects the client credentials")
    void findUsers_tokenRejected_throwsNotConfigured() {
        tokenStatus = 401;

        UserManagementNotConfiguredException exception =
                assertThrows(UserManagementNotConfiguredException.class, () -> service.findUsers(null));

        assertFalse(exception.getMessage().contains(SECRET));
    }

    @Test
    @DisplayName("Throws BadRequestException without echoing the Keycloak body when it does not return 200")
    void findUsers_nonOkStatus_throwsBadRequest() {
        usersStatus = 403;
        usersBody = "{\"error\":\"forbidden\"}";

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.findUsers(null));

        assertEquals("Keycloak returned 403 when listing users", exception.getMessage());
    }

    @Test
    @DisplayName("Throws BadRequestException when the response is not valid JSON")
    void findUsers_invalidJson_throwsBadRequest() {
        usersBody = "not-json";

        assertThrows(BadRequestException.class, () -> service.findUsers(null));
    }

    private static Tenant tenant(String realm) {
        return Tenant.Builder.newInstance().id(TENANT_ID).name("Engineering A").participantId("urn:participant:a").realm(realm).enabled(true).build();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(), authorization, body));
        int status;
        String response;
        if (exchange.getRequestURI().getPath().equals(TOKEN_PATH)) {
            status = tokenStatus;
            response = status == 200
                    ? "{\"access_token\":\"token-" + tokenCounter.incrementAndGet() + "\",\"expires_in\":300}"
                    : "{\"error\":\"unauthorized_client\"}";
        } else if (rejectedBearer != null && ("Bearer " + rejectedBearer).equals(authorization)) {
            status = 401;
            response = "{}";
        } else {
            status = usersStatus;
            response = usersBody;
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private record RecordedRequest(String method, String path, String query, String authorization, String body) {
    }
}
