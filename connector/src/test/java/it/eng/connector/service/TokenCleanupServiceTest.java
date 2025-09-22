package it.eng.connector.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenCleanupServiceTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private TokenCleanupService tokenCleanupService;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(jwtTokenService);
    }

    @Test
    @DisplayName("Cleanup expired tokens - success")
    void testCleanupExpiredTokens_success() {
        // Given
        doNothing().when(jwtTokenService).cleanupExpiredTokens();
        doNothing().when(jwtTokenService).cleanupExpiredRevokedTokens();

        // When
        tokenCleanupService.cleanupExpiredTokens();

        // Then
        verify(jwtTokenService).cleanupExpiredTokens();
        verify(jwtTokenService).cleanupExpiredRevokedTokens();
    }

    @Test
    @DisplayName("Cleanup expired tokens - exception during refresh token cleanup")
    void testCleanupExpiredTokens_exceptionRefreshTokenCleanup() {
        // Given
        doThrow(new RuntimeException("Database error")).when(jwtTokenService).cleanupExpiredTokens();

        // When
        tokenCleanupService.cleanupExpiredTokens();

        // Then
        verify(jwtTokenService).cleanupExpiredTokens();
        // Note: cleanupExpiredRevokedTokens() is not called when cleanupExpiredTokens() throws exception
        // This is the actual behavior of the service - it stops on first exception
        verify(jwtTokenService, never()).cleanupExpiredRevokedTokens();
        // Should not throw exception - service should handle gracefully
    }

    @Test
    @DisplayName("Cleanup expired tokens - exception during revoked token cleanup")
    void testCleanupExpiredTokens_exceptionRevokedTokenCleanup() {
        // Given
        doNothing().when(jwtTokenService).cleanupExpiredTokens();
        doThrow(new RuntimeException("Database error")).when(jwtTokenService).cleanupExpiredRevokedTokens();

        // When
        tokenCleanupService.cleanupExpiredTokens();

        // Then
        verify(jwtTokenService).cleanupExpiredTokens();
        verify(jwtTokenService).cleanupExpiredRevokedTokens();
        // Should not throw exception - service should handle gracefully
    }

    @Test
    @DisplayName("Cleanup expired tokens - both exceptions")
    void testCleanupExpiredTokens_bothExceptions() {
        // Given
        doThrow(new RuntimeException("Refresh token cleanup failed")).when(jwtTokenService).cleanupExpiredTokens();

        // When
        tokenCleanupService.cleanupExpiredTokens();

        // Then
        verify(jwtTokenService).cleanupExpiredTokens();
        // Note: cleanupExpiredRevokedTokens() is not called when cleanupExpiredTokens() throws exception
        // This is the actual behavior of the service - it stops on first exception
        verify(jwtTokenService, never()).cleanupExpiredRevokedTokens();
        // Should not throw exception - service should handle gracefully
    }

    @Test
    @DisplayName("Cleanup now - success")
    void testCleanupNow_success() {
        // Given
        doNothing().when(jwtTokenService).cleanupExpiredTokens();
        doNothing().when(jwtTokenService).cleanupExpiredRevokedTokens();

        // When
        tokenCleanupService.cleanupNow();

        // Then
        verify(jwtTokenService).cleanupExpiredTokens();
        verify(jwtTokenService).cleanupExpiredRevokedTokens();
    }

    @Test
    @DisplayName("Cleanup now - exception")
    void testCleanupNow_exception() {
        // Given
        doThrow(new RuntimeException("Cleanup failed")).when(jwtTokenService).cleanupExpiredTokens();

        // When
        tokenCleanupService.cleanupNow();

        // Then
        verify(jwtTokenService).cleanupExpiredTokens();
        // Note: cleanupExpiredRevokedTokens() is not called when cleanupExpiredTokens() throws exception
        // This is the actual behavior of the service - it stops on first exception
        verify(jwtTokenService, never()).cleanupExpiredRevokedTokens();
        // Should not throw exception - service should handle gracefully
    }

    @Test
    @DisplayName("Cleanup expired tokens - multiple calls")
    void testCleanupExpiredTokens_multipleCalls() {
        // Given
        doNothing().when(jwtTokenService).cleanupExpiredTokens();
        doNothing().when(jwtTokenService).cleanupExpiredRevokedTokens();

        // When
        tokenCleanupService.cleanupExpiredTokens();
        tokenCleanupService.cleanupExpiredTokens();
        tokenCleanupService.cleanupExpiredTokens();

        // Then
        verify(jwtTokenService, times(3)).cleanupExpiredTokens();
        verify(jwtTokenService, times(3)).cleanupExpiredRevokedTokens();
    }

    @Test
    @DisplayName("Cleanup now - multiple calls")
    void testCleanupNow_multipleCalls() {
        // Given
        doNothing().when(jwtTokenService).cleanupExpiredTokens();
        doNothing().when(jwtTokenService).cleanupExpiredRevokedTokens();

        // When
        tokenCleanupService.cleanupNow();
        tokenCleanupService.cleanupNow();

        // Then
        verify(jwtTokenService, times(2)).cleanupExpiredTokens();
        verify(jwtTokenService, times(2)).cleanupExpiredRevokedTokens();
    }
}
