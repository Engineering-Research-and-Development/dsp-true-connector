package it.eng.connector.rest.api;

import it.eng.connector.dto.AdminUserCreateRequest;
import it.eng.connector.dto.AdminUserUpdateRequest;
import it.eng.connector.dto.UserProfileResponse;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.service.UserService;
import it.eng.connector.util.TestUtil;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.rest.api.PagedAPIResponse;
import it.eng.tools.service.GenericFilterBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementAPIControllerTest {

    private static final String USER_ID = "user_id";

    @Mock
    private UserService userService;
    @Mock
    private GenericFilterBuilder filterBuilder;
    @Mock
    private PagedResourcesAssembler<User> pagedResourcesAssembler;
    @Mock
    private PlainUserAssembler plainUserAssembler;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserManagementAPIController controller;

    @Test
    @DisplayName("Get users")
    void getUsers() {
        // Setup mock data using TestUtil
        User mockUser = TestUtil.createUser(Role.ROLE_USER);
        Page<User> userPage = new PageImpl<>(List.of(mockUser));
        PagedModel<EntityModel<Object>> pagedModel = PagedModel.empty();

        // Setup request
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, Object> filters = new HashMap<>();

        // Mock filter builder and service
        when(filterBuilder.buildFromRequest(any())).thenReturn(filters);
        when(userService.findUsers(anyMap(), any(Pageable.class))).thenReturn(userPage);
        when(pagedResourcesAssembler.toModel(userPage, plainUserAssembler)).thenReturn(pagedModel);

        // Test get users
        PagedAPIResponse response = controller.getUsers(request, 0, 20, new String[]{"email", "asc"}).getBody();
        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertNotNull(response.getResponse().getData());

        // Test with custom pagination
        response = controller.getUsers(request, 1, 10, new String[]{"firstName", "desc"}).getBody();
        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertNotNull(response.getResponse().getData());
    }

    @Test
    @DisplayName("Create user")
    void createUser() {
        // Setup mock data using TestUtil
        AdminUserCreateRequest createRequest = TestUtil.createAdminUserCreateRequest();
        User mockUser = TestUtil.createUser(Role.ROLE_USER);

        when(userService.createUser(any(AdminUserCreateRequest.class))).thenReturn(mockUser);

        GenericApiResponse<UserProfileResponse> response = controller.createUser(createRequest).getBody();
        assertNotNull(response);
        assertNotNull(response.getData());
    }

    @Test
    @DisplayName("Create user - service error")
    void createUser_error() {
        AdminUserCreateRequest createRequest = TestUtil.createAdminUserCreateRequest();

        doThrow(BadRequestException.class).when(userService).createUser(any(AdminUserCreateRequest.class));

        assertThrows(BadRequestException.class, () -> controller.createUser(createRequest).getBody());
    }

    @Test
    @DisplayName("Update user")
    void updateUser() {
        // Setup mock data using TestUtil
        AdminUserUpdateRequest updateRequest = TestUtil.createAdminUserUpdateRequest();
        User mockUser = TestUtil.createUser(Role.ROLE_ADMIN);

        when(userService.updateUser(eq(USER_ID), any(AdminUserUpdateRequest.class))).thenReturn(mockUser);

        GenericApiResponse<UserProfileResponse> response = controller.updateUser(USER_ID, updateRequest).getBody();
        assertNotNull(response);
        assertNotNull(response.getData());
    }

    @Test
    @DisplayName("Update user - service error")
    void updateUser_error() {
        AdminUserUpdateRequest updateRequest = TestUtil.createAdminUserUpdateRequest();

        doThrow(BadRequestException.class).when(userService).updateUser(eq(USER_ID), any(AdminUserUpdateRequest.class));
        assertThrows(BadRequestException.class, () -> controller.updateUser(USER_ID, updateRequest));
    }

    @Test
    @DisplayName("Reset user password")
    void resetUserPassword() {
        String newPassword = "newPassword123";

        // Mock the service call
        when(userService.resetUserPassword(eq(USER_ID), eq(newPassword))).thenReturn(null);

        GenericApiResponse<String> response = controller.resetUserPassword(USER_ID, newPassword).getBody();
        assertNotNull(response);
        assertNotNull(response.getData());
    }

    @Test
    @DisplayName("Reset user password - service error")
    void resetUserPassword_error() {
        String newPassword = "newPassword123";

        doThrow(BadRequestException.class).when(userService).resetUserPassword(eq(USER_ID), eq(newPassword));
        assertThrows(BadRequestException.class, () -> controller.resetUserPassword(USER_ID, newPassword));
    }

    @Test
    @DisplayName("Get pending users - success")
    void getPendingUsers_success() {
        // Setup mock data using TestUtil
        User mockUser = TestUtil.createUser(Role.ROLE_USER);
        Page<User> userPage = new PageImpl<>(List.of(mockUser));
        PagedModel<EntityModel<Object>> pagedModel = PagedModel.empty();

        when(userService.getPendingUsers(any(Pageable.class))).thenReturn(userPage);
        when(pagedResourcesAssembler.toModel(userPage, plainUserAssembler)).thenReturn(pagedModel);

        PagedAPIResponse response = controller.getPendingUsers(0, 20, new String[]{"createdDate", "desc"}).getBody();
        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertNotNull(response.getResponse().getData());
    }

    @Test
    @DisplayName("Get pending users - custom pagination")
    void getPendingUsers_customPagination() {
        // Setup mock data using TestUtil
        User mockUser = TestUtil.createUser(Role.ROLE_USER);
        Page<User> userPage = new PageImpl<>(List.of(mockUser));
        PagedModel<EntityModel<Object>> pagedModel = PagedModel.empty();

        when(userService.getPendingUsers(any(Pageable.class))).thenReturn(userPage);
        when(pagedResourcesAssembler.toModel(userPage, plainUserAssembler)).thenReturn(pagedModel);

        PagedAPIResponse response = controller.getPendingUsers(1, 10, new String[]{"firstName", "asc"}).getBody();
        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertNotNull(response.getResponse().getData());
    }

    @Test
    @DisplayName("Get user by ID - success")
    void getUserById_success() {
        // Setup mock data using TestUtil
        User mockUser = TestUtil.createUser(Role.ROLE_USER);
        UserProfileResponse userProfileResponse = TestUtil.createUserProfileResponse();

        when(userService.getUserById(USER_ID)).thenReturn(mockUser);

        try (var mockedStatic = mockStatic(UserService.class)) {
            mockedStatic.when(() -> UserService.toUserProfileResponse(mockUser)).thenReturn(userProfileResponse);

            GenericApiResponse<UserProfileResponse> response = controller.getUserById(USER_ID).getBody();
            assertNotNull(response);
            assertNotNull(response.getData());
        }
    }

    @Test
    @DisplayName("Get user by ID - user not found")
    void getUserById_userNotFound() {
        doThrow(BadRequestException.class).when(userService).getUserById(USER_ID);
        assertThrows(BadRequestException.class, () -> controller.getUserById(USER_ID));
    }

    @Test
    @DisplayName("Update user status - enable")
    void updateUserStatus_enable() {
        // Setup mock data using TestUtil
        User mockUser = TestUtil.createUser(Role.ROLE_USER);
        UserProfileResponse userProfileResponse = TestUtil.createUserProfileResponse();

        when(userService.updateUserStatus(USER_ID, true)).thenReturn(mockUser);

        try (var mockedStatic = mockStatic(UserService.class)) {
            mockedStatic.when(() -> UserService.toUserProfileResponse(mockUser)).thenReturn(userProfileResponse);

            GenericApiResponse<UserProfileResponse> response = controller.updateUserStatus(USER_ID, true).getBody();
            assertNotNull(response);
            assertNotNull(response.getData());
        }
    }

    @Test
    @DisplayName("Update user status - disable")
    void updateUserStatus_disable() {
        // Setup mock data using TestUtil
        User mockUser = TestUtil.createUser(Role.ROLE_USER);
        UserProfileResponse userProfileResponse = TestUtil.createUserProfileResponse();

        when(userService.updateUserStatus(USER_ID, false)).thenReturn(mockUser);

        try (var mockedStatic = mockStatic(UserService.class)) {
            mockedStatic.when(() -> UserService.toUserProfileResponse(mockUser)).thenReturn(userProfileResponse);

            GenericApiResponse<UserProfileResponse> response = controller.updateUserStatus(USER_ID, false).getBody();
            assertNotNull(response);
            assertNotNull(response.getData());
        }
    }

    @Test
    @DisplayName("Update user status - service error")
    void updateUserStatus_error() {
        doThrow(BadRequestException.class).when(userService).updateUserStatus(USER_ID, true);
        assertThrows(BadRequestException.class, () -> controller.updateUserStatus(USER_ID, true));
    }

    @Test
    @DisplayName("Delete user - success")
    void deleteUser_success() {
        // Setup mock data using TestUtil
        User mockUser = TestUtil.createUser(Role.ROLE_USER);
        when(authentication.getPrincipal()).thenReturn(TestUtil.createApiUserPrincipal(Role.ROLE_USER));

        when(userService.deleteUser(USER_ID, "mock-role_user-id")).thenReturn(null);

        GenericApiResponse<String> response = controller.deleteUser(USER_ID, authentication).getBody();
        assertNotNull(response);
        assertNotNull(response.getData());
    }

    @Test
    @DisplayName("Delete user - service error")
    void deleteUser_error() {
        when(authentication.getPrincipal()).thenReturn(TestUtil.createApiUserPrincipal(Role.ROLE_USER));
        doThrow(BadRequestException.class).when(userService).deleteUser(USER_ID, "mock-role_user-id");
        assertThrows(BadRequestException.class, () -> controller.deleteUser(USER_ID, authentication));
    }
}
