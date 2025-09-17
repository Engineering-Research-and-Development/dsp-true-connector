package it.eng.connector.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogoutRequest {
    
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
    
    @NotBlank(message = "Access token is required")
    private String accessToken;
}
