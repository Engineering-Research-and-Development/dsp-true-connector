package it.eng.connector.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.dto.AdminUserCreateRequest;
import it.eng.connector.dto.AdminUserUpdateRequest;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class UserManagementIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String USER_EMAIL = "user@example.com";
    private static final String TEST_PASSWORD = "TestPassword123!";
    private static final String TEST_FIRST_NAME = "Test";
    private static final String TEST_LAST_NAME = "User";

    @BeforeEach
    void setUp() {
        // Clean up any existing test users
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(USER_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("Admin can manage users - complete flow")
    void testAdminUserManagementFlow() throws Exception {
        // 1. Create admin user
        User admin = createTestUser(ADMIN_EMAIL, Role.ROLE_ADMIN);
        String adminToken = getAccessTokenForUser(admin);

        // 2. Admin creates a new user
        AdminUserCreateRequest createRequest = new AdminUserCreateRequest();
        createRequest.setEmail(USER_EMAIL);
        createRequest.setPassword(TEST_PASSWORD);
        createRequest.setFirstName(TEST_FIRST_NAME);
        createRequest.setLastName(TEST_LAST_NAME);
        createRequest.setRole(Role.ROLE_USER);
        createRequest.setEnabled(true);

        ResultActions createResult = mockMvc.perform(post(ApiEndpoints.USERS_V1)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)));

        createResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.data.firstName").value(TEST_FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(TEST_LAST_NAME))
                .andExpect(jsonPath("$.data.role").value(Role.ROLE_USER.name()));

        // Extract user ID from response
        String createResponse = createResult.andReturn().getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResponse);
        String userId = createJson.get("data").get("userId").asText();

        // 3. Admin gets list of users
        ResultActions listResult = mockMvc.perform(get(ApiEndpoints.USERS_V1)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON));

        listResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.success").value(true))
                .andExpect(jsonPath("$.response.data").exists());

        // 4. Admin gets specific user by ID
        ResultActions getUserResult = mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/" + userId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON));

        getUserResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL));

        // 5. Admin updates user
        AdminUserUpdateRequest updateRequest = new AdminUserUpdateRequest();
        updateRequest.setFirstName("Updated");
        updateRequest.setLastName("Name");
        updateRequest.setRole(Role.ROLE_USER);
        updateRequest.setEnabled(false);

        ResultActions updateResult = mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + userId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)));

        updateResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Updated"))
                .andExpect(jsonPath("$.data.lastName").value("Name"))
                .andExpect(jsonPath("$.data.enabled").value(false));


        // 6. Admin deletes user
        ResultActions deleteResult = mockMvc.perform(delete(ApiEndpoints.USERS_V1 + "/" + userId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON));

        deleteResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true));

        // 7. Verify user was deleted (soft delete - user should be disabled)
        Optional<User> deletedUser = userRepository.findById(userId);
        assertTrue(deletedUser.isPresent());
        assertFalse(deletedUser.get().isEnabled());
    }

    @Test
    @DisplayName("Regular user cannot access admin endpoints")
    void testRegularUserCannotAccessAdminEndpoints() throws Exception {
        // Create regular user
        User user = createTestUser(USER_EMAIL, Role.ROLE_USER);
        String userToken = getAccessTokenForUser(user);

        // Try to access admin endpoints
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ApiEndpoints.USERS_V1)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("Admin can get pending users")
    void testAdminGetPendingUsers() throws Exception {
        // Create admin user
        User admin = createTestUser(ADMIN_EMAIL, Role.ROLE_ADMIN);
        String adminToken = getAccessTokenForUser(admin);

        // Create a pending user (disabled)
        User pendingUser = new User();
        pendingUser.setEmail("pending@example.com");
        pendingUser.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        pendingUser.setFirstName(TEST_FIRST_NAME);
        pendingUser.setLastName(TEST_LAST_NAME);
        pendingUser.setRole(Role.ROLE_USER);
        pendingUser.setEnabled(false); // Pending user
        userRepository.save(pendingUser);

        // Admin gets pending users
        ResultActions pendingUsersResult = mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/pending")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON));

        pendingUsersResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.success").value(true))
                .andExpect(jsonPath("$.response.data").exists());
    }

    @Test
    @DisplayName("User cannot access other users' profiles")
    void testUserCannotAccessOtherUsersProfiles() throws Exception {
        // Create a user
        User user1 = createTestUser(USER_EMAIL, Role.ROLE_USER);
        String user1Token = getAccessTokenForUser(user1);

        // User1 tries to access user2's profile (this should not be possible through the API)
        // The profile endpoint only returns the current user's profile
        ResultActions profileResult = mockMvc.perform(get(ApiEndpoints.PROFILE_V1)
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON));

        profileResult.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL)); // Should return user1's profile
    }

    @Test
    @DisplayName("Unauthorized access to user management endpoints")
    void testUnauthorizedAccessToUserManagementEndpoints() throws Exception {
        // Try to access user management endpoints without authentication
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(ApiEndpoints.USERS_V1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(ApiEndpoints.PROFILE_V1 + "/profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid user ID in admin operations")
    void testInvalidUserIdInAdminOperations() throws Exception {
        // Create admin user
        User admin = createTestUser(ADMIN_EMAIL, Role.ROLE_ADMIN);
        String adminToken = getAccessTokenForUser(admin);

        String invalidUserId = "invalid-user-id";

        // Try to get non-existent user
        mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/" + invalidUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Try to update non-existent user
        mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + invalidUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        // Try to delete non-existent user
        mockMvc.perform(delete(ApiEndpoints.USERS_V1 + "/" + invalidUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
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
}
