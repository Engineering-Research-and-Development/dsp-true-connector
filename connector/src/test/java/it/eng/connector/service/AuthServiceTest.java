package it.eng.connector.service;

import it.eng.connector.configuration.ApiUserPrincipal;
import it.eng.connector.dto.LoginRequest;
import it.eng.connector.dto.LoginResponse;
import it.eng.connector.dto.LogoutRequest;
import it.eng.connector.dto.RefreshTokenRequest;
import it.eng.connector.dto.RegisterRequest;
import it.eng.connector.dto.UserProfileResponse;
import it.eng.connector.model.RefreshToken;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.util.PasswordCheckValidator;
import it.eng.connector.util.TestUtil;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String USER_ID = "test-user-id";
    private static final String USER_EMAIL = "test@example.com";
    private static final String USER_PASSWORD = "password123";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordCheckValidator passwordCheckValidator;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private Authentication authentication;
    @Mock
    private ApiUserPrincipal userPrincipal;

    private AuthService authService;

    private User mockUser;
    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;
    private RefreshTokenRequest refreshRequest;
    private LogoutRequest logoutRequest;

    @BeforeEach
    void setUp() {
        // Create AuthService manually since it doesn't have a default constructor
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                passwordCheckValidator,
                jwtTokenService,
                true, // registrationAuto
                "ROLE_USER" // defaultRole
        );

        mockUser = TestUtil.createUser(Role.ROLE_USER);
        mockUser.setId(USER_ID);
        mockUser.setEmail(USER_EMAIL);
        mockUser.setPassword("encoded-password");
        mockUser.setEnabled(true);

        loginRequest = LoginRequest.builder()
                .email(USER_EMAIL)
                .password(USER_PASSWORD)
                .build();

        registerRequest = RegisterRequest.builder()
                .firstName("Test")
                .lastName("User")
                .email(USER_EMAIL)
                .password(USER_PASSWORD)
                .build();

        refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(REFRESH_TOKEN)
                .build();

        logoutRequest = LogoutRequest.builder()
                .accessToken(ACCESS_TOKEN)
                .refreshToken(REFRESH_TOKEN)
                .build();
    }

    @Test
    @DisplayName("Login - success")
    void testLogin() {
        // Given
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(USER_PASSWORD, mockUser.getPassword())).thenReturn(true);
        when(jwtTokenService.generateAccessToken(mockUser)).thenReturn(ACCESS_TOKEN);
        when(jwtTokenService.generateRefreshToken(mockUser)).thenReturn(REFRESH_TOKEN);
        when(jwtTokenService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        // When
        LoginResponse response = authService.login(loginRequest);

        // Then
        assertNotNull(response);
        assertEquals(ACCESS_TOKEN, response.getAccessToken());
        assertEquals(REFRESH_TOKEN, response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals(USER_ID, response.getUserId());
        assertEquals(mockUser.getFirstName(), response.getFirstName());
        assertEquals(mockUser.getLastName(), response.getLastName());
        assertEquals(mockUser.getEmail(), response.getEmail());
        assertEquals(mockUser.getRole(), response.getRole());

        verify(userRepository).save(mockUser);
        verify(jwtTokenService).generateAccessToken(mockUser);
        verify(jwtTokenService).generateRefreshToken(mockUser);
    }

    @Test
    @DisplayName("Login - user not found")
    void testLogin_userNotFound() {
        // Given
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
        verify(jwtTokenService, never()).generateAccessToken(any());
        verify(jwtTokenService, never()).generateRefreshToken(any());
    }

    @Test
    @DisplayName("Login - invalid password")
    void testLogin_invalidPassword() {
        // Given
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(USER_PASSWORD, mockUser.getPassword())).thenReturn(false);

        // When & Then
        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
        verify(jwtTokenService, never()).generateAccessToken(any());
        verify(jwtTokenService, never()).generateRefreshToken(any());
    }

    @Test
    @DisplayName("Login - disabled account")
    void testLogin_disabledAccount() {
        // Given
        mockUser.setEnabled(false);
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));

        // When & Then
        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
        verify(jwtTokenService, never()).generateAccessToken(any());
        verify(jwtTokenService, never()).generateRefreshToken(any());
    }

    @Test
    @DisplayName("Register - success with auto approval")
    void testRegister_autoApproval() {
        // Given
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(USER_PASSWORD)).thenReturn("encoded-password");
        when(jwtTokenService.generateAccessToken(any(User.class))).thenReturn(ACCESS_TOKEN);
        when(jwtTokenService.generateRefreshToken(any(User.class))).thenReturn(REFRESH_TOKEN);
        when(jwtTokenService.getAccessTokenExpirationSeconds()).thenReturn(3600L);
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        // When
        LoginResponse response = authService.register(registerRequest);

        // Then
        assertNotNull(response);
        assertEquals(ACCESS_TOKEN, response.getAccessToken());
        assertEquals(REFRESH_TOKEN, response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals(USER_ID, response.getUserId());
        assertNull(response.getMessage());

        verify(passwordCheckValidator).isValid(USER_PASSWORD);
        verify(userRepository).save(any(User.class));
        verify(jwtTokenService).generateAccessToken(any(User.class));
        verify(jwtTokenService).generateRefreshToken(any(User.class));
    }

    @Test
    @DisplayName("Register - success with manual approval")
    void testRegister_manualApproval() {
        // Given
        AuthService authServiceManual = new AuthService(userRepository, passwordEncoder, 
                passwordCheckValidator, jwtTokenService, false, "ROLE_USER");
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(USER_PASSWORD)).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        // When
        LoginResponse response = authServiceManual.register(registerRequest);

        // Then
        assertNotNull(response);
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType()); // Default value from @Builder.Default
        assertEquals(0L, response.getExpiresIn());
        assertEquals(USER_ID, response.getUserId());
        assertEquals("Registration successful. Your account is pending admin approval.", response.getMessage());

        verify(passwordCheckValidator).isValid(USER_PASSWORD);
        verify(userRepository).save(any(User.class));
        verify(jwtTokenService, never()).generateAccessToken(any());
        verify(jwtTokenService, never()).generateRefreshToken(any());
    }

    @Test
    @DisplayName("Register - duplicate email")
    void testRegister_duplicateEmail() {
        // Given
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));

        // When & Then
        assertThrows(BadRequestException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Register - duplicate key exception")
    void testRegister_duplicateKeyException() {
        // Given
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(USER_PASSWORD)).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("Duplicate key"));

        // When & Then
        assertThrows(BadRequestException.class, () -> authService.register(registerRequest));
    }

    @Test
    @DisplayName("Register - invalid password")
    void testRegister_invalidPassword() {
        // Given
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());
        doThrow(new BadRequestException("Invalid password")).when(passwordCheckValidator).isValid(USER_PASSWORD);

        // When & Then
        assertThrows(BadRequestException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Refresh token - success")
    void testRefreshToken() {
        // Given
        RefreshToken refreshToken = RefreshToken.builder()
                .token(REFRESH_TOKEN)
                .user(mockUser)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .revoked(false)
                .build();

        when(jwtTokenService.validateRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(refreshToken));
        when(jwtTokenService.generateAccessToken(mockUser)).thenReturn(ACCESS_TOKEN);
        when(jwtTokenService.generateRefreshToken(mockUser)).thenReturn(REFRESH_TOKEN);
        when(jwtTokenService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        // When
        LoginResponse response = authService.refreshToken(refreshRequest);

        // Then
        assertNotNull(response);
        assertEquals(ACCESS_TOKEN, response.getAccessToken());
        assertEquals(REFRESH_TOKEN, response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals(USER_ID, response.getUserId());

        verify(jwtTokenService).revokeRefreshToken(REFRESH_TOKEN);
        verify(jwtTokenService).generateAccessToken(mockUser);
        verify(jwtTokenService).generateRefreshToken(mockUser);
    }

    @Test
    @DisplayName("Refresh token - invalid token")
    void testRefreshToken_invalidToken() {
        // Given
        when(jwtTokenService.validateRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BadCredentialsException.class, () -> authService.refreshToken(refreshRequest));
        verify(jwtTokenService, never()).generateAccessToken(any());
        verify(jwtTokenService, never()).generateRefreshToken(any());
    }

    @Test
    @DisplayName("Refresh token - disabled user")
    void testRefreshToken_disabledUser() {
        // Given
        mockUser.setEnabled(false);
        RefreshToken refreshToken = RefreshToken.builder()
                .token(REFRESH_TOKEN)
                .user(mockUser)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .revoked(false)
                .build();

        when(jwtTokenService.validateRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(refreshToken));

        // When & Then
        assertThrows(BadCredentialsException.class, () -> authService.refreshToken(refreshRequest));
        verify(jwtTokenService, never()).generateAccessToken(any());
        verify(jwtTokenService, never()).generateRefreshToken(any());
    }

    @Test
    @DisplayName("Logout - with tokens")
    void testLogout_withTokens() {
        // When
        authService.logout(logoutRequest);

        // Then
        verify(jwtTokenService).revokeRefreshToken(REFRESH_TOKEN);
        verify(jwtTokenService).revokeAccessToken(ACCESS_TOKEN, "logout");
    }

    @Test
    @DisplayName("Logout - with auth header")
    void testLogout_withAuthHeader() {
        // Given
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(userPrincipal.getUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));

        // When
        authService.logout(authentication, ACCESS_TOKEN);

        // Then
        verify(jwtTokenService).revokeAccessToken(ACCESS_TOKEN, "logout");
        verify(jwtTokenService).revokeAllUserRefreshTokens(mockUser);
    }

    @Test
    @DisplayName("Logout - invalid authentication")
    void testLogout_invalidAuthentication() {
        // Given
        when(authentication.getPrincipal()).thenReturn(null);

        // When & Then
        assertThrows(BadCredentialsException.class, () -> authService.logout(authentication, ACCESS_TOKEN));
        verify(jwtTokenService, never()).revokeAccessToken(any(), any());
        verify(jwtTokenService, never()).revokeAllUserRefreshTokens(any());
    }

    @Test
    @DisplayName("Logout - user not found")
    void testLogout_userNotFound() {
        // Given
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(userPrincipal.getUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BadCredentialsException.class, () -> authService.logout(authentication, ACCESS_TOKEN));
        verify(jwtTokenService, never()).revokeAccessToken(any(), any());
        verify(jwtTokenService, never()).revokeAllUserRefreshTokens(any());
    }

    @Test
    @DisplayName("Get current user - success")
    void testGetCurrentUser() {
        // Given
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));

        // When
        UserProfileResponse response = authService.getCurrentUser(USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(USER_ID, response.getUserId());
        assertEquals(mockUser.getFirstName(), response.getFirstName());
        assertEquals(mockUser.getLastName(), response.getLastName());
        assertEquals(mockUser.getEmail(), response.getEmail());
        assertEquals(mockUser.getRole(), response.getRole());
        assertEquals(mockUser.isEnabled(), response.isEnabled());
    }

    @Test
    @DisplayName("Get current user - user not found")
    void testGetCurrentUser_userNotFound() {
        // Given
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> authService.getCurrentUser(USER_ID));
    }

    @Test
    @DisplayName("To user profile response")
    void testToUserProfileResponse() {
        // When
        UserProfileResponse response = AuthService.toUserProfileResponse(mockUser);

        // Then
        assertNotNull(response);
        assertEquals(mockUser.getId(), response.getUserId());
        assertEquals(mockUser.getFirstName(), response.getFirstName());
        assertEquals(mockUser.getLastName(), response.getLastName());
        assertEquals(mockUser.getEmail(), response.getEmail());
        assertEquals(mockUser.getRole(), response.getRole());
        assertEquals(mockUser.isEnabled(), response.isEnabled());
    }
}
