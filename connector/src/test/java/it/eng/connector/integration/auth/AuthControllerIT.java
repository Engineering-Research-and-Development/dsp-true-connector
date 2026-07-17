package it.eng.connector.integration.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.controller.ApiEndpoints;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage for {@code /api/v1/auth/**} against the real Spring context and security
 * filter chain, guarding against a regression where an authentication failure raised from
 * <em>within</em> {@code AuthController} (as opposed to a filter-chain-level rejection for a
 * missing/invalid credential) could be routed to the wrong, unscoped
 * {@code DataspaceProtocolEndpointsExceptionHandler} instead of the dedicated
 * {@code AuthExceptionAdvice}, crashing with
 * {@code IllegalStateException: getInputStream() has already been called for this request}
 * instead of returning a clean {@code 401}.
 */
class AuthControllerIT extends BaseIntegrationTest {

    private static final String LOGIN_PATH = ApiEndpoints.AUTH_V1 + "/login";
    private static final String EXISTING_USER_EMAIL = "connector@mail.com";
    private static final String EXISTING_USER_PASSWORD = "password";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Login with valid credentials returns 200 with an access token")
    void loginWithValidCredentialsReturns200() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", EXISTING_USER_EMAIL,
                                "password", EXISTING_USER_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    @DisplayName("Login for a non-existent email returns a clean 401 AuthErrorResponse, not a 500 crash")
    void loginWithNonExistentEmailReturns401() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "does-not-exist@mail.com",
                                "password", "irrelevant"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    @DisplayName("Login with a wrong password for an existing user returns a clean 401 AuthErrorResponse")
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", EXISTING_USER_EMAIL,
                                "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.success").doesNotExist());
    }
}
