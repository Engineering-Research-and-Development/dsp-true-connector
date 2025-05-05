package it.eng.connector.integration.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.model.UserDTO;
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
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void getUsers() throws Exception {
		
		ResultActions result = mockMvc.perform(get(ApiEndpoints.USERS_V1)
				.contentType(MediaType.APPLICATION_JSON));
		result.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		// TODO when user serialization is fixed, check if user is there
		
		result = mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/" + TestUtil.ADMIN_USER)
				.contentType(MediaType.APPLICATION_JSON));
		result.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		result = mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/" + "not_found@user.com")
				.contentType(MediaType.APPLICATION_JSON));
		result.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
	}
	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void createUser() throws Exception {
		UserDTO userDTO = new UserDTO("firstName", "lastName", "test@mail.com", "StrongPasswrd12!", null, Role.ROLE_ADMIN);
		
		final ResultActions result = mockMvc.perform(post(ApiEndpoints.USERS_V1)
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
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void createUser_weak_password() throws Exception {
		UserDTO userDTO = new UserDTO("firstName", "lastName", "test@mail.com", "pass", null, Role.ROLE_ADMIN);
		
		final ResultActions result = mockMvc.perform(post(ApiEndpoints.USERS_V1)
				.content(ToolsSerializer.serializePlain(userDTO))
				.contentType(MediaType.APPLICATION_JSON));
		
		// verify expected behavior
		result.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
	}
	
	
	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void createUser_already_exists() throws Exception {
		User user = new User(createNewId(), "FirstNameTest", "LastNameTest", "email_test@mail.com", "password", 
				true, false, false, Role.ROLE_ADMIN);
		userRepository.save(user);
		
		UserDTO userDTO = new UserDTO("FirstNameTest", "LastNameTest", "email_test@mail.com", "StrongPassword123!", null, Role.ROLE_ADMIN);
		
		final ResultActions result = mockMvc.perform(post(ApiEndpoints.USERS_V1)
				.content(ToolsSerializer.serializePlain(userDTO))
				.contentType(MediaType.APPLICATION_JSON));
		
		// verify expected behavior
		result.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		userRepository.delete(user);
	}
	
	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void updateUser() throws Exception {
		User user = new User(createNewId(), "FirstNameTest", "LastNameTest", TestUtil.ADMIN_USER, "password", 
				true, false, false,Role.ROLE_ADMIN);
		userRepository.save(user);
		
		UserDTO userDTO = new UserDTO("FirstNameTestUpdate", "LastNameTestUpdate", null, null, null, Role.ROLE_ADMIN);
		
		final ResultActions result = mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + user.getId() + "/update")
				.content(ToolsSerializer.serializePlain(userDTO))
				.contentType(MediaType.APPLICATION_JSON));
	
		result.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		User userUpdated = userRepository.findById(user.getId()).get();
		assertEquals(userUpdated.getFirstName(), "FirstNameTestUpdate");
		assertEquals(userUpdated.getLastName(), "LastNameTestUpdate");
		
		userRepository.delete(userUpdated);
	}
	
	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void updateUser_other_user() throws Exception {
		User user = new User(createNewId(), "FirstNameTest", "LastNameTest", "otherUser@mail.com", "password", 
				true, false, false, Role.ROLE_ADMIN);
		userRepository.save(user);
		
		UserDTO userDTO = new UserDTO("FirstNameTestUpdate", "LastNameTestUpdate", null, null, null, Role.ROLE_ADMIN);
		
		final ResultActions result = mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + user.getId() + "/update")
				.content(ToolsSerializer.serializePlain(userDTO))
				.contentType(MediaType.APPLICATION_JSON));
	
		result.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		// did not update original values
		User userUpdated = userRepository.findById(user.getId()).get();
		assertEquals(userUpdated.getFirstName(), "FirstNameTest");
		assertEquals(userUpdated.getLastName(), "LastNameTest");
		
		userRepository.delete(user);
	}
	
	@Test
	public void updatePassword() throws Exception {
		User user = new User(createNewId(), "FirstNameTest", "LastNameTest", "otherUser1@mail.com", 
				passwordEncoder.encode("password"), true, false, false, Role.ROLE_ADMIN);
		userRepository.save(user);
		
		UserDTO userDTO = new UserDTO("FirstNameTestUpdate", "LastNameTestUpdate", null, "password", "NewUpdPass123!", Role.ROLE_ADMIN);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("otherUser1@mail.com", "password");
		
		final ResultActions result = mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + user.getId() + "/password")
				.headers(headers)
				.content(ToolsSerializer.serializePlain(userDTO))
				.contentType(MediaType.APPLICATION_JSON));
	
		result.andExpect(status().is2xxSuccessful())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		userRepository.delete(user);
	}
	
	@Test
	public void updatePassword_weak() throws Exception {
		User user = new User(createNewId(), "FirstNameTest", "LastNameTest", "otherUser3@mail.com", 
				passwordEncoder.encode("password"), true, false, false, Role.ROLE_ADMIN);
		userRepository.save(user);
		
		UserDTO userDTO = new UserDTO("FirstNameTestUpdate", "LastNameTestUpdate", null, "password", "weak123!", Role.ROLE_ADMIN);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("otherUser3@mail.com", "password");
		
		final ResultActions result = mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + user.getId() + "/password")
				.headers(headers)
				.content(ToolsSerializer.serializePlain(userDTO))
				.contentType(MediaType.APPLICATION_JSON));
	
		result.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		userRepository.delete(user);
	}
	
	@Test
	public void updatePassword_othe_user() throws Exception {
		User user = new User(createNewId(), "FirstNameTest", "LastNameTest", "otherUser4@mail.com", 
				passwordEncoder.encode("password"), true, false, false, Role.ROLE_ADMIN);
		userRepository.save(user);
		
		Optional<User> u = userRepository.findByEmail(TestUtil.ADMIN_USER);
		
		UserDTO userDTO = new UserDTO("FirstNameTestUpdate", "LastNameTestUpdate", null, "password", "NewUpdatedPassword123!", Role.ROLE_ADMIN);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("otherUser4@mail.com", "password");
		
		// updating password for TestUtil.ADMIN_USER while sending request with otherUser@mail.com
		final ResultActions result = mockMvc.perform(put(ApiEndpoints.USERS_V1 + "/" + u.get().getId() + "/password")
				.headers(headers)
				.content(ToolsSerializer.serializePlain(userDTO))
				.contentType(MediaType.APPLICATION_JSON));
	
		result.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		userRepository.delete(user);
	}
}
