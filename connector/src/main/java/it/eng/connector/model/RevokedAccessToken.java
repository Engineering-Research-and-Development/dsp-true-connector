package it.eng.connector.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "revoked_access_tokens")
public class RevokedAccessToken {

    @Id
    private String id;
    
    @Indexed(unique = true)
    private String tokenId; // JWT "jti" claim - unique identifier for the token
    
    private String userId;
    
    private LocalDateTime revokedAt;
    
    @Indexed(expireAfterSeconds = 0) // MongoDB TTL index - auto-delete when expiresAt is reached
    private LocalDateTime expiresAt; // When the original token expires (for auto-cleanup)
    
    private String reason; // Optional: logout, admin-revoked, security-breach, etc.
}
