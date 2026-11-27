package it.eng.connector.configuration;

import it.eng.connector.model.Role;
import it.eng.tools.auth.internal.InternalCallTokenIssuer;
import it.eng.tools.auth.keycloak.KeycloakTrustProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class KeycloakRealmRoleConverterTest {

    private static final String PLATFORM_ISSUER = "http://kc/realms/platform";
    private static final String ADMIN_ISSUER = "http://kc/realms/connector-admin";
    private static final String TENANT_ISSUER = "http://kc/realms/tenant-EngineeringA";

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter(classifier());

    @Test
    @DisplayName("A tenant token with ADMIN yields ROLE_ADMIN only, other roles are dropped")
    void tenantTokenYieldsAdminOnly() {
        Jwt jwt = jwt(TENANT_ISSUER, roles("ADMIN", "default-roles-x", "offline_access", "USER", "AUDITOR"));

        assertEquals(Set.of(Role.ADMIN.authorityName()), authorities(jwt));
    }

    @Test
    @DisplayName("A connector-admin token with SUPER_ADMIN yields ROLE_SUPER_ADMIN only, other roles are dropped")
    void adminTokenYieldsSuperAdminOnly() {
        Jwt jwt = jwt(ADMIN_ISSUER, roles("SUPER_ADMIN", "default-roles-connector-admin", "USER"));

        assertEquals(Set.of(Role.SUPER_ADMIN.authorityName()), authorities(jwt));
    }

    @Test
    @DisplayName("A platform token with CONNECTOR yields ROLE_CONNECTOR only, other roles are dropped")
    void platformTokenYieldsConnectorOnly() {
        Jwt jwt = jwt(PLATFORM_ISSUER, roles("CONNECTOR", "ADMIN", "SUPER_ADMIN", "INTERNAL", "offline_access"));

        assertEquals(Set.of(Role.CONNECTOR.authorityName()), authorities(jwt));
    }

    @Test
    @DisplayName("A privileged role of another realm never maps to an authority")
    void otherRealmsPrivilegedRolesAreDropped() {
        assertEquals(Set.of(Role.ADMIN.authorityName()),
                authorities(jwt(TENANT_ISSUER, roles("ADMIN", "SUPER_ADMIN", "CONNECTOR", "INTERNAL"))));
        assertEquals(Set.of(Role.SUPER_ADMIN.authorityName()),
                authorities(jwt(ADMIN_ISSUER, roles("SUPER_ADMIN", "ADMIN", "CONNECTOR", "INTERNAL"))));
        assertTrue(authorities(jwt(TENANT_ISSUER, roles("SUPER_ADMIN", "CONNECTOR"))).isEmpty());
        assertTrue(authorities(jwt(ADMIN_ISSUER, roles("ADMIN", "CONNECTOR"))).isEmpty());
    }

    @ParameterizedTest(name = "issuer {0}")
    @ValueSource(strings = {TENANT_ISSUER, ADMIN_ISSUER, PLATFORM_ISSUER})
    @DisplayName("A token without its realm's role yields no authority")
    void tokenWithoutItsRealmRoleYieldsNothing(String issuer) {
        assertTrue(authorities(jwt(issuer, roles("offline_access", "uma_authorization"))).isEmpty());
        assertTrue(authorities(jwt(issuer, Map.of())).isEmpty());
    }

    @Test
    @DisplayName("Role names match exactly, as seeded")
    void roleNamesMatchExactly() {
        assertTrue(authorities(jwt(TENANT_ISSUER, roles("admin", "Admin"))).isEmpty());
        assertTrue(authorities(jwt(ADMIN_ISSUER, roles("super_admin"))).isEmpty());
        assertTrue(authorities(jwt(PLATFORM_ISSUER, roles("connector"))).isEmpty());
    }

    @Test
    @DisplayName("No Keycloak token yields ROLE_INTERNAL or a generic ROLE_ authority")
    void noGenericOrInternalAuthority() {
        for (String issuer : List.of(TENANT_ISSUER, ADMIN_ISSUER, PLATFORM_ISSUER)) {
            Set<String> authorities = authorities(jwt(issuer, roles("INTERNAL", "internal", "USER", "OPERATOR")));

            assertTrue(authorities.isEmpty(), () -> issuer + " mapped " + authorities);
        }
    }

    @Test
    @DisplayName("Role.INTERNAL has the authority the internal-call token grants")
    void internalRoleMatchesInternalCallToken() {
        assertEquals(InternalCallTokenIssuer.ROLE_INTERNAL, Role.INTERNAL.authorityName());
    }

    @Test
    @DisplayName("Missing or malformed realm_access.roles yields no authority")
    void malformedRolesClaimYieldsNothing() {
        List<Map<String, Object>> claimSets = List.of(
                Map.of(),
                Map.of("realm_access", "ADMIN"),
                Map.of("realm_access", Map.of("roles", "ADMIN")),
                Map.of("realm_access", Map.of("roles", List.of(1, Map.of("name", "ADMIN")))));

        for (Map<String, Object> claims : claimSets) {
            assertTrue(converter.convert(jwt(TENANT_ISSUER, claims)).isEmpty(), () -> "mapped " + claims);
        }
    }

    @Test
    @DisplayName("A token without iss yields no authority")
    void tokenWithoutIssuerYieldsNothing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .build();

        assertTrue(converter.convert(jwt).isEmpty());
    }

    @Test
    @DisplayName("The issuer classifier is required")
    void issuerClassifierIsRequired() {
        assertThrows(NullPointerException.class, () -> new KeycloakRealmRoleConverter(null));
    }

    static KeycloakIssuerClassifier classifier() {
        KeycloakTrustProperties properties = new KeycloakTrustProperties();
        properties.setBaseUrl("http://kc");
        return new KeycloakIssuerClassifier(properties);
    }

    private Set<String> authorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static Map<String, Object> roles(String... roles) {
        return Map.of("realm_access", Map.of("roles", List.of(roles)));
    }

    private static Jwt jwt(String issuer, Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer(issuer)
                .claims(c -> c.putAll(claims))
                .build();
    }
}
