package it.eng.connector.integration.auth;

import it.eng.connector.integration.BaseKeycloakIntegrationTest;
import it.eng.connector.rest.api.AuthController;
import it.eng.connector.service.AuthService;
import it.eng.tools.controller.ApiEndpoints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the connector offers no login, refresh or logout route in {@code KEYCLOAK} mode.
 *
 * <p>People sign in to Keycloak directly, so no {@link AuthService} or {@link AuthController} bean
 * exists and the {@code /api/v1/auth/*} routes have no handler.
 */
class KeycloakAuthRoutesAbsentIT extends BaseKeycloakIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("No AuthService or AuthController bean in Keycloak mode")
    void noAuthBeansInKeycloakMode() {
        assertThat(applicationContext.getBeanNamesForType(AuthService.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(AuthController.class)).isEmpty();
    }

    @ParameterizedTest(name = "POST /api/v1/auth/{0} with a tenant admin token returns 404")
    @ValueSource(strings = {"login", "refresh", "logout"})
    void authRouteWithAdminTokenReturnsNotFound(String route) throws Exception {
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/" + route)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A protected route still accepts a valid tenant admin token")
    void protectedRouteWithAdminTokenReturnsOk() throws Exception {
        mockMvc.perform(get(ApiEndpoints.NEGOTIATION_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken())))
                .andExpect(status().isOk());
    }
}
