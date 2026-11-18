package it.eng.connector.configuration;

import it.eng.connector.model.Role;
import it.eng.tools.auth.keycloak.issuer.IssuerType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

import java.util.*;

/**
 * Converts the realm roles of a Keycloak JWT into Spring Security authorities through a per-realm allow-list.
 *
 * <p>The authority depends on the type of the token's issuer, not on the role name alone. Each issuer type
 * may grant exactly one role:
 * <ul>
 *   <li>a tenant realm yields {@code ROLE_ADMIN} when {@code realm_access.roles} holds {@code ADMIN};</li>
 *   <li>the {@code connector-admin} realm yields {@code ROLE_SUPER_ADMIN} when it holds {@code SUPER_ADMIN};</li>
 *   <li>the {@code platform} realm yields {@code ROLE_CONNECTOR} when it holds {@code CONNECTOR}.</li>
 * </ul>
 * Role names match exactly, as seeded. Every other role is dropped: there is no generic {@code ROLE_<X>}
 * mapping, so a realm administrator cannot grant a connector authority by creating a role. The decoder's
 * per-issuer validators already reject a token that carries another realm's privileged role; this converter
 * keeps the allow-list should a decoder ever be configured without those checks.
 *
 * <p>The issuer type comes from the verified {@code iss}, see {@link KeycloakIssuerClassifier}. No Keycloak
 * token ever yields {@code ROLE_INTERNAL}: only the connector's own internal-call token carries it.
 */
@Slf4j
final class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    private final KeycloakIssuerClassifier issuerClassifier;

    /**
     * Creates the converter.
     *
     * @param issuerClassifier tells the issuer type of a verified token
     * @throws NullPointerException when {@code issuerClassifier} is {@code null}
     */
    KeycloakRealmRoleConverter(KeycloakIssuerClassifier issuerClassifier) {
        this.issuerClassifier = Objects.requireNonNull(issuerClassifier, "issuerClassifier");
    }

    /**
     * Maps {@code realm_access.roles} to the one authority the token's issuer type may grant.
     *
     * @param jwt the verified JWT access token
     * @return the authority of the issuer type when the token holds its role, otherwise an empty set; never
     *         {@code null}
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Optional<IssuerType> issuerType = issuerClassifier.typeOf(jwt.getClaimAsString(JwtClaimNames.ISS));
        if (issuerType.isEmpty()) {
            log.debug("Token has no issuer: no authority");
            return Set.of();
        }
        Role allowedRole = allowedRole(issuerType.get());
        Set<GrantedAuthority> authorities = holdsRealmRole(jwt, allowedRole.name())
                ? Set.of(new SimpleGrantedAuthority(allowedRole.authorityName()))
                : Set.of();
        log.debug("Token of a {} issuer mapped to authorities {}", issuerType.get(), authorities);
        return authorities;
    }

    private static Role allowedRole(IssuerType issuerType) {
        return switch (issuerType) {
            case TENANT -> Role.ADMIN;
            case ADMIN -> Role.SUPER_ADMIN;
            case PLATFORM -> Role.CONNECTOR;
        };
    }

    private static boolean holdsRealmRole(Jwt jwt, String role) {
        return jwt.getClaims().get(REALM_ACCESS_CLAIM) instanceof Map<?, ?> realmAccess
                && realmAccess.get(ROLES_CLAIM) instanceof Collection<?> roles
                && roles.contains(role);
    }
}
