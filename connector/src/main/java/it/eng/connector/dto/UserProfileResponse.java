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
public class UserProfileResponse {
    
    private String userId;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private boolean enabled;
}
