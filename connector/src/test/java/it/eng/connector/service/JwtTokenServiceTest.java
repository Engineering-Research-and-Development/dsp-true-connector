package it.eng.connector.service;

import io.jsonwebtoken.Claims;
import it.eng.connector.model.RefreshToken;
import it.eng.connector.model.RevokedAccessToken;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.repository.RefreshTokenRepository;
import it.eng.connector.repository.RevokedAccessTokenRepository;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.util.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm";
    private static final long ACCESS_TOKEN_EXPIRATION = 3600000L; // 1 hour
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7 days
    private static final String ISSUER = "test-issuer";
    private static final String USER_ID = "test-user-id";
    private static final String TOKEN_ID = "token-id-123";
    private static final String REFRESH_TOKEN_VALUE = "refresh-token-123";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private RevokedAccessTokenRepository revokedAccessTokenRepository;
    @Mock
    private UserRepository userRepository;

    private JwtTokenService jwtTokenService;

    private User mockUser;
    private RefreshToken mockRefreshToken;

    @BeforeEach
    void setUp() {
        // Create JwtTokenService with test values
        jwtTokenService = new JwtTokenService(
                SECRET,
                ACCESS_TOKEN_EXPIRATION,
                REFRESH_TOKEN_EXPIRATION,
                ISSUER,
                refreshTokenRepository,
                revokedAccessTokenRepository,
                userRepository
        );

        mockUser = TestUtil.createUser(Role.ROLE_USER);
        mockUser.setId(USER_ID);

        mockRefreshToken = RefreshToken.builder()
                .id("refresh-token-id")
                .token(REFRESH_TOKEN_VALUE)
                .user(mockUser)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .createdDate(LocalDateTime.now())
                .revoked(false)
                .build();
    }

    @Test
    @DisplayName("Generate access token - success")
    void testGenerateAccessToken() {
        // When
        String token = jwtTokenService.generateAccessToken(mockUser);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        // Token should contain dots (JWT format)
        assertTrue(token.contains("."));
    }

    @Test
    @DisplayName("Generate refresh token - success")
    void testGenerateRefreshToken() {
        // Given
        when(refreshTokenRepository.findByUser(mockUser)).thenReturn(List.of());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(mockRefreshToken);

        // When
        String token = jwtTokenService.generateRefreshToken(mockUser);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Generate refresh token - cleanup old tokens")
    void testGenerateRefreshToken_cleanupOldTokens() {
        // Given
        RefreshToken oldToken1 = RefreshToken.builder()
                .token("old-token-1")
                .user(mockUser)
                .createdDate(LocalDateTime.now().minusDays(1))
                .revoked(false)
                .build();
        RefreshToken oldToken2 = RefreshToken.builder()
                .token("old-token-2")
                .user(mockUser)
                .createdDate(LocalDateTime.now().minusDays(2))
                .revoked(false)
                .build();
        RefreshToken oldToken3 = RefreshToken.builder()
                .token("old-token-3")
                .user(mockUser)
                .createdDate(LocalDateTime.now().minusDays(3))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByUser(mockUser)).thenReturn(new ArrayList<>(List.of(oldToken1, oldToken2, oldToken3)));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(mockRefreshToken);
        when(refreshTokenRepository.saveAll(anyList())).thenReturn(new ArrayList<>());

        // When
        String token = jwtTokenService.generateRefreshToken(mockUser);

        // Then
        assertNotNull(token);
        verify(refreshTokenRepository).saveAll(anyList()); // Should revoke old tokens
        verify(refreshTokenRepository).save(any(RefreshToken.class)); // Should save new token
    }

    @Test
    @DisplayName("Validate access token - success")
    void testValidateAccessToken() {
        // Given
        String token = jwtTokenService.generateAccessToken(mockUser);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
        when(revokedAccessTokenRepository.existsByTokenId(anyString())).thenReturn(false);

        // When
        Claims claims = jwtTokenService.validateAccessToken(token);

        // Then
        assertNotNull(claims);
        assertEquals(USER_ID, claims.getSubject());
        assertEquals(mockUser.getEmail(), claims.get("email", String.class));
        assertEquals(mockUser.getFirstName(), claims.get("firstName", String.class));
        assertEquals(mockUser.getLastName(), claims.get("lastName", String.class));
        assertEquals(mockUser.getRole().name(), claims.get("role", String.class));
        assertEquals(ISSUER, claims.getIssuer());
    }

    @Test
    @DisplayName("Validate access token - revoked token")
    void testValidateAccessToken_revokedToken() {
        // Given
        String token = jwtTokenService.generateAccessToken(mockUser);
        when(revokedAccessTokenRepository.existsByTokenId(anyString())).thenReturn(true);

        // When & Then
        assertThrows(RuntimeException.class, () -> jwtTokenService.validateAccessToken(token));
    }

    @Test
    @DisplayName("Validate access token - user not found")
    void testValidateAccessToken_userNotFound() {
        // Given
        String token = jwtTokenService.generateAccessToken(mockUser);
        when(revokedAccessTokenRepository.existsByTokenId(anyString())).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> jwtTokenService.validateAccessToken(token));
    }

    @Test
    @DisplayName("Validate access token - disabled user")
    void testValidateAccessToken_disabledUser() {
        // Given
        mockUser.setEnabled(false);
        String token = jwtTokenService.generateAccessToken(mockUser);
        when(revokedAccessTokenRepository.existsByTokenId(anyString())).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));

        // When & Then
        assertThrows(RuntimeException.class, () -> jwtTokenService.validateAccessToken(token));
    }

    @Test
    @DisplayName("Validate access token - locked user")
    void testValidateAccessToken_lockedUser() {
        // Given
        mockUser.setLocked(true);
        String token = jwtTokenService.generateAccessToken(mockUser);
        when(revokedAccessTokenRepository.existsByTokenId(anyString())).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));

        // When & Then
        assertThrows(RuntimeException.class, () -> jwtTokenService.validateAccessToken(token));
    }

    @Test
    @DisplayName("Validate access token - expired user")
    void testValidateAccessToken_expiredUser() {
        // Given
        mockUser.setExpired(true);
        String token = jwtTokenService.generateAccessToken(mockUser);
        when(revokedAccessTokenRepository.existsByTokenId(anyString())).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));

        // When & Then
        assertThrows(RuntimeException.class, () -> jwtTokenService.validateAccessToken(token));
    }

    @Test
    @DisplayName("Validate refresh token - success")
    void testValidateRefreshToken() {
        // Given
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN_VALUE)).thenReturn(Optional.of(mockRefreshToken));

        // When
        Optional<RefreshToken> result = jwtTokenService.validateRefreshToken(REFRESH_TOKEN_VALUE);

        // Then
        assertTrue(result.isPresent());
        assertEquals(mockRefreshToken, result.get());
    }

    @Test
    @DisplayName("Validate refresh token - not found")
    void testValidateRefreshToken_notFound() {
        // Given
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN_VALUE)).thenReturn(Optional.empty());

        // When
        Optional<RefreshToken> result = jwtTokenService.validateRefreshToken(REFRESH_TOKEN_VALUE);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Validate refresh token - revoked")
    void testValidateRefreshToken_revoked() {
        // Given
        mockRefreshToken.setRevoked(true);
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN_VALUE)).thenReturn(Optional.of(mockRefreshToken));

        // When
        Optional<RefreshToken> result = jwtTokenService.validateRefreshToken(REFRESH_TOKEN_VALUE);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Validate refresh token - expired")
    void testValidateRefreshToken_expired() {
        // Given
        mockRefreshToken.setExpiryDate(LocalDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN_VALUE)).thenReturn(Optional.of(mockRefreshToken));
        doNothing().when(refreshTokenRepository).delete(any(RefreshToken.class));

        // When
        Optional<RefreshToken> result = jwtTokenService.validateRefreshToken(REFRESH_TOKEN_VALUE);

        // Then
        assertTrue(result.isEmpty());
        verify(refreshTokenRepository).delete(mockRefreshToken);
    }

    @Test
    @DisplayName("Revoke refresh token - success")
    void testRevokeRefreshToken() {
        // Given
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN_VALUE)).thenReturn(Optional.of(mockRefreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(mockRefreshToken);

        // When
        jwtTokenService.revokeRefreshToken(REFRESH_TOKEN_VALUE);

        // Then
        assertTrue(mockRefreshToken.isRevoked());
        verify(refreshTokenRepository).save(mockRefreshToken);
    }

    @Test
    @DisplayName("Revoke refresh token - not found")
    void testRevokeRefreshToken_notFound() {
        // Given
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN_VALUE)).thenReturn(Optional.empty());

        // When
        jwtTokenService.revokeRefreshToken(REFRESH_TOKEN_VALUE);

        // Then
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Revoke all user refresh tokens")
    void testRevokeAllUserRefreshTokens() {
        // Given
        RefreshToken token1 = RefreshToken.builder().token("token1").user(mockUser).revoked(false).build();
        RefreshToken token2 = RefreshToken.builder().token("token2").user(mockUser).revoked(false).build();
        List<RefreshToken> tokens = List.of(token1, token2);
        
        when(refreshTokenRepository.findByUser(mockUser)).thenReturn(tokens);
        when(refreshTokenRepository.saveAll(anyList())).thenReturn(tokens);

        // When
        jwtTokenService.revokeAllUserRefreshTokens(mockUser);

        // Then
        assertTrue(token1.isRevoked());
        assertTrue(token2.isRevoked());
        verify(refreshTokenRepository).saveAll(tokens);
    }

    @Test
    @DisplayName("Revoke access token - success")
    void testRevokeAccessToken() {
        // Given
        String token = jwtTokenService.generateAccessToken(mockUser);
        when(revokedAccessTokenRepository.existsByTokenId(anyString())).thenReturn(false);
        when(revokedAccessTokenRepository.save(any(RevokedAccessToken.class))).thenReturn(new RevokedAccessToken());

        // When
        jwtTokenService.revokeAccessToken(token, "test-reason");

        // Then
        verify(revokedAccessTokenRepository).save(any(RevokedAccessToken.class));
    }

    @Test
    @DisplayName("Revoke access token - already revoked")
    void testRevokeAccessToken_alreadyRevoked() {
        // Given
        String token = jwtTokenService.generateAccessToken(mockUser);
        when(revokedAccessTokenRepository.existsByTokenId(anyString())).thenReturn(true);

        // When
        jwtTokenService.revokeAccessToken(token, "test-reason");

        // Then
        verify(revokedAccessTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Is access token revoked - true")
    void testIsAccessTokenRevoked_true() {
        // Given
        when(revokedAccessTokenRepository.existsByTokenId(TOKEN_ID)).thenReturn(true);

        // When
        boolean result = jwtTokenService.isAccessTokenRevoked(TOKEN_ID);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Is access token revoked - false")
    void testIsAccessTokenRevoked_false() {
        // Given
        when(revokedAccessTokenRepository.existsByTokenId(TOKEN_ID)).thenReturn(false);

        // When
        boolean result = jwtTokenService.isAccessTokenRevoked(TOKEN_ID);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Cleanup expired tokens")
    void testCleanupExpiredTokens() {
        // Given
        doNothing().when(refreshTokenRepository).deleteByExpiryDateBefore(any(LocalDateTime.class));

        // When
        jwtTokenService.cleanupExpiredTokens();

        // Then
        verify(refreshTokenRepository).deleteByExpiryDateBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Get user ID from token")
    void testGetUserIdFromToken() {
        // Given
        String token = jwtTokenService.generateAccessToken(mockUser);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
        when(revokedAccessTokenRepository.existsByTokenId(anyString())).thenReturn(false);

        // When
        String userId = jwtTokenService.getUserIdFromToken(token);

        // Then
        assertEquals(USER_ID, userId);
    }

    @Test
    @DisplayName("Get access token expiration seconds")
    void testGetAccessTokenExpirationSeconds() {
        // When
        long expiration = jwtTokenService.getAccessTokenExpirationSeconds();

        // Then
        assertEquals(ACCESS_TOKEN_EXPIRATION / 1000, expiration);
    }

    @Test
    @DisplayName("Delete all user tokens")
    void testDeleteAllUserTokens() {
        // When
        jwtTokenService.deleteAllUserTokens(mockUser);

        // Then
        verify(refreshTokenRepository).deleteByUser(mockUser);
        verify(revokedAccessTokenRepository).deleteByUserId(mockUser.getId());
    }

    @Test
    @DisplayName("Cleanup expired revoked tokens")
    void testCleanupExpiredRevokedTokens() {
        // Given
        doNothing().when(revokedAccessTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));

        // When
        jwtTokenService.cleanupExpiredRevokedTokens();

        // Then
        verify(revokedAccessTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
}
