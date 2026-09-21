package it.eng.connector.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.model.UserCreateRequest;
import it.eng.connector.model.UserNamesUpdateRequest;
import it.eng.connector.model.UserPasswordUpdateRequest;
import it.eng.connector.service.UserService;
import it.eng.connector.util.TestUtil;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAPIControllerTest {

	private static final String USER = "user";
	private static final String USER_ID = "user_id";
	@Mock
	private UserService userService;
	@Mock
	private Principal principal;
	
	@InjectMocks
	private UserAPIController controller;

	private static UserCreateRequest createRequest() {
		return UserCreateRequest.Builder.newInstance()
				.firstName("First")
				.lastName("Last")
				.email("user@mail.com")
				.password("StrongPassword1!")
				.build();
	}

	private static UserNamesUpdateRequest namesRequest() {
		return UserNamesUpdateRequest.Builder.newInstance()
				.firstName("First")
				.lastName("Last")
				.build();
	}

	private static UserPasswordUpdateRequest passwordRequest() {
		return UserPasswordUpdateRequest.Builder.newInstance()
				.password("oldPassword")
				.newPassword("newPassword")
				.build();
	}

	@Test
	@DisplayName("Create user")
	void createUser() {
		UserCreateRequest request = createRequest();
		when(userService.createUser(request)).thenReturn(ToolsSerializer.serializePlainJsonNode(TestUtil.USER));
		GenericApiResponse<JsonNode> response = controller.createUser(request).getBody();
		assertNotNull(response);
		assertNotNull(response.getData());
	}
	
	@Test
	@DisplayName("Create user - service error")
	void createUser_error() {
		UserCreateRequest request = createRequest();
		doThrow(BadRequestException.class).when(userService).createUser(request);
		
		assertThrows(BadRequestException.class, ()-> controller.createUser(request).getBody());
	}

	@Test
	@DisplayName("Update user")
	void updateUserNames() {
		UserNamesUpdateRequest request = namesRequest();
		when(principal.getName()).thenReturn(USER);
		when(userService.updateUserNames(USER_ID, USER, request)).thenReturn(ToolsSerializer.serializePlainJsonNode(TestUtil.API_USER));
		GenericApiResponse<JsonNode> response =  controller.updateUserNames(USER_ID, request, principal).getBody();
		assertNotNull(response);
		assertNotNull(response.getData());
	}

	@Test
	@DisplayName("Update user - service error")
	void updateUser_Names_error() {
		UserNamesUpdateRequest request = namesRequest();
		when(principal.getName()).thenReturn(USER);
		doThrow(BadRequestException.class).when(userService).updateUserNames(USER_ID, USER, request);
		assertThrows(BadRequestException.class, ()-> controller.updateUserNames(USER_ID, request, principal));
	}
	
	@Test
	void updatePassword() {
		UserPasswordUpdateRequest request = passwordRequest();
		when(principal.getName()).thenReturn(USER);
		when(userService.updatePassword(USER_ID, USER, request)).thenReturn(ToolsSerializer.serializePlainJsonNode(TestUtil.API_USER));
		GenericApiResponse<JsonNode> response =  controller.updatePassword(USER_ID, request, principal).getBody();
		assertNotNull(response);
		assertNotNull(response.getData());
	}
	
	@Test
	@DisplayName("Update password - service error")
	void updatePassword_error() {
		UserPasswordUpdateRequest request = passwordRequest();
		when(principal.getName()).thenReturn(USER);
		doThrow(BadRequestException.class).when(userService).updatePassword(USER_ID, USER, request);
		assertThrows(BadRequestException.class, ()-> controller.updatePassword(USER_ID, request, principal));
	}
}
