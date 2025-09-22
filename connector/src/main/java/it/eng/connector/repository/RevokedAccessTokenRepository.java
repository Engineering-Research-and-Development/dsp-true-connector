package it.eng.connector.repository;

import it.eng.connector.model.RevokedAccessToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RevokedAccessTokenRepository extends MongoRepository<RevokedAccessToken, String> {
    
    /**
     * Find revoked token by token ID (jti claim).
     * @param tokenId the JWT token ID to search for
     * @return Optional containing the revoked token if found
     */
    Optional<RevokedAccessToken> findByTokenId(String tokenId);
    
    /**
     * Check if a token ID exists in the revoked tokens collection.
     * @param tokenId the JWT token ID to check
     * @return true if token is revoked, false otherwise
     */
    boolean existsByTokenId(String tokenId);
    
    /**
     * Delete all revoked tokens for a specific user.
     * @param userId the user ID whose revoked tokens should be deleted
     */
    void deleteByUserId(String userId);
    
    /**
     * Delete revoked tokens that have expired (for manual cleanup if needed).
     * MongoDB TTL index should handle this automatically, but this method
     * provides manual cleanup capability.
     * @param dateTime the cutoff date - delete tokens that expired before this
     */
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
    
    /**
     * Count total number of revoked tokens (for monitoring).
     * @return count of revoked tokens
     */
    long count();
}
