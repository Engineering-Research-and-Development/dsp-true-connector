package it.eng.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;
import it.eng.tools.event.TenantUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the tenant name synchronization of {@link KeycloakUserService}.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakUserServiceTest {

    private static final String SERVER_URL = "http://keycloak:8080/";
    private static final String REALM = "dsp-connector";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KeycloakAuthenticationService keycloakAuthService;
    @Mock
    private HttpClient httpClient;

    private KeycloakUserService service;

    @BeforeEach
    void setUp() {
        service = new KeycloakUserService(keycloakAuthService, SERVER_URL, REALM, httpClient);
        when(keycloakAuthService.fetchToken("ROLE_ADMIN")).thenReturn("admin-token");
    }

    @Test
    @DisplayName("syncTenantName updates only the tenant's users whose tenant_name differs")
    void syncTenantNameUpdatesStaleUsers() throws Exception {
        String users = """
                [
                  {"id": "u1", "username": "a", "attributes": {"tenant_id": ["acme"], "tenant_name": ["Old"]}},
                  {"id": "u2", "username": "b", "attributes": {"tenant_id": ["acme"], "tenant_name": ["New"]}}
                ]""";
        doReturn(response(200, users)).when(httpClient).send(argThat(r -> "GET".equals(r.method())), any());
        doReturn(response(204, "")).when(httpClient).send(argThat(r -> "PUT".equals(r.method())), any());

        int updated = service.syncTenantName("acme", "New");

        assertEquals(1, updated);
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture(), any());
        HttpRequest search = requests.getAllValues().get(0);
        assertEquals("http://keycloak:8080/admin/realms/dsp-connector/users"
                        + "?briefRepresentation=false&q=tenant_id%3Aacme&first=0&max=100",
                search.uri().toString());
        HttpRequest update = requests.getAllValues().get(1);
        assertEquals("http://keycloak:8080/admin/realms/dsp-connector/users/u1", update.uri().toString());
        assertEquals("Bearer admin-token", update.headers().firstValue("Authorization").orElseThrow());
        JsonNode body = objectMapper.readTree(body(update));
        assertEquals("New", body.path("attributes").path("tenant_name").path(0).asText());
        assertEquals("acme", body.path("attributes").path("tenant_id").path(0).asText());
    }

    @Test
    @DisplayName("onTenantUpdated logs a failing Keycloak call instead of throwing")
    void onTenantUpdatedSwallowsKeycloakFailure() throws Exception {
        doReturn(response(403, "{\"error\":\"HTTP 403 Forbidden\"}")).when(httpClient).send(any(), any());

        assertDoesNotThrow(() -> service.onTenantUpdated(new TenantUpdatedEvent("acme", "New")));
        verify(httpClient, never()).send(argThat(r -> "PUT".equals(r.method())), any());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        if (status != 204) {
            when(response.body()).thenReturn(body);
        }
        return response;
    }

    // Reads the body of a request built with BodyPublishers.ofString.
    private static String body(HttpRequest request) {
        List<ByteBuffer> buffers = new ArrayList<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                buffers.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        StringBuilder body = new StringBuilder();
        buffers.forEach(buffer -> body.append(StandardCharsets.UTF_8.decode(buffer)));
        return body.toString();
    }
}
