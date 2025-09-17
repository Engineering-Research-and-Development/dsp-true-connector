package it.eng.connector.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for periodic cleanup of expired tokens.
 * This service runs scheduled tasks to clean up expired refresh tokens
 * and revoked access tokens to prevent database bloat.
 */
@Service
@Slf4j
public class TokenCleanupService {

    private final JwtTokenService jwtTokenService;

    public TokenCleanupService(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Clean up expired refresh tokens and revoked access tokens.
     * Runs every hour (3600000 milliseconds).
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanupExpiredTokens() {
        log.debug("Starting scheduled token cleanup");
        
        try {
            // Clean up expired refresh tokens
            jwtTokenService.cleanupExpiredTokens();
            
            // Clean up expired revoked access tokens (though MongoDB TTL should handle this)
            jwtTokenService.cleanupExpiredRevokedTokens();
            
            log.debug("Token cleanup completed successfully");
        } catch (Exception e) {
            log.error("Error during token cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up expired tokens immediately (for manual triggers or testing).
     */
    public void cleanupNow() {
        log.info("Manual token cleanup triggered");
        cleanupExpiredTokens();
    }
}
