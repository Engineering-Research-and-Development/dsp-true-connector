package it.eng.connector.integration.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.integration.BaseKeycloakIntegrationTest;
import it.eng.connector.service.KeycloakUserService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.TenantUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class KeycloakAuthLoginIT extends BaseKeycloakIntegrationTest {

    private static final String LOGIN_PATH = ApiEndpoints.AUTH_V1 + "/login";
    private static final String REFRESH_PATH = ApiEndpoints.AUTH_V1 + "/refresh";
    private static final String LOGOUT_PATH = ApiEndpoints.AUTH_V1 + "/logout";
    private static final String EXISTING_UI_USER_EMAIL = "admin@test.com";
    private static final String EXISTING_UI_USER_PASSWORD = "admin123";

    private static final String SUPER_ADMIN_EMAIL = "superadmin@test.com";
    private static final String SUPER_ADMIN_PASSWORD = "password";
    private static final String TENANT_ID = "engineering";
    private static final String TENANT_NAME = "Engineering";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private KeycloakUserService keycloakUserService;

    @Test
    @DisplayName("Access token of a tenant admin carries the user identity and tenant claims")
    void adminAccessTokenCarriesUserClaims() throws Exception {
        DecodedJWT accessToken = JWT.decode(login(EXISTING_UI_USER_EMAIL, EXISTING_UI_USER_PASSWORD).get("access_token"));

        assertThat(accessToken.getSubject()).isNotBlank();
        assertThat(accessToken.getClaim("given_name").asString()).isEqualTo("Admin");
        assertThat(accessToken.getClaim("family_name").asString()).isEqualTo("User");
        assertThat(accessToken.getClaim("email").asString()).isEqualTo(EXISTING_UI_USER_EMAIL);
        assertThat(accessToken.getClaim("realm_access").asMap().get("roles")).asList().contains("ADMIN");
        assertThat(accessToken.getClaim("tenant_id").asString()).isEqualTo(TENANT_ID);
        assertThat(accessToken.getClaim("tenant_name").asString()).isEqualTo(TENANT_NAME);
        assertThat(accessToken.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("Access token of a super-admin without a tenant has the SUPER_ADMIN role and no tenant claims")
    void superAdminAccessTokenHasNoTenantClaims() throws Exception {
        DecodedJWT accessToken = JWT.decode(login(SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD).get("access_token"));

        assertThat(accessToken.getClaim("given_name").asString()).isEqualTo("Super");
        assertThat(accessToken.getClaim("email").asString()).isEqualTo(SUPER_ADMIN_EMAIL);
        assertThat(accessToken.getClaim("realm_access").asMap().get("roles")).asList().contains("SUPER_ADMIN");
        assertThat(accessToken.getClaim("tenant_id").isMissing()).isTrue();
        assertThat(accessToken.getClaim("tenant_name").isMissing()).isTrue();
    }

    @Test
    @DisplayName("A tenant update rewrites the tenant_name attribute, so the next refreshed token carries the new name")
    void tenantRenameIsReflectedInRefreshedToken() throws Exception {
        Map<String, String> tokens = login(EXISTING_UI_USER_EMAIL, EXISTING_UI_USER_PASSWORD);
        try {
            keycloakUserService.onTenantUpdated(new TenantUpdatedEvent(TENANT_ID, "Engineering Renamed"));

            String response = mockMvc.perform(post(REFRESH_PATH)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "refresh_token", tokens.get("refresh_token")))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Map<String, String> refreshed = objectMapper.readValue(response, Map.class);

            assertThat(refreshed.get("refresh_token")).isNotBlank();
            assertThat(JWT.decode(refreshed.get("access_token")).getClaim("tenant_name").asString())
                    .isEqualTo("Engineering Renamed");
        } finally {
            keycloakUserService.onTenantUpdated(new TenantUpdatedEvent(TENANT_ID, TENANT_NAME));
        }
    }

    private Map<String, String> login(String email, String password) throws Exception {
        String response = mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, Map.class);
    }

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
