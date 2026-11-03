package it.eng.connector.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.auth.ConnectorCredentialProvider;
import it.eng.tools.auth.M2mTokenCache;
import it.eng.tools.util.CredentialUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs catalog, contract negotiation and transfer between two connectors in {@code KEYCLOAK} mode, with real
 * {@code platform} realm tokens from the shared Keycloak test container (seed realms of
 * {@link BaseKeycloakIntegrationTest}).
 *
 * <p>Both instances use the {@code dsp-connector-backend} client of {@code platform} for their outbound
 * protocol calls and accept only {@code platform} tokens on their protocol routes. The consumer's API is
 * called with a tenant {@code ADMIN} token of {@code tenant-EngineeringA}. Loopback calls use the
 * {@code /internal} zone. Nothing on the path is stubbed. The {@code INTERNAL} mode counterpart is
 * {@link InternalConnectorToConnectorIT}.
 */
@DisplayName("KEYCLOAK mode: catalog, negotiation and transfer between two connectors with platform tokens")
class KeycloakConnectorToConnectorIT extends ConnectorToConnectorFlowSupport {

    private static final int CONSUMER_PORT = 8187;
    private static final int PROVIDER_PORT = 8288;
    private static final String PLATFORM_REALM = "platform";
    private static final String TENANT_REALM = "tenant-EngineeringA";
    private static final String BACKEND_CLIENT_ID = "dsp-connector-backend";
    private static final String BACKEND_CLIENT_SECRET = "dsp-connector-backend-secret";
    private static final String CONNECTOR_ROLE = "CONNECTOR";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeAll
    static void startConnectors() {
        // Reading the base URL starts the shared Keycloak container and imports the seed realms.
        String keycloakBaseUrl = BaseKeycloakIntegrationTest.keycloakBaseUrl();
        startInstances("kc", CONSUMER_PORT, PROVIDER_PORT, Map.of(
                "application.auth.provider", "KEYCLOAK",
                "application.keycloak.base-url", keycloakBaseUrl,
                "application.keycloak.platform-realm", PLATFORM_REALM,
                "application.keycloak.admin-realm", "connector-admin",
                "application.keycloak.audience", "dsp-true-connector",
                "application.keycloak.backend.client-id", BACKEND_CLIENT_ID,
                "application.keycloak.backend.client-secret", BACKEND_CLIENT_SECRET));
    }

    @Override
    protected String consumerApiToken() throws Exception {
        String form = "grant_type=password&client_id=keycloak-api&username=admin&password="
                + URLEncoder.encode("password", StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BaseKeycloakIntegrationTest.realmUrl(TENANT_REALM) + "/protocol/openid-connect/token"))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("tenant admin token request").isEqualTo(200);
        return OBJECT_MAPPER.readTree(response.body()).get("access_token").asText();
    }

    @Override
    protected void assertConnectorToken(RecordedCall call) {
        assertThat(call.issuer()).as("iss of %s %s", call.method(), call.path())
                .isEqualTo(BaseKeycloakIntegrationTest.realmUrl(PLATFORM_REALM));
        assertThat(call.authorizedParty()).as("azp of %s %s", call.method(), call.path()).isEqualTo(BACKEND_CLIENT_ID);
        assertThat(call.realmRoles()).as("roles of %s %s", call.method(), call.path()).contains(CONNECTOR_ROLE);
    }

    /**
     * The outbound token comes from the platform token endpoint of the discovery document and is cached only
     * under its Keycloak key: there is no {@code INTERNAL} connector slot and no entry for the internal-call
     * token, which is minted per call.
     */
    @Override
    protected void assertOutboundTokenCache(Instance instance) {
        assertThat(instance.context().getBeanProvider(ConnectorCredentialProvider.class).getIfAvailable())
                .as("no INTERNAL connector credential provider in KEYCLOAK mode").isNull();
        assertThat(cacheKeys(instance)).containsExactly("keycloak-m2m:" + BACKEND_CLIENT_ID + "@"
                + BaseKeycloakIntegrationTest.realmUrl(PLATFORM_REALM) + "/protocol/openid-connect/token");
        String authorization = instance.bean(CredentialUtils.class).getConnectorCredentials();
        assertThat(authorization).startsWith("Bearer ");
    }

    @SuppressWarnings("unchecked")
    static Set<String> cacheKeys(Instance instance) {
        Map<String, ?> cache = (Map<String, ?>) ReflectionTestUtils.getField(instance.bean(M2mTokenCache.class), "cache");
        return cache == null ? Set.of() : cache.keySet();
    }
}
