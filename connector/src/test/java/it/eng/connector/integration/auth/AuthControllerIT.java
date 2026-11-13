package it.eng.connector.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for {@code /api/v1/auth/**} against the real Spring context and security
 * filter chain, guarding against a regression where an authentication failure raised from
 * <em>within</em> {@code AuthController} (as opposed to a filter-chain-level rejection for a
 * missing/invalid credential) could be routed to the wrong, unscoped
 * {@code DataspaceProtocolEndpointsExceptionHandler} instead of the dedicated
 * {@code AuthExceptionAdvice}, crashing with
 * {@code IllegalStateException: getInputStream() has already been called for this request}
 * instead of returning a clean {@code 401}.
 *
 * <p>The login error tests authenticate with {@code user(...).roles(ADMIN)} so they exercise
 * {@code AuthController}'s own logic. The flow and audit tests call the routes without
 * credentials, as the UI does in INTERNAL mode, so they also check that {@code /api/v1/auth/**}
 * stays {@code permitAll()}.
 */
class AuthControllerIT extends BaseIntegrationTest {

    private static final String LOGIN_PATH = ApiEndpoints.AUTH_V1 + "/login";
    private static final String REFRESH_PATH = ApiEndpoints.AUTH_V1 + "/refresh";
    private static final String LOGOUT_PATH = ApiEndpoints.AUTH_V1 + "/logout";
    private static final long AUDIT_WAIT_MILLIS = 5_000;
    private static final String EXISTING_USER_EMAIL = "admin@mail.com";
    private static final String EXISTING_USER_PASSWORD = "password";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("Login with valid credentials returns 200 with an access token")
    void loginWithValidCredentialsReturns200() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .with(user("probe").roles(Role.ADMIN.name()))
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
                        .with(user("probe").roles(Role.ADMIN.name()))
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
                        .with(user("probe").roles(Role.ADMIN.name()))
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

    @Test
    @DisplayName("Login, refresh and logout work in sequence; rotated and revoked refresh tokens are rejected")
    void loginRefreshLogoutFlow() throws Exception {
        long loginEvents = countEvents(AuditEventType.APPLICATION_LOGIN, EXISTING_USER_EMAIL);
        long refreshEvents = countEvents(AuditEventType.APPLICATION_TOKEN_REFRESHED, EXISTING_USER_EMAIL);
        long logoutEvents = countEvents(AuditEventType.APPLICATION_LOGOUT, EXISTING_USER_EMAIL);

        JsonNode login = postJson(LOGIN_PATH, Map.of(
                "email", EXISTING_USER_EMAIL,
                "password", EXISTING_USER_PASSWORD), 200);
        String firstRefreshToken = login.get("refresh_token").asText();

        JsonNode refreshed = postJson(REFRESH_PATH, Map.of("refresh_token", firstRefreshToken), 200);
        String secondRefreshToken = refreshed.get("refresh_token").asText();
        assertThat(refreshed.get("access_token").asText()).isNotBlank();
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        postJson(REFRESH_PATH, Map.of("refresh_token", firstRefreshToken), 401);

        postJson(LOGOUT_PATH, Map.of("refresh_token", secondRefreshToken), 200);
        postJson(REFRESH_PATH, Map.of("refresh_token", secondRefreshToken), 401);

        awaitEventCount(AuditEventType.APPLICATION_LOGIN, EXISTING_USER_EMAIL, loginEvents + 1);
        awaitEventCount(AuditEventType.APPLICATION_TOKEN_REFRESHED, EXISTING_USER_EMAIL, refreshEvents + 1);
        awaitEventCount(AuditEventType.APPLICATION_LOGOUT, EXISTING_USER_EMAIL, logoutEvents + 1);
    }

    @Test
    @DisplayName("A failed login publishes an APPLICATION_LOGIN_FAILED audit event")
    void failedLoginPublishesAuditEvent() throws Exception {
        // A unique email keeps failed logins from other tests out of the count.
        String email = "audit-probe-" + UUID.randomUUID() + "@mail.com";

        postJson(LOGIN_PATH, Map.of(
                "email", email,
                "password", "irrelevant"), 401);

        awaitEventCount(AuditEventType.APPLICATION_LOGIN_FAILED, email, 1);
        assertThat(countEvents(AuditEventType.APPLICATION_LOGIN_FAILED, email)).isEqualTo(1);
    }

    private JsonNode postJson(String path, Map<String, String> body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? objectMapper.createObjectNode() : objectMapper.readTree(response);
    }

    private long countEvents(AuditEventType type, String username) {
        return mongoTemplate.count(Query.query(Criteria.where("eventType").is(type).and("username").is(username)),
                AuditEvent.class);
    }

    // Audit events are persisted by an @Async listener, so give it a moment to catch up.
    private void awaitEventCount(AuditEventType type, String username, long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AUDIT_WAIT_MILLIS;
        while (countEvents(type, username) < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(countEvents(type, username)).isGreaterThanOrEqualTo(expected);
    }
}
