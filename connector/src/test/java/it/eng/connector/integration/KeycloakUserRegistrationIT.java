package it.eng.connector.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import it.eng.connector.model.Role;
import it.eng.connector.model.UserDTO;
import it.eng.tools.controller.ApiEndpoints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying the Keycloak user registration path.
 *
 * <p>The Keycloak Admin REST API is stubbed with a standalone WireMock server started in a static
 * initializer so its port is available when the Spring context resolves
 * {@code application.keycloak.admin.server-url} via {@link DynamicPropertySource}.
 */
@TestPropertySource(properties = "application.auth.provider=KEYCLOAK")
class KeycloakUserRegistrationIT extends BaseKeycloakIntegrationTest {

    private static final String ADMIN_USERS_PATH = "/admin/realms/dsp-connector/users";

    /**
     * WireMock server started before Spring context initialisation so the port is stable when
     * {@link #keycloakAdminProperties} is first evaluated.
     */
    private static final WireMockServer ADMIN_API_MOCK;

    static {
        ADMIN_API_MOCK = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        ADMIN_API_MOCK.start();
    }

    @AfterAll
    static void stopAdminMock() {
        if (ADMIN_API_MOCK != null && ADMIN_API_MOCK.isRunning()) {
            ADMIN_API_MOCK.stop();
        }
    }

    @AfterEach
    void resetMock() {
        ADMIN_API_MOCK.resetAll();
    }

    /**
     * Overrides the Keycloak Admin API base URL with the WireMock port.
     *
     * @param registry the dynamic property registry
     */
    @DynamicPropertySource
    static void keycloakAdminProperties(DynamicPropertyRegistry registry) {
        registry.add("application.keycloak.admin.server-url",
                () -> "http://localhost:" + ADMIN_API_MOCK.port());
        registry.add("application.keycloak.admin.realm", () -> "dsp-connector");
    }

    @Test
    @DisplayName("POST /api/v1/users in Keycloak mode calls Admin API and returns 200 with user JSON")
    void createUser_keycloakMode_success() throws Exception {
        ADMIN_API_MOCK.stubFor(WireMock.post(urlEqualTo(ADMIN_USERS_PATH))
                .willReturn(aResponse().withStatus(201)));

        UserDTO userDTO = new UserDTO("First", "Last", "keycloak.user@test.com",
                "TestPass123!", null, Role.ADMIN, null);

        String token = superAdminAccessToken();
        mockMvc.perform(post(ApiEndpoints.USERS_V1)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("keycloak.user@test.com"));

        ADMIN_API_MOCK.verify(1, WireMock.postRequestedFor(urlEqualTo(ADMIN_USERS_PATH)));
    }

    @Test
    @DisplayName("POST /api/v1/users when Keycloak Admin returns 409 - connector returns 4xx")
    void createUser_keycloakMode_conflict_returns4xx() throws Exception {
        ADMIN_API_MOCK.stubFor(WireMock.post(urlEqualTo(ADMIN_USERS_PATH))
                .willReturn(aResponse().withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errorMessage\":\"User exists with same username or email\"}")));

        UserDTO userDTO = new UserDTO("First", "Last", "existing.user@test.com",
                "TestPass123!", null, Role.ADMIN, null);

        String token = adminAccessToken();
        mockMvc.perform(post(ApiEndpoints.USERS_V1)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userDTO)))
                .andExpect(status().is4xxClientError());
    }
}
