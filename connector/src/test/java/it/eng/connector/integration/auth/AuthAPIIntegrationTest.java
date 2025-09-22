package it.eng.connector.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.dto.*;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthAPIIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clean up any existing test users
        cleanupAllTestUsers();
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Register a new user")
    void testRegisterUser() throws Exception {
        // 1. Register a new user
        RegisterRequest registerRequest = createRegisterRequest();

        ResultActions registerResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        registerResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.firstName").value(TEST_FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(TEST_LAST_NAME))
                .andExpect(jsonPath("$.data.role").value(Role.ROLE_USER.name()));

        // Extract tokens from registration response
        String registerResponse = registerResult.andReturn().getResponse().getContentAsString();
        JsonNode registerJson = objectMapper.readTree(registerResponse);
        String accessToken = registerJson.get("data").get("accessToken").asText();
        String refreshToken = registerJson.get("data").get("refreshToken").asText();

        // 2. Get current user profile using access token
        ResultActions profileResult = mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON));

        profileResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.firstName").value(TEST_FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(TEST_LAST_NAME))
                .andExpect(jsonPath("$.data.role").value(Role.ROLE_USER.name()));

        // 3. Refresh token
        RefreshTokenRequest refreshRequest = createRefreshTokenRequest(refreshToken);

        ResultActions refreshResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)));

        refreshResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());

        // Extract new access token
        String refreshResponse = refreshResult.andReturn().getResponse().getContentAsString();
        JsonNode refreshJson = objectMapper.readTree(refreshResponse);
        String newAccessToken = refreshJson.get("data").get("accessToken").asText();

        // 4. Verify new token works
        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .header("Authorization", "Bearer " + newAccessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 5. Logout
        ResultActions logoutResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/logout")
                .header("Authorization", "Bearer " + newAccessToken)
                .contentType(MediaType.APPLICATION_JSON));

        logoutResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("success"));

        // 6. Verify user was created in database
        Optional<User> savedUser = userRepository.findByEmail(TEST_EMAIL);
        assertTrue(savedUser.isPresent());
        assertEquals(TEST_EMAIL, savedUser.get().getEmail());
        assertEquals(TEST_FIRST_NAME, savedUser.get().getFirstName());
        assertEquals(TEST_LAST_NAME, savedUser.get().getLastName());
        assertEquals(Role.ROLE_USER, savedUser.get().getRole());
        assertTrue(savedUser.get().isEnabled());
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Login with registered user")
    void testLoginWithRegisteredUser() throws Exception {
        // First create a user
        createAndSaveTestUser(Role.ROLE_USER);

        // Login with the user
        LoginRequest loginRequest = createLoginRequest();

        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        loginResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.firstName").value("Mock"))
                .andExpect(jsonPath("$.data.lastName").value("User"));
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Refresh token")
    void testRefreshToken() throws Exception {
        // First create a user
        createAndSaveTestUser(Role.ROLE_USER);
        
        // Login to get tokens
        LoginRequest loginRequest = createLoginRequest();
        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        String loginResponse = loginResult.andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String refreshToken = loginJson.get("data").get("refreshToken").asText();

        // Refresh token
        RefreshTokenRequest refreshRequest = createRefreshTokenRequest(refreshToken);

        ResultActions refreshResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)));

        refreshResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.firstName").value("Mock"))
                .andExpect(jsonPath("$.data.lastName").value("User"));
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Get current user profile")
    void testGetCurrentUser() throws Exception {
        // First create a user
        createAndSaveTestUser(Role.ROLE_USER);
        
        // Login to get access token
        LoginRequest loginRequest = createLoginRequest();
        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        String loginResponse = loginResult.andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.get("data").get("accessToken").asText();

        // Get current user profile
        ResultActions profileResult = mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON));

        profileResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.firstName").value("Mock"))
                .andExpect(jsonPath("$.data.lastName").value("User"))
                .andExpect(jsonPath("$.data.role").value(Role.ROLE_USER.name()));
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: Logout")
    void testLogout() throws Exception {
        // First create a user
        createAndSaveTestUser(Role.ROLE_USER);
        
        // Login to get tokens
        LoginRequest loginRequest = createLoginRequest();
        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        String loginResponse = loginResult.andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.get("data").get("accessToken").asText();
        String refreshToken = loginJson.get("data").get("refreshToken").asText();

        // Logout
        LogoutRequest logoutRequest = createLogoutRequest(accessToken, refreshToken);

        ResultActions logoutResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest)));

        logoutResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("success"));
    }

    @Test
    @Order(6)
    @DisplayName("Error case: Login with invalid credentials")
    void testLoginWithInvalidCredentials() throws Exception {
        LoginRequest loginRequest = createLoginRequest(TEST_EMAIL, "WrongPassword");

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(7)
    @DisplayName("Error case: Register with duplicate email")
    void testRegisterWithDuplicateEmail() throws Exception {
        // First create a user
        createAndSaveTestUser(Role.ROLE_USER);

        // Try to register with same email
        RegisterRequest registerRequest = createRegisterRequest();

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(8)
    @DisplayName("Error case: Access protected endpoint without authentication")
    void testAccessProtectedEndpointWithoutAuth() throws Exception {
        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(9)
    @DisplayName("Error case: Access protected endpoint with invalid token")
    void testAccessProtectedEndpointWithInvalidToken() throws Exception {
        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/me")
                .header("Authorization", "Bearer invalid-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(10)
    @DisplayName("Error case: Logout without authentication")
    void testLogoutWithoutAuth() throws Exception {
        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/logout")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(11)
    @DisplayName("Error case: Refresh token with invalid refresh token")
    void testRefreshTokenWithInvalidToken() throws Exception {
        RefreshTokenRequest refreshRequest = createRefreshTokenRequest("invalid-refresh-token");

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(12)
    @DisplayName("Error case: Register with invalid request data")
    void testRegisterWithInvalidData() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        // Missing required fields

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("Error case: Login with invalid request data")
    void testLoginWithInvalidData() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        // Missing required fields

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(14)
    @DisplayName("Error case: Refresh token with invalid request data")
    void testRefreshTokenWithInvalidData() throws Exception {
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        // Missing required fields

        mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isBadRequest());
    }
}
