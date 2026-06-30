package it.eng.connector.integration.user;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.model.UserDTO;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.serializer.ToolsSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UserIT extends BaseIntegrationTest {

    // Known test-specific email addresses used across all tests in this class.
    // Only these are deleted in @AfterEach; deleteAll() is intentionally avoided to
    // preserve seed users (admin, connector user) that the application requires.
    private static final List<String> TEST_USER_EMAILS = List.of(
            "test@mail.com",
            "email_test@mail.com",
            "otherUser@mail.com",
            "otherUser1@mail.com",
            "otherUser3@mail.com",
            "otherUser4@mail.com",
            "tenant.user@mail.com",
            "superadmin.user@mail.com",
            TestUtil.SUPER_ADMIN_USER
    );

    private static final String KNOWN_TENANT_ID = "engineering";
    private static final String UNKNOWN_TENANT_ID = "non-existent-tenant-xyz";

    // updateUser test creates a duplicate entry under the super-admin email; track by ID for safe cleanup.
    private String savedTestSuperAdminDuplicateId;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    public void cleanup() {
        TEST_USER_EMAILS.forEach(email ->
                userRepository.findByEmail(email).ifPresent(userRepository::delete));
        if (savedTestSuperAdminDuplicateId != null) {
            userRepository.deleteById(savedTestSuperAdminDuplicateId);
            savedTestSuperAdminDuplicateId = null;
        }
    }

    @Test
    public void getUsers() throws Exception {

        ResultActions result = mockMvc.perform(get(ApiEndpoints.USERS_V1)
                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        // TODO when user serialization is fixed, check if user is there

        result = mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/" + TestUtil.ADMIN_USER)
                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        result = mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/" + "not_found@user.com")
                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

    }

    @Test
    @DisplayName("GET /api/v1/users as ROLE_ADMIN returns 403")
    @WithUserDetails(TestUtil.ADMIN_USER)
    public void getUsers_asAdmin_returns403() throws Exception {
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/{email} as ROLE_ADMIN returns 403")
    @WithUserDetails(TestUtil.ADMIN_USER)
    public void getUserByEmail_asAdmin_returns403() throws Exception {
        mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/" + TestUtil.ADMIN_USER)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    public void createUser() throws Exception {
        UserDTO userDTO = new UserDTO("firstName", "lastName", "test@mail.com", "StrongPassword1!", null, Role.ROLE_ADMIN, null);

        final ResultActions result = mockMvc.perform(post(ApiEndpoints.USERS_V1)
                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                .content(ToolsSerializer.serializePlain(userDTO))
                .contentType(MediaType.APPLICATION_JSON));

        // verify expected behavior
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
		/* TODO check how to deserialize User and GrantedAuthority 
		 * Cannot construct instance of `org.springframework.security.core.GrantedAuthority` (no Creators, like default constructor, exist)
		String json = result.andReturn().getResponse().getContentAsString();
		System.out.println(json);
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, User.class);
		GenericApiResponse<User> genericApiResponse = jsonMapper.readValue(json, javaType);
		assertNotNull(genericApiResponse);
		assertTrue(genericApiResponse.isSuccess());
		assertNotNull(genericApiResponse.getData());
		*/
    }

    @Test
    public void createUser_weak_password() throws Exception {
        UserDTO userDTO = new UserDTO("firstName", "lastName", "test@mail.com", "pass", null, Role.ROLE_ADMIN, null);

        final ResultActions result = mockMvc.perform(post(ApiEndpoints.USERS_V1)
                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                .content(ToolsSerializer.serializePlain(userDTO))
                .contentType(MediaType.APPLICATION_JSON));

        // verify expected behavior
        result.andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }


    @Test
    public void createUser_already_exists() throws Exception {
        User userObj = new User(createNewId(), "FirstNameTest", "LastNameTest", "email_test@mail.com", "password",
                true, false, false, Role.ROLE_ADMIN);
        userRepository.save(userObj);

        UserDTO userDTO = new UserDTO("FirstNameTest", "LastNameTest", "email_test@mail.com", "StrongPassword123!", null, Role.ROLE_ADMIN, null);

        final ResultActions result = mockMvc.perform(post(ApiEndpoints.USERS_V1)
                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                .content(ToolsSerializer.serializePlain(userDTO))
                .contentType(MediaType.APPLICATION_JSON));

        // verify expected behavior
        result.andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    public void updateUser() throws Exception {
        // Create the user with SUPER_ADMIN email so the service email-ownership check passes.
        User userObj = new User(createNewId(), "FirstNameTest", "LastNameTest", TestUtil.SUPER_ADMIN_USER, "password",
                true, false, false, Role.ROLE_SUPER_ADMIN);
        userRepository.save(userObj);
        // Track ID for @AfterEach cleanup; can't delete by email safely as the seed may share it.
        savedTestSuperAdminDuplicateId = userObj.getId();

        UserDTO userDTO = new UserDTO("FirstNameTestUpdate", "LastNameTestUpdate", null, null, null, Role.ROLE_SUPER_ADMIN, null);

        final ResultActions result = mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + userObj.getId() + "/update")
                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                .content(ToolsSerializer.serializePlain(userDTO))
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        User userUpdated = userRepository.findById(userObj.getId()).get();
        assertEquals(userUpdated.getFirstName(), "FirstNameTestUpdate");
        assertEquals(userUpdated.getLastName(), "LastNameTestUpdate");
    }

    @Test
    public void updateUser_other_user() throws Exception {
        User userObj = new User(createNewId(), "FirstNameTest", "LastNameTest", "otherUser@mail.com", "password",
                true, false, false, Role.ROLE_ADMIN);
        userRepository.save(userObj);

        UserDTO userDTO = new UserDTO("FirstNameTestUpdate", "LastNameTestUpdate", null, null, null, Role.ROLE_ADMIN, null);

        final ResultActions result = mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + userObj.getId() + "/update")
                .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                .content(ToolsSerializer.serializePlain(userDTO))
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        // did not update original values
        User userUpdated = userRepository.findById(userObj.getId()).get();
        assertEquals(userUpdated.getFirstName(), "FirstNameTest");
        assertEquals(userUpdated.getLastName(), "LastNameTest");
    }

    @Test
    public void updatePassword() throws Exception {
        User userObj = new User(createNewId(), "FirstNameTest", "LastNameTest", "otherUser1@mail.com",
                passwordEncoder.encode("password"), true, false, false, Role.ROLE_SUPER_ADMIN);
        userRepository.save(userObj);

        UserDTO userDTO = new UserDTO("FirstNameTestUpdate", "LastNameTestUpdate", null, "password", "NewUpdPass123!", Role.ROLE_SUPER_ADMIN, null);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("otherUser1@mail.com", "password");

        final ResultActions result = mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + userObj.getId() + "/password")
                .headers(headers)
                .content(ToolsSerializer.serializePlain(userDTO))
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().is2xxSuccessful())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    public void updatePassword_weak() throws Exception {
        User userObj = new User(createNewId(), "FirstNameTest", "LastNameTest", "otherUser3@mail.com",
                passwordEncoder.encode("password"), true, false, false, Role.ROLE_SUPER_ADMIN);
        userRepository.save(userObj);

        UserDTO userDTO = new UserDTO("FirstNameTestUpdate", "LastNameTestUpdate", null, "password", "weak123!", Role.ROLE_SUPER_ADMIN, null);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("otherUser3@mail.com", "password");

        final ResultActions result = mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + userObj.getId() + "/password")
                .headers(headers)
                .content(ToolsSerializer.serializePlain(userDTO))
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("POST /api/v1/users with valid tenantId links user to tenant and returns 200")
    public void createUser_withValidTenantId_returns200() throws Exception {
        UserDTO userDTO = new UserDTO("First", "Last", "tenant.user@mail.com", "StrongPassword1!", null,
                Role.ROLE_ADMIN, KNOWN_TENANT_ID);

        mockMvc.perform(post(ApiEndpoints.USERS_V1)
                        .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                        .content(ToolsSerializer.serializePlain(userDTO))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("POST /api/v1/users with non-existent tenantId returns 4xx")
    public void createUser_withNonExistentTenantId_returns4xx() throws Exception {
        UserDTO userDTO = new UserDTO("First", "Last", "tenant.user@mail.com", "StrongPassword1!", null,
                Role.ROLE_ADMIN, UNKNOWN_TENANT_ID);

        mockMvc.perform(post(ApiEndpoints.USERS_V1)
                        .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                        .content(ToolsSerializer.serializePlain(userDTO))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("POST /api/v1/users for SUPER_ADMIN without tenantId returns 200")
    public void createUser_superAdminWithoutTenantId_returns200() throws Exception {
        UserDTO userDTO = new UserDTO("SuperFirst", "SuperLast", "superadmin.user@mail.com", "StrongPassword1!", null,
                Role.ROLE_SUPER_ADMIN, null);

        mockMvc.perform(post(ApiEndpoints.USERS_V1)
                        .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                        .content(ToolsSerializer.serializePlain(userDTO))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("POST /api/v1/users as ROLE_ADMIN returns 403")
    @WithUserDetails(TestUtil.ADMIN_USER)
    public void createUser_asAdmin_returns403() throws Exception {
        UserDTO userDTO = new UserDTO("firstName", "lastName", "test@mail.com", "StrongPassword1!", null, Role.ROLE_ADMIN, null);

        mockMvc.perform(post(ApiEndpoints.USERS_V1)
                        .content(ToolsSerializer.serializePlain(userDTO))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
