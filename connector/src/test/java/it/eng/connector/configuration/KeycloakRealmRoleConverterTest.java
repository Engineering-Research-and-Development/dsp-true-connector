package it.eng.connector.configuration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    @Test
    void convertsRealmRolesToAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("admin", "connector")))
                .build();

        KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertTrue(authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_CONNECTOR")));
    }
}
