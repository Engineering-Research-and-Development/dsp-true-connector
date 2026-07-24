package it.eng.connector.integration.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.controller.ApiEndpoints;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying the unified INTERNAL authentication mode flow.
 * Covers full login -> protected call -> refresh token rotation -> logout -> revoked token rejection,
 * login failure scenarios, validation (400) flows, and internal machine-to-machine Basic Auth regression check.
 */
@Slf4j
public class InternalAuthFlowIT extends BaseIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanUpTestUsers() {
        deleteUserIfExists("lifecycle@test.com");
        deleteUserIfExists("failures@test.com");
        deleteUserIfExists("disabled@test.com");
        deleteUserIfExists("locked@test.com");
        deleteUserIfExists("connector@test.com");
        deleteUserIfExists("admin@test.com");
        contractNegotiationRepository.deleteAll();
    }

    private void deleteUserIfExists(String email) {
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("Full authentication lifecycle for API: login -> protected call -> refresh -> logout -> rejection of revoked token")
    void fullAuthLifecycleFlow_API() throws Exception {
        String email = "lifecycle@test.com";
        String password = "password123";
        createTestUserAPI(email, password, true, false);

        // 1. Full Login
        MvcResult loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseBody, Map.class);
        String accessToken1 = (String) responseMap.get("access_token");
        String refreshToken1 = (String) responseMap.get("refresh_token");

        // 2. Protected Call
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 3. Refresh (Rotation)
        MvcResult refreshResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        String refreshResponseBody = refreshResult.getResponse().getContentAsString();
        Map<?, ?> refreshResponseMap = objectMapper.readValue(refreshResponseBody, Map.class);
        String accessToken2 = (String) refreshResponseMap.get("access_token");
        String refreshToken2 = (String) refreshResponseMap.get("refresh_token");

        // 4. Protected Call
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 5. Logout
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken2))))
                .andExpect(status().isOk());

        // 6. Refresh-with-revoked-token rejection
        // Attempting to refresh with the rotated token (refreshToken1) should be rejected
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken1))))
                .andExpect(status().isUnauthorized());

        // Attempting to refresh with the logged-out token (refreshToken2) should also be rejected
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refresh_token", refreshToken2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login failure paths: bad password, disabled, and locked")
    void loginFailurePaths() throws Exception {
        String email = "failures@test.com";
        String password = "password123";

        createTestUserAPI(email, password, true, false);

        // A. Bad Password
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "wrongpassword"))))
                .andExpect(status().isUnauthorized());

        // B. Disabled User
        String disabledEmail = "disabled@test.com";
        createTestUserAPI(disabledEmail, password, false, false);

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", disabledEmail,
                                "password", password))))
                .andExpect(status().isUnauthorized());

        // C. Locked User
        String lockedEmail = "locked@test.com";
        createTestUserAPI(lockedEmail, password, true, true);

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", lockedEmail,
                                "password", password))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Validation failure paths: empty email, empty password, or empty refresh token")
    void validationFailurePaths() throws Exception {
        // Empty Email
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "",
                                "password", "password123"))))
                .andExpect(status().isBadRequest());

        // Empty Password
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "test@test.com",
                                "password", ""))))
                .andExpect(status().isBadRequest());

        // Empty Refresh Token
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Failing authorization: Using refresh token instead of access token")
    void failAuth_usingRefreshToken() throws Exception {
        String email = "lifecycle@test.com";
        String password = "password123";
        createTestUserAPI(email, password, true, false);

        // 1. Full Login
        MvcResult loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseBody, Map.class);
        String refreshToken1 = (String) responseMap.get("refresh_token");

        // 2. Protected Call
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

    }

    private void createTestUserAPI(String email, String password, boolean enabled, boolean locked) {
        User user = new User("urn:uuid:" + UUID.randomUUID(), "John", "Doe",
                email, passwordEncoder.encode(password),
                enabled, false, locked, Role.ADMIN, TENANT_ID);
        userRepository.save(user);
    }

    private void createTestUserProtocol(String email, String password, boolean enabled, boolean locked) {
        User user = new User("urn:uuid:" + UUID.randomUUID(), "John", "Doe",
                email, passwordEncoder.encode(password),
                enabled, false, locked, Role.CONNECTOR, TENANT_ID);
        userRepository.save(user);
    }
}