package it.eng.connector.configuration;

import it.eng.connector.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.Principal;

/**
 * Principal object representing an authenticated API user.
 * Contains user information extracted from JWT token.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiUserPrincipal implements Principal {
    
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;

    @Override
    public String getName() {
        return email;
    }
}
