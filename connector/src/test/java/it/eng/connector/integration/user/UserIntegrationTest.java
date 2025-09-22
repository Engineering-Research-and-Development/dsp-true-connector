package it.eng.connector.integration.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.dto.AdminUserCreateRequest;
import it.eng.connector.dto.AdminUserUpdateRequest;
import it.eng.connector.dto.ChangePasswordRequest;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.serializer.ToolsSerializer;

public class UserIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void getUsers() throws Exception {

        ResultActions result = mockMvc.perform(authenticatedGet(ApiEndpoints.USERS_V1, TestUtil.ADMIN_USER)
                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        // TODO when user serialization is fixed, check if user is there

        // Get user by ID - fetch admin user ID first
        Optional<User> adminUser = userRepository.findByEmail(TestUtil.ADMIN_USER);
        if (adminUser.isPresent()) {
            result = mockMvc.perform(authenticatedGet(ApiEndpoints.USERS_V1 + "/" + adminUser.get().getId(), TestUtil.ADMIN_USER)
                    .contentType(MediaType.APPLICATION_JSON));
            result.andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        result = mockMvc.perform(authenticatedGet(ApiEndpoints.USERS_V1 + "/" + TestUtil.NON_EXISTENT_USER_ID, TestUtil.ADMIN_USER)
                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

    }
    @Test
    public void createUser() throws Exception {
        AdminUserCreateRequest createRequest = AdminUserCreateRequest.builder()
                .firstName("firstName")
                .lastName("lastName")
                .email("test@mail.com")
                .password("StrongPasswrd12!")
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .build();

        final ResultActions result = mockMvc.perform(authenticatedPost(ApiEndpoints.USERS_V1, TestUtil.ADMIN_USER)
                .content(ToolsSerializer.serializePlain(createRequest))
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
        AdminUserCreateRequest createRequest = AdminUserCreateRequest.builder()
                .firstName("firstName")
                .lastName("lastName")
                .email("test@mail.com")
                .password("pass")
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .build();

        final ResultActions result = mockMvc.perform(authenticatedPost(ApiEndpoints.USERS_V1, TestUtil.ADMIN_USER)
                .content(ToolsSerializer.serializePlain(createRequest))
                .contentType(MediaType.APPLICATION_JSON));

        // verify expected behavior
        result.andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }


    @Test
    public void createUser_already_exists() throws Exception {
        User user = User.builder()
                .id(createNewId())
                .firstName("FirstNameTest")
                .lastName("LastNameTest")
                .email("email_test@mail.com")
                .password("password")
                .enabled(true)
                .expired(false)
                .locked(false)
                .role(Role.ROLE_ADMIN)
                .build();
        userRepository.save(user);

        AdminUserCreateRequest createRequest = AdminUserCreateRequest.builder()
                .firstName("FirstNameTest")
                .lastName("LastNameTest")
                .email("email_test@mail.com")
                .password("StrongPassword123!")
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .build();

        final ResultActions result = mockMvc.perform(authenticatedPost(ApiEndpoints.USERS_V1, TestUtil.ADMIN_USER)
                .content(ToolsSerializer.serializePlain(createRequest))
                .contentType(MediaType.APPLICATION_JSON));

        // verify expected behavior
        result.andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        userRepository.delete(user);
    }

    @Test
    public void updateUser() throws Exception {
        User user = User.builder()
                .id(createNewId())
                .firstName("FirstNameTest")
                .lastName("LastNameTest")
                .email("updatetest@mail.com")
                .password("password")
                .enabled(true)
                .expired(false)
                .locked(false)
                .role(Role.ROLE_ADMIN)
                .build();
        userRepository.save(user);

        AdminUserUpdateRequest updateRequest = AdminUserUpdateRequest.builder()
                .firstName("FirstNameTestUpdate")
                .lastName("LastNameTestUpdate")
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .build();

        final ResultActions result = mockMvc.perform(authenticatedPut(ApiEndpoints.USERS_V1 + "/" + user.getId(), TestUtil.ADMIN_USER)
                .content(ToolsSerializer.serializePlain(updateRequest))
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        User userUpdated = userRepository.findById(user.getId()).get();
        assertEquals(userUpdated.getFirstName(), "FirstNameTestUpdate");
        assertEquals(userUpdated.getLastName(), "LastNameTestUpdate");

        userRepository.delete(userUpdated);
    }

    @Test
    public void updateUser_other_user() throws Exception {
        User user = User.builder()
                .id(createNewId())
                .firstName("FirstNameTest")
                .lastName("LastNameTest")
                .email("otherUser@mail.com")
                .password("password")
                .enabled(true)
                .expired(false)
                .locked(false)
                .role(Role.ROLE_ADMIN)
                .build();
        userRepository.save(user);

        AdminUserUpdateRequest updateRequest = AdminUserUpdateRequest.builder()
                .firstName("FirstNameTestUpdate")
                .lastName("LastNameTestUpdate")
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .build();

        final ResultActions result = mockMvc.perform(authenticatedPut(ApiEndpoints.USERS_V1 + "/" + user.getId(), TestUtil.ADMIN_USER)
                .content(ToolsSerializer.serializePlain(updateRequest))
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        // Should update since admin can update any user
        User userUpdated = userRepository.findById(user.getId()).get();
        assertEquals(userUpdated.getFirstName(), "FirstNameTestUpdate");
        assertEquals(userUpdated.getLastName(), "LastNameTestUpdate");

        userRepository.delete(user);
    }

    @Test
    public void updatePassword() throws Exception {
        User user = User.builder()
                .id(createNewId())
                .firstName("FirstNameTest")
                .lastName("LastNameTest")
                .email("otherUser1@mail.com")
                .password(passwordEncoder.encode("password"))
                .enabled(true)
                .expired(false)
                .locked(false)
                .role(Role.ROLE_ADMIN)
                .build();
        userRepository.save(user);

        ChangePasswordRequest changeRequest = ChangePasswordRequest.builder()
                .currentPassword("password")
                .newPassword("NewUpdPass123!")
                .build();

        final ResultActions result = mockMvc.perform(authenticatedPut(ApiEndpoints.PROFILE_V1 + "/password", "otherUser1@mail.com")
                .content(ToolsSerializer.serializePlain(changeRequest))
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().is2xxSuccessful())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        userRepository.delete(user);
    }

    @Test
    public void updatePassword_weak() throws Exception {
        User user = User.builder()
                .id(createNewId())
                .firstName("FirstNameTest")
                .lastName("LastNameTest")
                .email("otherUser3@mail.com")
                .password(passwordEncoder.encode("password"))
                .enabled(true)
                .expired(false)
                .locked(false)
                .role(Role.ROLE_ADMIN)
                .build();
        userRepository.save(user);

        ChangePasswordRequest changeRequest = ChangePasswordRequest.builder()
                .currentPassword("password")
                .newPassword("weak123!")
                .build();

        final ResultActions result = mockMvc.perform(authenticatedPut(ApiEndpoints.PROFILE_V1 + "/password", "otherUser3@mail.com")
                .content(ToolsSerializer.serializePlain(changeRequest))
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        userRepository.delete(user);
    }

}
