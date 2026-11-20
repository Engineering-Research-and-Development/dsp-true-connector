package it.eng.connector.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.model.Role;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.service.AuthService;
import it.eng.tools.auth.M2mTokenCache;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.util.CredentialUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs catalog, contract negotiation and transfer between two connectors in {@code INTERNAL} mode: the
 * counterpart of {@link KeycloakConnectorToConnectorIT} with the connector's self-issued tokens.
 *
 * <p>The consumer's API is called with the token of the seeded tenant admin ({@code admin@mail.com}); the
 * protocol calls carry the locally minted {@code INTERNAL} protocol token (role {@code CONNECTOR} only, no user), and the loopback
 * calls use the {@code /internal} zone as in {@code KEYCLOAK} mode. Nothing on the path is stubbed.
 */
@DisplayName("INTERNAL mode: catalog, negotiation and transfer between two connectors")
class InternalConnectorToConnectorIT extends ConnectorToConnectorFlowSupport {

    private static final int CONSUMER_PORT = 8189;
    private static final int PROVIDER_PORT = 8290;
    private static final String CONNECTOR_ROLE = "CONNECTOR";
    private static final String INTERNAL_CONNECTOR_SLOT = "connector-m2m";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeAll
    static void startConnectors() {
        startInstances("internal", CONSUMER_PORT, PROVIDER_PORT, Map.of("application.auth.provider", "INTERNAL"),
                UserAndLoginObserver::new);
    }

    /**
     * Wraps {@link AuthService} and {@link UserRepository} of an instance in Mockito mocks that delegate to the
     * real bean and record every call, so the test can prove that the outbound protocol path never reaches
     * them. A post-processor is used because both instances are booted by {@code SpringApplicationBuilder} in
     * this JVM, where {@code @SpyBean} / {@code @MockitoSpyBean} do not apply.
     */
    private static final class UserAndLoginObserver implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof AuthService) {
                return Mockito.mock(AuthService.class, AdditionalAnswers.delegatesTo(bean));
            }
            if (bean instanceof UserRepository) {
                return Mockito.mock(UserRepository.class, AdditionalAnswers.delegatesTo(bean));
            }
            return bean;
        }
    }

    @Override
    protected String consumerApiToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(consumer.baseUrl() + ApiEndpoints.AUTH_V1 + "/login"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\": \"admin@mail.com\", \"password\": \"password\"}"))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("INTERNAL login").isEqualTo(200);
        return OBJECT_MAPPER.readTree(response.body()).get("access_token").asText();
    }

    @Override
    protected void assertConnectorToken(RecordedCall call) {
        assertThat(call.issuer()).as("iss of %s %s", call.method(), call.path())
                .isNotEqualTo("urn:true-connector:internal");
        assertThat(call.realmRoles()).as("roles of %s %s", call.method(), call.path()).containsExactly(CONNECTOR_ROLE);
        assertThat(call.claimNames()).as("claims of %s %s", call.method(), call.path())
                .doesNotContain("email", "tenant_id", "token_type");
    }

    @Test
    @DisplayName("No user with role CONNECTOR exists in the Mongo of either connector")
    void noConnectorUserExistsInEitherInstance() {
        for (Instance instance : List.of(consumer, provider)) {
            assertThat(instance.bean(UserRepository.class).findAll())
                    .as("users of %s", instance.name())
                    .noneMatch(user -> user.getRole() == Role.CONNECTOR);
        }
    }

    @Test
    @DisplayName("The outbound protocol token is minted without AuthService or UserRepository and carries only CONNECTOR")
    void outboundProtocolTokenNeedsNoLoginAndNoUser() throws Exception {
        String apiToken = consumerApiToken();
        // Force a fresh mint inside the observed window, then observe the outbound catalog request only.
        consumer.bean(CredentialUtils.class).invalidateCachedCredentials();
        for (Instance instance : List.of(consumer, provider)) {
            Mockito.clearInvocations(instance.bean(AuthService.class), instance.bean(UserRepository.class));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(consumer.baseUrl() + ApiEndpoints.PROXY_V1 + "/catalogs"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(
                        OBJECT_MAPPER.writeValueAsString(Map.of("Forward-To", provider.protocolUrl()))))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("catalog request").isEqualTo(200);

        for (Instance instance : List.of(consumer, provider)) {
            Mockito.verifyNoInteractions(instance.bean(AuthService.class), instance.bean(UserRepository.class));
        }

        // The slot now holds the token that was just minted and sent; decode it.
        String authorization = consumer.bean(CredentialUtils.class).getConnectorCredentials();
        assertThat(authorization).startsWith("Bearer ");
        DecodedJWT jwt = JWT.decode(authorization.substring("Bearer ".length()));
        assertThat(jwt.getClaims().keySet()).as("claims of the protocol token")
                .doesNotContain("email", "tenant_id", "token_type");
        assertThat(jwt.getSubject()).isEqualTo(JwtService.PROTOCOL_TOKEN_SUBJECT);
        assertThat(jwt.getClaim("realm_access").asMap()).containsOnlyKeys("roles")
                .containsEntry("roles", List.of(CONNECTOR_ROLE));
    }

    /**
     * The {@code INTERNAL} connector token is cached in its own slot only; the internal-call token is never
     * cached.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void assertOutboundTokenCache(Instance instance) {
        Map<String, ?> cache = (Map<String, ?>) ReflectionTestUtils.getField(instance.bean(M2mTokenCache.class), "cache");
        Set<String> keys = cache == null ? Set.of() : cache.keySet();
        assertThat(keys).containsExactly(INTERNAL_CONNECTOR_SLOT);
    }
}
