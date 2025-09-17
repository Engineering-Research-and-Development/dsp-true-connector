package it.eng.connector.rest.api;

import it.eng.connector.configuration.ApiUserPrincipal;
import it.eng.connector.dto.ChangePasswordRequest;
import it.eng.connector.dto.UpdateProfileRequest;
import it.eng.connector.dto.UserProfileResponse;
import it.eng.connector.service.UserService;
import it.eng.connector.util.TestUtil;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileAPIControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private Authentication authentication;
    @Mock
    private ApiUserPrincipal userPrincipal;

    @InjectMocks
    private UserProfileAPIController controller;

    @Test
    @DisplayName("Get current user profile - success")
    void testGetCurrentUserProfile_success() {
        // Given
        UserProfileResponse profileResponse = TestUtil.createUserProfileResponse();
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(userService.getCurrentUserProfile(any(ApiUserPrincipal.class))).thenReturn(profileResponse);

        // When
        ResponseEntity<GenericApiResponse<UserProfileResponse>> response = controller.getCurrentUserProfile(authentication);

        // Then
        verify(userService).getCurrentUserProfile(userPrincipal);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertEquals("mock-user-id-12345", response.getBody().getData().getUserId());
        assertEquals("Test", response.getBody().getData().getFirstName());
        assertEquals("User", response.getBody().getData().getLastName());
        assertEquals("test@example.com", response.getBody().getData().getEmail());
    }

    @Test
    @DisplayName("Get current user profile - service error")
    void testGetCurrentUserProfile_error() {
        // Given
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        doThrow(BadRequestException.class).when(userService).getCurrentUserProfile(any(ApiUserPrincipal.class));

        // When & Then
        assertThrows(BadRequestException.class, () -> controller.getCurrentUserProfile(authentication));
        verify(userService).getCurrentUserProfile(userPrincipal);
    }

    @Test
    @DisplayName("Update current user profile - success")
    void testUpdateCurrentUserProfile_success() {
        // Given
        UpdateProfileRequest updateRequest = TestUtil.createUpdateProfileRequest();
        UserProfileResponse profileResponse = TestUtil.createUserProfileResponse();
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(userService.updateCurrentUserProfile(any(UpdateProfileRequest.class), any(ApiUserPrincipal.class)))
                .thenReturn(profileResponse);

        // When
        ResponseEntity<GenericApiResponse<UserProfileResponse>> response = controller.updateCurrentUserProfile(updateRequest, authentication);

        // Then
        verify(userService).updateCurrentUserProfile(updateRequest, userPrincipal);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertEquals("mock-user-id-12345", response.getBody().getData().getUserId());
    }

    @Test
    @DisplayName("Update current user profile - service error")
    void testUpdateCurrentUserProfile_error() {
        // Given
        UpdateProfileRequest updateRequest = TestUtil.createUpdateProfileRequest();
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        doThrow(BadRequestException.class).when(userService).updateCurrentUserProfile(any(UpdateProfileRequest.class), any(ApiUserPrincipal.class));

        // When & Then
        assertThrows(BadRequestException.class, () -> controller.updateCurrentUserProfile(updateRequest, authentication));
        verify(userService).updateCurrentUserProfile(updateRequest, userPrincipal);
    }

    @Test
    @DisplayName("Change password - success")
    void testChangePassword_success() {
        // Given
        ChangePasswordRequest changeRequest = TestUtil.createChangePasswordRequest();
        when(authentication.getPrincipal()).thenReturn(userPrincipal);

        // When
        ResponseEntity<GenericApiResponse<String>> response = controller.changePassword(changeRequest, authentication);

        // Then
        verify(userService).changeCurrentUserPassword(changeRequest, userPrincipal);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("success", response.getBody().getData());
    }

    @Test
    @DisplayName("Change password - service error")
    void testChangePassword_error() {
        // Given
        ChangePasswordRequest changeRequest = TestUtil.createChangePasswordRequest();
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        doThrow(BadRequestException.class).when(userService).changeCurrentUserPassword(any(ChangePasswordRequest.class), any(ApiUserPrincipal.class));

        // When & Then
        assertThrows(BadRequestException.class, () -> controller.changePassword(changeRequest, authentication));
        verify(userService).changeCurrentUserPassword(changeRequest, userPrincipal);
    }

    @Test
    @DisplayName("Delete current user - success")
    void testDeleteCurrentUser_success() {
        // Given
        when(authentication.getPrincipal()).thenReturn(userPrincipal);

        // When
        ResponseEntity<GenericApiResponse<String>> response = controller.deleteCurrentUser(authentication);

        // Then
        verify(userService).deleteCurrentUser(userPrincipal);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("success", response.getBody().getData());
    }

    @Test
    @DisplayName("Delete current user - service error")
    void testDeleteCurrentUser_error() {
        // Given
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        doThrow(BadRequestException.class).when(userService).deleteCurrentUser(any(ApiUserPrincipal.class));

        // When & Then
        assertThrows(BadRequestException.class, () -> controller.deleteCurrentUser(authentication));
        verify(userService).deleteCurrentUser(userPrincipal);
    }
}