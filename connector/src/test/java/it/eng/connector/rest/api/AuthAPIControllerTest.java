package it.eng.connector.rest.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import it.eng.connector.configuration.ApiUserPrincipal;
import it.eng.connector.dto.LoginRequest;
import it.eng.connector.dto.LoginResponse;
import it.eng.connector.dto.RefreshTokenRequest;
import it.eng.connector.dto.RegisterRequest;
import it.eng.connector.dto.UserProfileResponse;
import it.eng.connector.model.Role;
import it.eng.connector.service.AuthService;
import it.eng.connector.util.TestUtil;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
class AuthAPIControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthAPIController controller;

    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;
    private RefreshTokenRequest refreshRequest;
    private LoginResponse loginResponse;
    private UserProfileResponse userProfileResponse;
    private ApiUserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        // Create test data using TestUtil pattern
        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");

        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");

        refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken("refresh-token");

        loginResponse = LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .userId("user-id")
                .firstName("John")
                .lastName("Doe")
                .email("test@example.com")
                .role(Role.ROLE_USER)
                .build();

        userProfileResponse = TestUtil.createUserProfileResponse();
        userPrincipal = TestUtil.createApiUserPrincipal(Role.ROLE_USER);
    }

    @Test
    @DisplayName("Login - success")
    void testLogin_success() {
        // Given
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        // When
        ResponseEntity<GenericApiResponse<LoginResponse>> response = controller.login(loginRequest);

        // Then
        verify(authService).login(loginRequest);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertEquals("access-token", response.getBody().getData().getAccessToken());
        assertEquals("refresh-token", response.getBody().getData().getRefreshToken());
        assertEquals("user-id", response.getBody().getData().getUserId());
        assertEquals("John", response.getBody().getData().getFirstName());
        assertEquals("Doe", response.getBody().getData().getLastName());
        assertEquals("test@example.com", response.getBody().getData().getEmail());
        assertEquals(Role.ROLE_USER, response.getBody().getData().getRole());
    }

    @Test
    @DisplayName("Login - invalid credentials")
    void testLogin_invalidCredentials() {
        // Given
        when(authService.login(any(LoginRequest.class))).thenThrow(new BadCredentialsException("Invalid email or password"));

        // When & Then
        assertThrows(BadCredentialsException.class, () -> controller.login(loginRequest));
        verify(authService).login(loginRequest);
    }

    @Test
    @DisplayName("Register - success")
    void testRegister_success() {
        // Given
        when(authService.register(any(RegisterRequest.class))).thenReturn(loginResponse);

        // When
        ResponseEntity<GenericApiResponse<LoginResponse>> response = controller.register(registerRequest);

        // Then
        verify(authService).register(registerRequest);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertEquals("access-token", response.getBody().getData().getAccessToken());
        assertEquals("refresh-token", response.getBody().getData().getRefreshToken());
        assertEquals("user-id", response.getBody().getData().getUserId());
    }

    @Test
    @DisplayName("Register - duplicate email")
    void testRegister_duplicateEmail() {
        // Given
        when(authService.register(any(RegisterRequest.class))).thenThrow(new BadRequestException("User with this email already exists"));

        // When & Then
        assertThrows(BadRequestException.class, () -> controller.register(registerRequest));
        verify(authService).register(registerRequest);
    }

    @Test
    @DisplayName("Refresh token - success")
    void testRefreshToken_success() {
        // Given
        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(loginResponse);

        // When
        ResponseEntity<GenericApiResponse<LoginResponse>> response = controller.refreshToken(refreshRequest);

        // Then
        verify(authService).refreshToken(refreshRequest);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertEquals("access-token", response.getBody().getData().getAccessToken());
        assertEquals("refresh-token", response.getBody().getData().getRefreshToken());
    }

    @Test
    @DisplayName("Refresh token - invalid token")
    void testRefreshToken_invalidToken() {
        // Given
        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenThrow(new BadCredentialsException("Invalid or expired refresh token"));

        // When & Then
        assertThrows(BadCredentialsException.class, () -> controller.refreshToken(refreshRequest));
        verify(authService).refreshToken(refreshRequest);
    }

    @Test
    @DisplayName("Logout - success")
    void testLogout_success() {
        // Given
        String authorizationHeader = "Bearer valid-token";

        // When
        ResponseEntity<GenericApiResponse<String>> response = controller.logout(authorizationHeader, authentication);

        // Then
        verify(authService).logout(authentication, "valid-token");
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("success", response.getBody().getData());
    }

    @Test
    @DisplayName("Logout - invalid token")
    void testLogout_invalidToken() {
        // Given
        String invalidHeader = "Invalid token";

        // When
        ResponseEntity<GenericApiResponse<String>> response = controller.logout(invalidHeader, null);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Authorization: Bearer <token> header is required"));
    }

    @Test
    @DisplayName("Get current user - success")
    void testGetCurrentUser_success() {
        // Given
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(authService.getCurrentUser(anyString())).thenReturn(userProfileResponse);

        // When
        ResponseEntity<GenericApiResponse<UserProfileResponse>> response = controller.getCurrentUser(authentication);

        // Then
        verify(authService).getCurrentUser(userPrincipal.getUserId());
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertEquals("mock-user-id-12345", response.getBody().getData().getUserId());
        assertEquals("Test", response.getBody().getData().getFirstName());
        assertEquals("User", response.getBody().getData().getLastName());
        assertEquals("test@example.com", response.getBody().getData().getEmail());
        assertEquals(Role.ROLE_USER, response.getBody().getData().getRole());
        assertTrue(response.getBody().getData().isEnabled());
    }

    @Test
    @DisplayName("Get current user - not authenticated")
    void testGetCurrentUser_notAuthenticated() {
        // When & Then
        assertThrows(NullPointerException.class, () -> controller.getCurrentUser(null));
    }

    @Test
    @DisplayName("Login - invalid request body")
    void testLogin_invalidRequestBody() {
        // Given
        LoginRequest invalidRequest = new LoginRequest();
        // Missing required fields

        // When & Then
        // This would be handled by @Valid annotation in real scenario
        // For unit test, we just verify the method can be called
        assertDoesNotThrow(() -> controller.login(invalidRequest));
    }

    @Test
    @DisplayName("Register - invalid request body")
    void testRegister_invalidRequestBody() {
        // Given
        RegisterRequest invalidRequest = new RegisterRequest();
        // Missing required fields

        // When & Then
        // This would be handled by @Valid annotation in real scenario
        // For unit test, we just verify the method can be called
        assertDoesNotThrow(() -> controller.register(invalidRequest));
    }

    @Test
    @DisplayName("Refresh token - invalid request body")
    void testRefreshToken_invalidRequestBody() {
        // Given
        RefreshTokenRequest invalidRequest = new RefreshTokenRequest();
        // Missing required fields

        // When & Then
        // This would be handled by @Valid annotation in real scenario
        // For unit test, we just verify the method can be called
        assertDoesNotThrow(() -> controller.refreshToken(invalidRequest));
    }
}