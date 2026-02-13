package it.eng.connector.configuration;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import lombok.extern.slf4j.Slf4j;

/**
 * Extracts realm-level roles from a Keycloak JWT and converts them to Spring Security authorities.
 */
@Slf4j
final class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    /**
     * Extracts {@code realm_access.roles} and maps them to {@code ROLE_*} authorities.
     *
     * @param jwt the JWT access token
     * @return the mapped authorities for Spring Security
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        log.debug("Converting JWT to authorities. JWT claims: {}", jwt.getClaims().keySet());

        Object realmAccessClaim = jwt.getClaims().get(REALM_ACCESS_CLAIM);
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            log.warn("No 'realm_access' claim found or it's not a Map. Available claims: {}", jwt.getClaims().keySet());
            return Collections.emptySet();
        }

        log.debug("Found realm_access claim: {}", realmAccess);

        Object rolesClaim = realmAccess.get(ROLES_CLAIM);
        if (!(rolesClaim instanceof Collection<?> roles)) {
            log.warn("No 'roles' found in realm_access or it's not a Collection. realm_access keys: {}", realmAccess.keySet());
            return Collections.emptySet();
        }

        log.debug("Found roles in realm_access: {}", roles);

        Collection<GrantedAuthority> authorities = roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> "ROLE_" + role.toUpperCase(Locale.ROOT))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());

        log.info("Converted Keycloak roles {} to Spring authorities: {}", roles, authorities);
        return authorities;
    }
}
