package it.eng.tools.auth.keycloak.issuer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class IssuerTokenValidatorsTest {

    private static final String AUDIENCE = "dsp-true-connector";
    private static final String PLATFORM_CLIENT_ID = "dsp-connector-backend";
    private static final String UI_CLIENT_ID = "keycloak-api";
    private static final String TENANT_ID = "tenant-a";

    private static final TrustedIssuer TENANT = new TrustedIssuer("http://kc/realms/tenant-A", "tenant-A",
            IssuerType.TENANT, TENANT_ID);
    private static final TrustedIssuer PLATFORM = new TrustedIssuer("http://kc/realms/platform", "platform",
            IssuerType.PLATFORM, null);
    private static final TrustedIssuer ADMIN = new TrustedIssuer("http://kc/realms/connector-admin",
            "connector-admin", IssuerType.ADMIN, null);

    private final IssuerTokenValidators validators =
            new IssuerTokenValidators(AUDIENCE, PLATFORM_CLIENT_ID, UI_CLIENT_ID);

    // ---------------------------------------------------------------- valid tokens

    @Test
    @DisplayName("A tenant token with audience, UI azp, ADMIN and the bound tenant_id is accepted")
    void validTenantTokenIsAccepted() {
        Jwt jwt = jwt(List.of("account", AUDIENCE), tenantClaims());

        assertValid(validators.forIssuer(TENANT).validate(jwt));
    }

    @Test
    @DisplayName("A connector-admin token with audience, UI azp, SUPER_ADMIN and no tenant_id is accepted")
    void validAdminTokenIsAccepted() {
        Jwt jwt = jwt(List.of("account", AUDIENCE), adminClaims());

        assertValid(validators.forIssuer(ADMIN).validate(jwt));
    }

    @Test
    @DisplayName("A platform token with audience, platform azp, CONNECTOR and no tenant_id is accepted")
    void validPlatformTokenIsAccepted() {
        Jwt jwt = jwt(List.of("account", AUDIENCE), platformClaims());

        assertValid(validators.forIssuer(PLATFORM).validate(jwt));
    }

    @Test
    @DisplayName("Non-privileged roles never cause a rejection, for any issuer type")
    void nonPrivilegedRolesAreIgnored() {
        List<String> extra = List.of("offline_access", "uma_authorization", "default-roles-tenant-a", "user");

        assertValid(validators.forIssuer(TENANT).validate(jwt(List.of(AUDIENCE), withRoles(tenantClaims(),
                concat(extra, "ADMIN")))));
        assertValid(validators.forIssuer(ADMIN).validate(jwt(List.of(AUDIENCE), withRoles(adminClaims(),
                concat(extra, "SUPER_ADMIN")))));
        assertValid(validators.forIssuer(PLATFORM).validate(jwt(List.of(AUDIENCE), withRoles(platformClaims(),
                concat(extra, "CONNECTOR")))));
    }

    @Test
    @DisplayName("A tenant token without ADMIN and an admin token without SUPER_ADMIN are still valid")
    void humanTokensWithoutOwnRoleAreValid() {
        assertValid(validators.forIssuer(TENANT).validate(jwt(List.of(AUDIENCE),
                withRoles(tenantClaims(), List.of("offline_access")))));
        assertValid(validators.forIssuer(ADMIN).validate(jwt(List.of(AUDIENCE),
                withRoles(adminClaims(), List.of()))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "Admin"})
    @DisplayName("A tenant token's own role is matched case-insensitively and is not a violation")
    void tenantOwnRoleInOtherCaseIsAccepted(String role) {
        assertValid(validators.forIssuer(TENANT).validate(jwt(List.of(AUDIENCE),
                withRoles(tenantClaims(), List.of(role)))));
    }

    @Test
    @DisplayName("A tenant or admin token without a usable realm_access.roles claim passes the role rule")
    void missingOrMalformedRolesClaimPassesRoleRule() {
        List<Object> realmAccessValues = List.of("SUPER_ADMIN", Map.of(), Map.of("roles", "SUPER_ADMIN"),
                Map.of("roles", List.of(1, Map.of("name", "CONNECTOR"))));

        for (Object realmAccess : realmAccessValues) {
            Map<String, Object> tenant = tenantClaims();
            tenant.put("realm_access", realmAccess);
            Map<String, Object> admin = adminClaims();
            admin.put("realm_access", realmAccess);
            assertValid(validators.forIssuer(TENANT).validate(jwt(List.of(AUDIENCE), tenant)));
            assertValid(validators.forIssuer(ADMIN).validate(jwt(List.of(AUDIENCE), admin)));
        }
    }

    // ---------------------------------------------------------------- aud

    @Test
    @DisplayName("A token whose audience is missing or different is rejected for every issuer type")
    void missingAudienceIsRejected() {
        Map<TrustedIssuer, Map<String, Object>> validClaims = Map.of(TENANT, tenantClaims(), ADMIN, adminClaims(),
                PLATFORM, platformClaims());

        validClaims.forEach((issuer, claims) -> {
            assertInvalid(validators.forIssuer(issuer).validate(jwt(null, claims)));
            assertInvalid(validators.forIssuer(issuer).validate(jwt(List.of("account"), claims)));
        });
    }

    // ---------------------------------------------------------------- azp

    static Stream<Arguments> wrongAuthorizedParties() {
        return Stream.of(
                Arguments.of(TENANT, PLATFORM_CLIENT_ID),
                Arguments.of(TENANT, "admin-cli"),
                Arguments.of(TENANT, "KEYCLOAK-API"),
                Arguments.of(TENANT, ""),
                Arguments.of(ADMIN, PLATFORM_CLIENT_ID),
                Arguments.of(ADMIN, "admin-cli"),
                Arguments.of(ADMIN, "keycloak-api "),
                Arguments.of(PLATFORM, UI_CLIENT_ID),
                Arguments.of(PLATFORM, "other-client"),
                Arguments.of(PLATFORM, "DSP-CONNECTOR-BACKEND"),
                Arguments.of(PLATFORM, "dsp-connector-backend "),
                Arguments.of(PLATFORM, ""));
    }

    @ParameterizedTest(name = "[{index}] {0} azp={1}")
    @MethodSource("wrongAuthorizedParties")
    @DisplayName("A token whose azp is not the client of its issuer type is rejected")
    void wrongAuthorizedPartyIsRejected(TrustedIssuer issuer, String azp) {
        Map<String, Object> claims = validClaims(issuer);
        claims.put("azp", azp);

        assertInvalid(validators.forIssuer(issuer).validate(jwt(List.of(AUDIENCE), claims)));
    }

    @Test
    @DisplayName("A token without an azp claim, or with a non-string azp, is rejected for every issuer type")
    void missingOrNonStringAuthorizedPartyIsRejected() {
        for (TrustedIssuer issuer : List.of(TENANT, ADMIN, PLATFORM)) {
            Map<String, Object> missing = validClaims(issuer);
            missing.remove("azp");
            Map<String, Object> nonString = validClaims(issuer);
            nonString.put("azp", List.of(nonString.get("azp")));

            assertInvalid(validators.forIssuer(issuer).validate(jwt(List.of(AUDIENCE), missing)));
            assertInvalid(validators.forIssuer(issuer).validate(jwt(List.of(AUDIENCE), nonString)));
        }
    }

    // ---------------------------------------------------------------- wrong-realm privileged roles

    static Stream<Arguments> wrongRealmRoles() {
        return Stream.of(
                Arguments.of(TENANT, "SUPER_ADMIN"),
                Arguments.of(TENANT, "super_admin"),
                Arguments.of(TENANT, "CONNECTOR"),
                Arguments.of(TENANT, "connector"),
                Arguments.of(TENANT, "INTERNAL"),
                Arguments.of(TENANT, "Internal"),
                Arguments.of(ADMIN, "ADMIN"),
                Arguments.of(ADMIN, "admin"),
                Arguments.of(ADMIN, "CONNECTOR"),
                Arguments.of(ADMIN, "INTERNAL"),
                Arguments.of(ADMIN, "internal"),
                Arguments.of(PLATFORM, "ADMIN"),
                Arguments.of(PLATFORM, "Admin"),
                Arguments.of(PLATFORM, "SUPER_ADMIN"),
                Arguments.of(PLATFORM, "INTERNAL"),
                Arguments.of(PLATFORM, "internal"));
    }

    @ParameterizedTest(name = "[{index}] {0} role={1}")
    @MethodSource("wrongRealmRoles")
    @DisplayName("A token carrying another realm's privileged role is rejected with the realm role violation code")
    void wrongRealmPrivilegedRoleIsRejected(TrustedIssuer issuer, String role) {
        Map<String, Object> claims = validClaims(issuer);
        claims.put("realm_access", Map.of("roles", List.of(ownRole(issuer), role)));

        OAuth2TokenValidatorResult result = validators.forIssuer(issuer).validate(jwt(List.of(AUDIENCE), claims));

        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size(), "only the role rule fails");
        assertEquals(IssuerTokenValidators.REALM_ROLE_VIOLATION, result.getErrors().iterator().next().getErrorCode());
    }

    @Test
    @DisplayName("A role violation is the first error even when other rules fail too")
    void roleViolationIsFirstError() {
        Map<String, Object> claims = tenantClaims();
        claims.put("azp", "admin-cli");
        claims.put("realm_access", Map.of("roles", List.of("SUPER_ADMIN")));

        OAuth2TokenValidatorResult result = validators.forIssuer(TENANT).validate(jwt(List.of("account"), claims));

        List<String> codes = result.getErrors().stream().map(OAuth2Error::getErrorCode).toList();
        assertEquals(IssuerTokenValidators.REALM_ROLE_VIOLATION, codes.get(0));
        assertTrue(codes.subList(1, codes.size()).stream().allMatch(OAuth2ErrorCodes.INVALID_TOKEN::equals));
    }

    @Test
    @DisplayName("The realm role violation code is distinct from invalid_token")
    void roleViolationCodeIsDistinct() {
        assertFalse(OAuth2ErrorCodes.INVALID_TOKEN.equals(IssuerTokenValidators.REALM_ROLE_VIOLATION));
    }

    // ---------------------------------------------------------------- platform CONNECTOR

    @Test
    @DisplayName("A platform token without the CONNECTOR realm role (exact match) is rejected as invalid_token")
    void platformTokenWithoutConnectorRoleIsRejected() {
        List<Object> realmAccessValues = List.of("CONNECTOR", Map.of(), Map.of("roles", "CONNECTOR"),
                Map.of("roles", List.of("default-roles-platform")), Map.of("roles", List.of("connector")));

        for (Object realmAccess : realmAccessValues) {
            Map<String, Object> claims = platformClaims();
            claims.put("realm_access", realmAccess);
            assertInvalid(validators.forIssuer(PLATFORM).validate(jwt(List.of(AUDIENCE), claims)));
        }
        Map<String, Object> noRealmAccess = platformClaims();
        noRealmAccess.remove("realm_access");
        assertInvalid(validators.forIssuer(PLATFORM).validate(jwt(List.of(AUDIENCE), noRealmAccess)));
    }

    // ---------------------------------------------------------------- tenant_id

    @ParameterizedTest
    @ValueSource(strings = {"", "tenant-b", "TENANT-A", "tenant-a "})
    @DisplayName("A tenant token whose tenant_id is empty or differs from the bound tenant is rejected")
    void tenantTokenWithOtherTenantIdIsRejected(String tenantId) {
        Map<String, Object> claims = tenantClaims();
        claims.put("tenant_id", tenantId);

        assertInvalid(validators.forIssuer(TENANT).validate(jwt(List.of(AUDIENCE), claims)));
    }

    @Test
    @DisplayName("A tenant token without tenant_id, or with a non-string tenant_id, is rejected")
    void tenantTokenWithoutUsableTenantIdIsRejected() {
        Map<String, Object> missing = tenantClaims();
        missing.remove("tenant_id");
        Map<String, Object> list = tenantClaims();
        list.put("tenant_id", List.of(TENANT_ID));

        assertInvalid(validators.forIssuer(TENANT).validate(jwt(List.of(AUDIENCE), missing)));
        assertInvalid(validators.forIssuer(TENANT).validate(jwt(List.of(AUDIENCE), list)));
    }

    @Test
    @DisplayName("A tenant token is checked against the tenant bound to its own issuer")
    void tenantIdIsCheckedPerIssuer() {
        TrustedIssuer tenantB = new TrustedIssuer("http://kc/realms/tenant-B", "tenant-B", IssuerType.TENANT,
                "tenant-b");
        Jwt tenantAToken = jwt(List.of(AUDIENCE), tenantClaims());

        assertValid(validators.forIssuer(TENANT).validate(tenantAToken));
        assertInvalid(validators.forIssuer(tenantB).validate(tenantAToken));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("nonTenantIssuers")
    @DisplayName("An admin or platform token carrying tenant_id is rejected; an empty tenant_id counts as absent")
    void nonTenantTokenWithTenantIdIsRejected(TrustedIssuer issuer) {
        for (Object tenantId : List.of(TENANT_ID, " ", List.of(TENANT_ID), 1)) {
            Map<String, Object> claims = validClaims(issuer);
            claims.put("tenant_id", tenantId);
            assertInvalid(validators.forIssuer(issuer).validate(jwt(List.of(AUDIENCE), claims)));
        }
        Map<String, Object> empty = validClaims(issuer);
        empty.put("tenant_id", "");
        assertValid(validators.forIssuer(issuer).validate(jwt(List.of(AUDIENCE), empty)));
    }

    static Stream<TrustedIssuer> nonTenantIssuers() {
        return Stream.of(ADMIN, PLATFORM);
    }

    // ---------------------------------------------------------------- descriptions and construction

    @Test
    @DisplayName("Error descriptions name the rule and never the claim values")
    void errorDescriptionsDoNotLeakClaimValues() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("azp", "leaky-client");
        claims.put("tenant_id", "leaky-tenant");
        claims.put("realm_access", Map.of("roles", List.of("SUPER_ADMIN", "leaky-role")));

        for (TrustedIssuer issuer : List.of(TENANT, ADMIN, PLATFORM)) {
            OAuth2TokenValidatorResult result = validators.forIssuer(issuer)
                    .validate(jwt(List.of("leaky-audience"), claims));
            assertTrue(result.hasErrors());
            for (OAuth2Error error : result.getErrors()) {
                assertFalse(error.getDescription().contains("leaky"), error.getDescription());
                assertFalse(error.getDescription().contains("SUPER_ADMIN"), error.getDescription());
            }
        }
    }

    @Test
    @DisplayName("A blank or null audience, platform client id or UI client id is refused at construction")
    void blankArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new IssuerTokenValidators(" ", PLATFORM_CLIENT_ID, UI_CLIENT_ID));
        assertThrows(NullPointerException.class,
                () -> new IssuerTokenValidators(null, PLATFORM_CLIENT_ID, UI_CLIENT_ID));
        assertThrows(IllegalArgumentException.class, () -> new IssuerTokenValidators(AUDIENCE, " ", UI_CLIENT_ID));
        assertThrows(NullPointerException.class, () -> new IssuerTokenValidators(AUDIENCE, null, UI_CLIENT_ID));
        assertThrows(IllegalArgumentException.class,
                () -> new IssuerTokenValidators(AUDIENCE, PLATFORM_CLIENT_ID, ""));
        assertThrows(NullPointerException.class,
                () -> new IssuerTokenValidators(AUDIENCE, PLATFORM_CLIENT_ID, null));
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, Object> tenantClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("azp", UI_CLIENT_ID);
        claims.put("tenant_id", TENANT_ID);
        claims.put("realm_access", Map.of("roles", List.of("default-roles-tenant-a", "ADMIN")));
        return claims;
    }

    private static Map<String, Object> adminClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("azp", UI_CLIENT_ID);
        claims.put("realm_access", Map.of("roles", List.of("default-roles-connector-admin", "SUPER_ADMIN")));
        return claims;
    }

    private static Map<String, Object> platformClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("azp", PLATFORM_CLIENT_ID);
        claims.put("realm_access", Map.of("roles", List.of("default-roles-platform", "CONNECTOR")));
        return claims;
    }

    private static Map<String, Object> validClaims(TrustedIssuer issuer) {
        return switch (issuer.type()) {
            case TENANT -> tenantClaims();
            case ADMIN -> adminClaims();
            case PLATFORM -> platformClaims();
        };
    }

    private static String ownRole(TrustedIssuer issuer) {
        return switch (issuer.type()) {
            case TENANT -> "ADMIN";
            case ADMIN -> "SUPER_ADMIN";
            case PLATFORM -> "CONNECTOR";
        };
    }

    private static Map<String, Object> withRoles(Map<String, Object> claims, List<String> roles) {
        claims.put("realm_access", Map.of("roles", roles));
        return claims;
    }

    private static List<String> concat(List<String> roles, String role) {
        return Stream.concat(roles.stream(), Stream.of(role)).toList();
    }

    private static void assertValid(OAuth2TokenValidatorResult result) {
        assertFalse(result.hasErrors(), () -> "rejected: " + result.getErrors());
    }

    private static void assertInvalid(OAuth2TokenValidatorResult result) {
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream()
                .allMatch(error -> OAuth2ErrorCodes.INVALID_TOKEN.equals(error.getErrorCode())),
                () -> "unexpected error codes: " + result.getErrors());
    }

    private static Jwt jwt(List<String> audience, Map<String, Object> claims) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claims(c -> c.putAll(claims));
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }
}
