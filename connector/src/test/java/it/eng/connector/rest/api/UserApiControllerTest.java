package it.eng.connector.rest.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.connector.model.UserDTO;
import it.eng.connector.service.UserService;
import it.eng.connector.util.TestUtil;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;

@ExtendWith(MockitoExtension.class)
class UserApiControllerTest {

	private static final String USER = "user";
	private static final String USER_ID = "user_id";
	@Mock
	private UserService userService;
	@Mock
	private UserDTO userDTO;
	@Mock
	private Principal principal;
	
	@InjectMocks
	private UserApiController controller;
	
	@Test
	@DisplayName("Get users")
	void getUsers() {
		// find all
		when(userService.findUsers(isNull()))
			.thenReturn(Arrays.asList(ToolsSerializer.serializePlainJsonNode(TestUtil.USER)));
		GenericApiResponse<Collection<JsonNode>> response = controller.getUsers(null).getBody();
		assertNotNull(response);
		assertNotNull(response.getData());
		
		// found by email
		when(userService.findUsers(TestUtil.USER.getEmail()))
			.thenReturn(Arrays.asList(ToolsSerializer.serializePlainJsonNode(TestUtil.USER)));
		response = controller.getUsers(TestUtil.USER.getEmail()).getBody();
		assertNotNull(response);
		assertNotNull(response.getData());
		
		// not found by email
		when(userService.findUsers("not found"))
			.thenReturn(new ArrayList<JsonNode>());
		response = controller.getUsers("not found").getBody();
		assertNotNull(response);
		assertTrue(response.getData().isEmpty());
	}

	@Test
	@DisplayName("Create user")
	void createUser() {
		when(userService.createUser(userDTO)).thenReturn(ToolsSerializer.serializePlainJsonNode(TestUtil.USER));
		GenericApiResponse<JsonNode> response = controller.createUser(userDTO).getBody();
		assertNotNull(response);
		assertNotNull(response.getData());
	}
	
	@Test
	@DisplayName("Create user - service error")
	void createUser_error() {
		doThrow(BadRequestException.class).when(userService).createUser(userDTO);
		
		assertThrows(BadRequestException.class, ()-> controller.createUser(userDTO).getBody());
	}

	@Test
	@DisplayName("Update user")
	void updateUser() {
		when(principal.getName()).thenReturn(USER);
		when(userService.updateUser(USER_ID, USER, userDTO)).thenReturn(ToolsSerializer.serializePlainJsonNode(TestUtil.API_USER));
		GenericApiResponse<JsonNode> response =  controller.updateUser(USER_ID, userDTO, principal).getBody();
		assertNotNull(response);
		assertNotNull(response.getData());
	}

	@Test
	@DisplayName("Create user - service error")
	void updateUser_error() {
		when(principal.getName()).thenReturn(USER);
		doThrow(BadRequestException.class).when(userService).updateUser(USER_ID, USER, userDTO);
		assertThrows(BadRequestException.class, ()-> controller.updateUser(USER_ID, userDTO, principal));
	}
	
	@Test
	void updatePassword() {
		when(principal.getName()).thenReturn(USER);
		when(userService.updatePassword(USER_ID, USER, userDTO)).thenReturn(ToolsSerializer.serializePlainJsonNode(TestUtil.API_USER));
		GenericApiResponse<JsonNode> response =  controller.updatePassword(USER_ID, userDTO, principal).getBody();
		assertNotNull(response);
		assertNotNull(response.getData());
	}
	
	@Test
	@DisplayName("Update password - service error")
	void updatePassword_error() {
		when(principal.getName()).thenReturn(USER);
		doThrow(BadRequestException.class).when(userService).updatePassword(USER_ID, USER, userDTO);
		assertThrows(BadRequestException.class, ()-> controller.updatePassword(USER_ID, userDTO, principal));
	}
}
