package it.eng.connector.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.dto.ChangePasswordRequest;
import it.eng.connector.dto.LoginRequest;
import it.eng.connector.dto.UpdateProfileRequest;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProfileAPIIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("Step 1: Update user profile")
    void testUpdateUserProfile() throws Exception {
        // First create a user for this test
        createAndSaveTestUser(Role.ROLE_USER);

        // Login to get access token
        LoginRequest loginRequest = createLoginRequest();
        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        String loginResponse = loginResult.andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.get("data").get("accessToken").asText();

        // Update profile
        UpdateProfileRequest updateRequest = TestUtil.createUpdateProfileRequest();
        updateRequest.setFirstName("UpdatedFirst");
        updateRequest.setLastName("UpdatedLast");
        updateRequest.setEmail("updated@example.com");

        ResultActions updateResult = mockMvc.perform(put(ApiEndpoints.PROFILE_V1)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)));

        updateResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("UpdatedFirst"))
                .andExpect(jsonPath("$.data.lastName").value("UpdatedLast"))
                .andExpect(jsonPath("$.data.email").value("updated@example.com"));
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Change user password")
    void testChangeUserPassword() throws Exception {
        // First create a user for this test
        createAndSaveTestUser(Role.ROLE_USER);

        // Login to get access token
        LoginRequest loginRequest = createLoginRequest();
        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        String loginResponse = loginResult.andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.get("data").get("accessToken").asText();

        // Change password
        ChangePasswordRequest changePasswordRequest = TestUtil.createChangePasswordRequest();
        changePasswordRequest.setCurrentPassword(TEST_PASSWORD);
        changePasswordRequest.setNewPassword("NewPassword123!");

        ResultActions changePasswordResult = mockMvc.perform(put(ApiEndpoints.PROFILE_V1 + "/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changePasswordRequest)));

        changePasswordResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("success"))
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Verify password change with new login")
    void testLoginWithNewPassword() throws Exception {
        // First create a user and change their password
        createAndSaveTestUser(Role.ROLE_USER);

        // Login to get access token
        LoginRequest loginRequest = createLoginRequest();
        ResultActions loginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        String loginResponse = loginResult.andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.get("data").get("accessToken").asText();

        // Change password
        ChangePasswordRequest changePasswordRequest = TestUtil.createChangePasswordRequest();
        changePasswordRequest.setCurrentPassword(TEST_PASSWORD);
        changePasswordRequest.setNewPassword("NewPassword123!");

        mockMvc.perform(put(ApiEndpoints.PROFILE_V1 + "/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordRequest)))
                .andExpect(status().isOk());

        // Now login with new password
        LoginRequest newLoginRequest = createLoginRequest(TEST_EMAIL, "NewPassword123!");

        ResultActions newLoginResult = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newLoginRequest)));

        newLoginResult.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.firstName").value("Mock"))
                .andExpect(jsonPath("$.data.lastName").value("User"));
    }

    @Test
    @Order(4)
    @DisplayName("Error case: Update profile without authentication")
    void testUpdateProfileWithoutAuth() throws Exception {
        UpdateProfileRequest updateRequest = TestUtil.createUpdateProfileRequest();

        mockMvc.perform(put(ApiEndpoints.PROFILE_V1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(5)
    @DisplayName("Error case: Change password without authentication")
    void testChangePasswordWithoutAuth() throws Exception {
        ChangePasswordRequest changePasswordRequest = TestUtil.createChangePasswordRequest();

        mockMvc.perform(put(ApiEndpoints.PROFILE_V1 + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(6)
    @DisplayName("Error case: Change password with wrong current password")
    void testChangePasswordWithWrongCurrentPassword() throws Exception {
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

        // Try to change password with wrong current password
        ChangePasswordRequest changePasswordRequest = TestUtil.createChangePasswordRequest();
        changePasswordRequest.setCurrentPassword("WrongPassword");
        changePasswordRequest.setNewPassword("NewPassword123!");

        mockMvc.perform(put(ApiEndpoints.PROFILE_V1 + "/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(7)
    @DisplayName("Error case: Update profile with invalid data")
    void testUpdateProfileWithInvalidData() throws Exception {
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

        // Try to update profile with invalid data
        UpdateProfileRequest updateRequest = new UpdateProfileRequest();
        updateRequest.setFirstName("A"); // Too short (min 2 characters)
        updateRequest.setLastName("B"); // Too short (min 2 characters)
        updateRequest.setEmail("invalid-email"); // Invalid email format

        mockMvc.perform(put(ApiEndpoints.PROFILE_V1)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }
}
