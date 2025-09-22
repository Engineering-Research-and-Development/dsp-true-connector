package it.eng.connector.dto;

import it.eng.connector.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresIn; // in seconds
    
    // User information
    private String userId;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    
    // Optional message for registration status
    private String message;
}
