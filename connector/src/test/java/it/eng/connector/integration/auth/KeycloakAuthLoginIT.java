package it.eng.connector.integration.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.integration.BaseKeycloakIntegrationTest;
import it.eng.tools.controller.ApiEndpoints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class KeycloakAuthLoginIT extends BaseKeycloakIntegrationTest {

    private static final String LOGIN_PATH = ApiEndpoints.AUTH_V1 + "/login";
    private static final String REFRESH_PATH = ApiEndpoints.AUTH_V1 + "/refresh";
    private static final String LOGOUT_PATH = ApiEndpoints.AUTH_V1 + "/logout";
    private static final String EXISTING_UI_USER_EMAIL = "admin@test.com";
    private static final String EXISTING_UI_USER_PASSWORD = "admin123";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Successful login against Keycloak return valid access and refresh token")
    void successfulLoginAgainstKeycloakReturnValidAccessAndRefreshToken() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", EXISTING_UI_USER_EMAIL,
                                "password", EXISTING_UI_USER_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300));
    }

    @Test
    @DisplayName("Login failed with invalid credentials, 401 not authorized")
    void loginFailedWithInvalidCredentials() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", EXISTING_UI_USER_EMAIL,
                                "password", "wrongpassword"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should login successfully and then use refresh token to get new access token")
    void shouldLoginSuccessfullyAndUseRefreshToken() throws Exception {
        final ResultActions result = mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", EXISTING_UI_USER_EMAIL,
                                "password", EXISTING_UI_USER_PASSWORD))))
                .andExpect(status().isOk());

        String response = result.andReturn().getResponse().getContentAsString();

        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> map = objectMapper.readValue(response, Map.class);
        String refreshToken = (String) map.get("refresh_token");

        // use refresh token to fetch new access token
        mockMvc.perform(post(REFRESH_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300));
    }

    @Test
    @DisplayName("Should fail to fetch access token with invalid refresh token")
    void shouldFailToFetchAccessTokenWithInvalidRefreshToken() throws Exception {
        mockMvc.perform(post(REFRESH_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", "invalid_refresh_token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should successfully perform logout")
    void shouldLogout() throws Exception {
        final ResultActions result = mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", EXISTING_UI_USER_EMAIL,
                                "password", EXISTING_UI_USER_PASSWORD))))
                .andExpect(status().isOk());

        String response = result.andReturn().getResponse().getContentAsString();

        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> map = objectMapper.readValue(response, Map.class);
        String refreshToken = (String) map.get("refresh_token");

        mockMvc.perform(post(LOGOUT_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should fail to perform logout with invalid refresh token")
    void shouldFailToLogoutWithInvalidRefreshToken() throws Exception {
        mockMvc.perform(post(LOGOUT_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", "invalid_refresh_token"))))
                .andExpect(status().isUnauthorized());
    }
}
