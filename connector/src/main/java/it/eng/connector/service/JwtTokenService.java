package it.eng.connector.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import it.eng.connector.model.RefreshToken;
import it.eng.connector.model.RevokedAccessToken;
import it.eng.connector.model.User;
import it.eng.connector.repository.RefreshTokenRepository;
import it.eng.connector.repository.RevokedAccessTokenRepository;
import it.eng.connector.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final String issuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final UserRepository userRepository;

    public JwtTokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${app.jwt.issuer}") String issuer,
            RefreshTokenRepository refreshTokenRepository,
            RevokedAccessTokenRepository revokedAccessTokenRepository,
            UserRepository userRepository) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.issuer = issuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * Generate access token for user.
     * @param user the user for which to generate the token
     * @return the generated JWT access token
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);
        String tokenId = UUID.randomUUID().toString(); // Generate unique token ID

        return Jwts.builder()
                .subject(user.getId())
                .id(tokenId) // Add JWT ID (jti) claim for blacklist tracking
                .claim("email", user.getEmail())
                .claim("firstName", user.getFirstName())
                .claim("lastName", user.getLastName())
                .claim("role", user.getRole().name())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generate refresh token for user.
     * @param user the user for which to generate the refresh token
     * @return the generated refresh token
     */
    public String generateRefreshToken(User user) {
        // Create refresh token
        String tokenValue = UUID.randomUUID().toString();
        
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000);
        
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .user(user)
                .expiryDate(expiryDate)
                .createdDate(LocalDateTime.now())
                .revoked(false)
                .build();
        
        // Clean up old refresh tokens for this user
        cleanupUserRefreshTokens(user);
        
        // Save new refresh token
        refreshTokenRepository.save(refreshToken);
        
        return tokenValue;
    }

    /**
     * Validate access token and extract user information.
     * @param token the JWT token to validate
     * @return the claims extracted from the token
     */
    public Claims validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            // Check if token is in blacklist
            String tokenId = claims.getId();
            if (tokenId != null && isAccessTokenRevoked(tokenId)) {
                log.debug("JWT token is revoked: {}", tokenId);
                throw new RuntimeException("JWT token has been revoked");
            }
            // Check user status: token must belong to an existing and enabled user
            String userId = claims.getSubject();
            if (userId == null) {
                throw new RuntimeException("JWT token subject missing");
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            if (!user.isEnabled() || user.isLocked() || user.isExpired()) {
                throw new RuntimeException("User is disabled or not allowed");
            }
            
            return claims;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
            throw new RuntimeException("JWT token is expired", e);
        } catch (UnsupportedJwtException e) {
            log.debug("JWT token is unsupported: {}", e.getMessage());
            throw new RuntimeException("JWT token is unsupported", e);
        } catch (MalformedJwtException e) {
            log.debug("JWT token is malformed: {}", e.getMessage());
            throw new RuntimeException("JWT token is malformed", e);
        } catch (IllegalArgumentException e) {
            log.debug("JWT token compact of handler are invalid: {}", e.getMessage());
            throw new RuntimeException("JWT token compact of handler are invalid", e);
        }
    }

    /**
     * Validate refresh token.
     * @param token the refresh token to validate
     * @return Optional containing the refresh token if valid, empty otherwise
     */
    public Optional<RefreshToken> validateRefreshToken(String token) {
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
        
        if (refreshTokenOpt.isEmpty()) {
            log.debug("Refresh token not found");
            return Optional.empty();
        }
        
        RefreshToken refreshToken = refreshTokenOpt.get();
        
        if (refreshToken.isRevoked()) {
            log.debug("Refresh token is revoked");
            return Optional.empty();
        }
        
        if (refreshToken.isExpired()) {
            log.debug("Refresh token is expired");
            // Clean up expired token
            refreshTokenRepository.delete(refreshToken);
            return Optional.empty();
        }
        
        return Optional.of(refreshToken);
    }

    /**
     * Revoke refresh token.
     * @param token the refresh token to revoke
     */
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
        });
    }

    /**
     * Revoke all refresh tokens for a user.
     * @param user the user whose tokens should be revoked
     */
    public void revokeAllUserRefreshTokens(User user) {
        List<RefreshToken> userTokens = refreshTokenRepository.findByUser(user);
        userTokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(userTokens);
    }

    /**
     * Clean up old refresh tokens for user (keep only the most recent ones).
     * @param user the user whose old tokens should be cleaned up
     */
    private void cleanupUserRefreshTokens(User user) {
        List<RefreshToken> userTokens = refreshTokenRepository.findByUser(user);
        
        // Keep only the 3 most recent tokens, revoke the rest
        if (userTokens.size() >= 3) {
            userTokens.sort((a, b) -> b.getCreatedDate().compareTo(a.getCreatedDate()));
            List<RefreshToken> tokensToRevoke = userTokens.subList(2, userTokens.size());
            tokensToRevoke.forEach(token -> token.setRevoked(true));
            refreshTokenRepository.saveAll(tokensToRevoke);
        }
    }

    /**
     * Clean up expired tokens (should be called periodically).
     */
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.deleteByExpiryDateBefore(now);
    }

    /**
     * Extract user ID from JWT token.
     * @param token the JWT token
     * @return the user ID extracted from the token
     */
    public String getUserIdFromToken(String token) {
        Claims claims = validateAccessToken(token);
        return claims.getSubject();
    }

    /**
     * Get access token expiration time in seconds.
     * @return the access token expiration time in seconds
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }

    /**
     * Revoke access token by adding it to blacklist.
     * @param token the JWT access token to revoke
     * @param reason the reason for revocation (e.g., "logout", "admin-revoked")
     */
    public void revokeAccessToken(String token, String reason) {
        try {
            // Parse token without validation (we still want to revoke expired/invalid tokens)
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            String tokenId = claims.getId();
            String userId = claims.getSubject();
            Date expiration = claims.getExpiration();
            
            if (tokenId != null) {
                // Check if already revoked to avoid duplicates
                if (!revokedAccessTokenRepository.existsByTokenId(tokenId)) {
                    RevokedAccessToken revokedToken = RevokedAccessToken.builder()
                            .tokenId(tokenId)
                            .userId(userId)
                            .revokedAt(LocalDateTime.now())
                            .expiresAt(expiration.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
                            .reason(reason)
                            .build();
                    
                    revokedAccessTokenRepository.save(revokedToken);
                    log.debug("Access token revoked: {} for user: {} (reason: {})", tokenId, userId, reason);
                } else {
                    log.debug("Access token already revoked: {}", tokenId);
                }
            } else {
                log.warn("Cannot revoke token without ID (jti claim missing)");
            }
        } catch (Exception e) {
            log.warn("Failed to revoke access token: {}", e.getMessage());
            // Don't throw exception - logout should still succeed even if token revocation fails
        }
    }

    /**
     * Revoke access token by adding it to blacklist (with default reason).
     * @param token the JWT access token to revoke
     */
    public void revokeAccessToken(String token) {
        revokeAccessToken(token, "logout");
    }

    /**
     * Check if access token is revoked.
     * @param tokenId the JWT token ID (jti claim) to check
     * @return true if token is revoked, false otherwise
     */
    public boolean isAccessTokenRevoked(String tokenId) {
        return revokedAccessTokenRepository.existsByTokenId(tokenId);
    }

    /**
     * Revoke all access tokens for a user (useful for security breaches).
     * Note: This only prevents future validation of tokens that we can identify.
     * Existing tokens without proper jti claims cannot be revoked this way.
     * @param userId the user ID whose tokens should be revoked
     * @param reason the reason for revocation
     */
    public void revokeAllUserAccessTokens(String userId, String reason) {
        // Note: We can only revoke tokens that are already in our system
        // This is a limitation of JWT - we can't revoke tokens we don't know about
        log.info("Revoking all known access tokens for user: {} (reason: {})", userId, reason);
        // Implementation would require tracking all issued tokens, which is complex
        // For now, we rely on individual token revocation during logout
    }

    /**
     * Clean up expired revoked tokens (manual cleanup - MongoDB TTL should handle this automatically).
     */
    public void cleanupExpiredRevokedTokens() {
        LocalDateTime now = LocalDateTime.now();
        revokedAccessTokenRepository.deleteByExpiresAtBefore(now);
        log.debug("Cleaned up expired revoked access tokens");
    }

    /**
     * Delete all tokens (refresh and revoked access tokens metadata) for a user.
     * Useful when permanently deleting or disabling a user account.
     * @param user the user whose tokens should be deleted
     */
    public void deleteAllUserTokens(User user) {
        try {
            // Delete refresh tokens
            refreshTokenRepository.deleteByUser(user);
        } catch (Exception e) {
            log.warn("Failed deleting refresh tokens for user {}: {}", user.getId(), e.getMessage());
        }
        try {
            // Delete revoked access token records to free storage
            revokedAccessTokenRepository.deleteByUserId(user.getId());
        } catch (Exception e) {
            log.warn("Failed deleting revoked access tokens for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
