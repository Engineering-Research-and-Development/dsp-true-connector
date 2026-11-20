package it.eng.connector.integration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultMatcher;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the {@code KEYCLOAK} security chain selects the signing keys per issuer through the
 * trusted-issuer registry.
 *
 * <p>On {@code /api/**} the realm of every enabled tenant, looked up on every request, the
 * {@code connector-admin} realm and the {@code platform} realm are trusted. A platform token holds
 * {@code ROLE_CONNECTOR} only and gets 403. A tenant token must carry the {@code dsp-true-connector} audience and must not hold the
 * {@code SUPER_ADMIN} or {@code CONNECTOR} realm role.
 */
class KeycloakIssuerRegistryIT extends BaseKeycloakIntegrationTest {

    private static final String PROTECTED_ROUTE = ApiEndpoints.NEGOTIATION_V1;
    private static final String TENANT_A_REALM = "tenant-EngineeringA";
    private static final String TENANT_B_ID = "engineering-b";
    private static final String TENANT_B_REALM = "tenant-EngineeringB";
    private static final String AUDIENCE = "dsp-true-connector";
    private static final String TENANT_ADMIN_USERNAME = "admin";
    private static final String UI_CLIENT_ID = "keycloak-api";
    private static final String CLIENT_WITHOUT_AUDIENCE = "admin-cli";

    @BeforeEach
    void removeTenantB() {
        tenantRepository.deleteById(TENANT_B_ID);
    }

    @AfterEach
    void cleanupTenantB() {
        tenantRepository.deleteById(TENANT_B_ID);
    }

    @Test
    @DisplayName("Tenant A admin token reaches a protected /api route")
    void tenantAAdminTokenReturnsOk() throws Exception {
        performProtected(adminAccessToken(), status().isOk());
    }

    @Test
    @DisplayName("Tenant B token is trusted only while an enabled tenant is bound to its realm")
    void tenantBTokenFollowsTenantBinding() throws Exception {
        String tenantBToken = tenantBAdminAccessToken();

        performProtected(tenantBToken, status().isUnauthorized());

        Tenant enabled = tenantRepository.save(tenantB(true, null));
        performProtected(tenantBToken, status().isOk());

        tenantRepository.save(tenantB(false, enabled.getVersion()));
        performProtected(tenantBToken, status().isUnauthorized());
    }

    @Test
    @DisplayName("Platform client-credentials token authenticates on /api but is denied (ROLE_CONNECTOR only)")
    void platformTokenReturnsForbidden() throws Exception {
        performProtected(connectorAccessToken(), status().isForbidden());
    }

    @Test
    @DisplayName("Connector-admin token reaches a protected /api route")
    void connectorAdminTokenReturnsOk() throws Exception {
        performProtected(superAdminAccessToken(), status().isOk());
    }

    @Test
    @DisplayName("Token re-signed with a foreign key but carrying the tenant issuer is rejected")
    void foreignKeyTokenWithTenantIssuerReturnsUnauthorized() throws Exception {
        String original = adminAccessToken();
        String forged = resign(original, claims(original).build());

        performProtected(forged, status().isUnauthorized());
    }

    @Test
    @DisplayName("Token naming a realm that no tenant is bound to is rejected")
    void unknownRealmIssuerReturnsUnauthorized() throws Exception {
        String original = adminAccessToken();
        String forged = resign(original, claims(original).issuer(realmUrl("tenant-Unknown")).build());

        performProtected(forged, status().isUnauthorized());
    }

    @Test
    @DisplayName("Token naming an issuer outside the configured Keycloak is rejected")
    void foreignHostIssuerReturnsUnauthorized() throws Exception {
        String original = adminAccessToken();
        String forged = resign(original,
                claims(original).issuer("https://issuer.invalid/realms/" + TENANT_A_REALM).build());

        performProtected(forged, status().isUnauthorized());
    }

    @Test
    @DisplayName("Tenant token without the dsp-true-connector audience is rejected")
    void tenantTokenWithoutAudienceReturnsUnauthorized() throws Exception {
        String token = adminAccessToken(CLIENT_WITHOUT_AUDIENCE);
        assertEquals(realmUrl(TENANT_A_REALM), claims(token).build().getIssuer());
        List<String> audience = claims(token).build().getAudience();
        assertFalse(audience.contains(AUDIENCE), "precondition: token must not carry the connector audience");

        performProtected(token, status().isUnauthorized());
    }

    /**
     * Grants the tenant admin a realm role that only the platform or the connector-admin realm may grant, maps
     * it into the {@code keycloak-api} client scope (the client has no full scope) and checks that the whole
     * token is rejected. The role is deleted afterwards, which also removes its user and scope mappings.
     */
    @ParameterizedTest(name = "[{index}] role={0}")
    @ValueSource(strings = {"SUPER_ADMIN", "CONNECTOR"})
    @DisplayName("Tenant token carrying a platform-only realm role is rejected as a whole")
    void tenantTokenWithPlatformOnlyRoleReturnsUnauthorized(String role) throws Exception {
        RealmResource realm = keycloakAdminClient().realm(TENANT_A_REALM);
        assertTrue(realm.roles().list().stream().noneMatch(existing -> role.equals(existing.getName())),
                "precondition: the tenant realm must not define " + role);
        RoleRepresentation newRole = new RoleRepresentation();
        newRole.setName(role);
        realm.roles().create(newRole);
        try {
            RoleRepresentation created = realm.roles().get(role).toRepresentation();
            UserRepresentation user = realm.users().searchByUsername(TENANT_ADMIN_USERNAME, true).get(0);
            realm.users().get(user.getId()).roles().realmLevel().add(List.of(created));
            ClientRepresentation client = realm.clients().findByClientId(UI_CLIENT_ID).get(0);
            realm.clients().get(client.getId()).getScopeMappings().realmLevel().add(List.of(created));

            String token = adminAccessToken();
            JWTClaimsSet tokenClaims = claims(token).build();
            assertTrue(tokenClaims.getAudience().contains(AUDIENCE), "precondition: token must carry the audience");
            assertTrue(realmRoles(tokenClaims).contains(role), "precondition: token must carry " + role);
            assertTrue(realmRoles(tokenClaims).contains("ADMIN"), "precondition: token must carry ADMIN");

            performProtected(token, status().isUnauthorized());
        } finally {
            realm.roles().deleteRole(role);
        }
        performProtected(adminAccessToken(), status().isOk());
    }

    private static List<?> realmRoles(JWTClaimsSet claims) throws ParseException {
        Map<String, Object> realmAccess = claims.getJSONObjectClaim("realm_access");
        return realmAccess != null && realmAccess.get("roles") instanceof List<?> roles ? roles : List.of();
    }

    private void performProtected(String accessToken, ResultMatcher expectedStatus) throws Exception {
        mockMvc.perform(get(PROTECTED_ROUTE)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken)))
                .andExpect(expectedStatus);
    }

    private Tenant tenantB(boolean enabled, Long version) {
        return Tenant.Builder.newInstance()
                .id(TENANT_B_ID)
                .name("EngineeringB")
                .participantId("urn:connector:engineering-b")
                .realm(TENANT_B_REALM)
                .enabled(enabled)
                .version(version)
                .build();
    }

    private static JWTClaimsSet.Builder claims(String token) throws ParseException {
        return new JWTClaimsSet.Builder(SignedJWT.parse(token).getJWTClaimsSet());
    }

    /**
     * Signs the claims with a freshly generated key, keeping the original header's key id so that the
     * connector looks for the issuer's real key.
     */
    private static String resign(String original, JWTClaimsSet claims) throws ParseException, JOSEException {
        JWSHeader originalHeader = SignedJWT.parse(original).getHeader();
        RSAKey foreignKey = new RSAKeyGenerator(2048).keyID(originalHeader.getKeyID()).generate();
        SignedJWT forged = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(originalHeader.getType())
                .keyID(originalHeader.getKeyID())
                .build(), claims);
        forged.sign(new RSASSASigner(foreignKey));
        return forged.serialize();
    }
}
