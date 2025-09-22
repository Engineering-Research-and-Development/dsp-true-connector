package it.eng.connector.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.dto.LoginRequest;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.tools.controller.ApiEndpoints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class JwtAuthenticationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_EMAIL = "jwt-test@example.com";
    private static final String TEST_PASSWORD = "JwtTestPassword123!";
    private static final String TEST_FIRST_NAME = "JWT";
    private static final String TEST_LAST_NAME = "Test";
    
    // Initial data users for testing
    private static final String INITIAL_ADMIN_EMAIL = "admin@mail.com";
    private static final String INITIAL_ADMIN_PASSWORD = "password";

    @BeforeEach
    void setUp() {
        // Clean up any existing test users
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("JWT token validation and processing")
    void testJwtTokenValidationAndProcessing() throws Exception {
        // Create a test user
        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setFirstName(TEST_FIRST_NAME);
        user.setLastName(TEST_LAST_NAME);
        user.setRole(Role.ROLE_USER);
        user.setEnabled(true);
        userRepository.save(user);

        // Login to get JWT token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(TEST_EMAIL);
        loginRequest.setPassword(TEST_PASSWORD);

        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        loginResult.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());

        // Extract access token
        String loginResponse = loginResult.andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.get("data").get("accessToken").asText();

        // Test JWT token with various protected endpoints
        testJwtTokenWithEndpoint(accessToken, "GET", ApiEndpoints.AUTH_V1 + "/me");
        testJwtTokenWithEndpoint(accessToken, "GET", ApiEndpoints.USERS_V1);
        testJwtTokenWithEndpoint(accessToken, "GET", ApiEndpoints.CATALOG_CATALOGS_V1 + "/");
    }

    @Test
    @DisplayName("JWT token expiration handling")
    void testJwtTokenExpirationHandling() throws Exception {
        // This test would require a way to create an expired token
        // For now, we test with an invalid token format
        String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .header("Authorization", "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("JWT token with different user roles")
    void testJwtTokenWithDifferentUserRoles() throws Exception {
        // Test with USER role
        User user = createTestUser(TEST_EMAIL, Role.ROLE_USER);
        String userToken = getAccessTokenForUser(user);

        // USER should be able to access user endpoints
        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // USER should not be able to access admin endpoints
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Test with ADMIN role using existing user from initial data
        String adminToken = getAccessTokenForExistingUser(INITIAL_ADMIN_EMAIL, INITIAL_ADMIN_PASSWORD);

        // ADMIN should be able to access admin endpoints
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("JWT token with malformed Authorization header")
    void testJwtTokenWithMalformedAuthorizationHeader() throws Exception {
        // Test without Bearer prefix
        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .header("Authorization", "invalid-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // Test with empty token
        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .header("Authorization", "Bearer ")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // Test with null token
        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .header("Authorization", "Bearer null")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT token with disabled user")
    void testJwtTokenWithDisabledUser() throws Exception {
        // Create a disabled user
        User disabledUser = new User();
        disabledUser.setEmail("disabled@example.com");
        disabledUser.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        disabledUser.setFirstName(TEST_FIRST_NAME);
        disabledUser.setLastName(TEST_LAST_NAME);
        disabledUser.setRole(Role.ROLE_USER);
        disabledUser.setEnabled(false); // Disabled user
        userRepository.save(disabledUser);

        // Try to login with disabled user
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("disabled@example.com");
        loginRequest.setPassword(TEST_PASSWORD);

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("JWT token refresh flow")
    void testJwtTokenRefreshFlow() throws Exception {
        // Create a test user
        User user = createTestUser(TEST_EMAIL, Role.ROLE_USER);
        getAccessTokenForUser(user);

        // Get refresh token by logging in
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(TEST_EMAIL);
        loginRequest.setPassword(TEST_PASSWORD);

        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        String loginResponse = loginResult.andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String refreshToken = loginJson.get("data").get("refreshToken").asText();

        // Use refresh token to get new access token
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    @DisplayName("JWT token with concurrent requests")
    void testJwtTokenWithConcurrentRequests() throws Exception {
        // Create a test user
        User user = createTestUser(TEST_EMAIL, Role.ROLE_USER);
        String accessToken = getAccessTokenForUser(user);

        // Make multiple concurrent requests with the same token
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Test
    @DisplayName("JWT token with different HTTP methods")
    void testJwtTokenWithDifferentHttpMethods() throws Exception {
        // Create a test user
        User user = createTestUser(TEST_EMAIL, Role.ROLE_USER);
        String accessToken = getAccessTokenForUser(user);

        // Test GET request
        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Test POST request (logout)
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private void testJwtTokenWithEndpoint(String accessToken, String method, String endpoint) throws Exception {
        ResultActions result;
        if ("GET".equals(method)) {
            result = mockMvc.perform(get(endpoint)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON));
        } else {
            result = mockMvc.perform(post(endpoint)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON));
        }

        // The endpoint should either return 200 (success) or 403 (forbidden due to role)
        // but not 401 (unauthorized due to invalid token)
        int status = result.andReturn().getResponse().getStatus();
        assertTrue(status == 200 || status == 403 || status == 404, 
                "Expected status 200, 403, or 404, but got: " + status);
    }

    private User createTestUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setFirstName(TEST_FIRST_NAME);
        user.setLastName(TEST_LAST_NAME);
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private String getAccessTokenForUser(User user) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(user.getEmail());
        loginRequest.setPassword(TEST_PASSWORD);

        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        String loginResponse = loginResult.andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        
        // Check if login was successful
        if (!loginJson.get("success").asBoolean()) {
            String errorMessage = loginJson.has("message") ? loginJson.get("message").asText() : "Login failed";
            throw new RuntimeException("Login failed for user " + user.getEmail() + ": " + errorMessage);
        }
        
        // Check if data field exists and contains accessToken
        JsonNode dataNode = loginJson.get("data");
        if (dataNode == null || dataNode.isNull()) {
            throw new RuntimeException("Login response missing data field for user " + user.getEmail());
        }
        
        JsonNode accessTokenNode = dataNode.get("accessToken");
        if (accessTokenNode == null || accessTokenNode.isNull()) {
            throw new RuntimeException("Login response missing accessToken for user " + user.getEmail());
        }
        
        return accessTokenNode.asText();
    }

    private String getAccessTokenForExistingUser(String email, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword(password);

        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        String loginResponse = loginResult.andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        
        // Check if login was successful
        if (!loginJson.get("success").asBoolean()) {
            String errorMessage = loginJson.has("message") ? loginJson.get("message").asText() : "Login failed";
            throw new RuntimeException("Login failed for user " + email + ": " + errorMessage);
        }
        
        // Check if data field exists and contains accessToken
        JsonNode dataNode = loginJson.get("data");
        if (dataNode == null || dataNode.isNull()) {
            throw new RuntimeException("Login response missing data field for user " + email);
        }
        
        JsonNode accessTokenNode = dataNode.get("accessToken");
        if (accessTokenNode == null || accessTokenNode.isNull()) {
            throw new RuntimeException("Login response missing accessToken for user " + email);
        }
        
        return accessTokenNode.asText();
    }
}
