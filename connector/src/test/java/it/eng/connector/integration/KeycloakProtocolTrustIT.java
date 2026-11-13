package it.eng.connector.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.controller.ApiEndpoints;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the protocol trust boundary of {@code KEYCLOAK} mode.
 *
 * <p>Protocol routes trust the {@code platform} issuer only: the token's {@code aud} must contain
 * {@code dsp-true-connector}, its {@code azp} must be {@code dsp-connector-backend} and its realm roles must
 * hold {@code CONNECTOR} and no other privileged role. Any other token, tenant and super-admin tokens included,
 * gets 401. On {@code /api/**} a valid platform token authenticates but holds {@code ROLE_CONNECTOR} only, so it
 * gets 403.
 *
 * <p>The cases that need a platform token with a single rule broken change the shared Keycloak container
 * through the admin API (an extra client, a removed role or client scope) and restore it afterwards.
 */
class KeycloakProtocolTrustIT extends BaseKeycloakIntegrationTest {

    private static final String PLATFORM_REALM = "platform";
    private static final String PLATFORM_CLIENT_ID = "dsp-connector-backend";
    private static final String OTHER_CLIENT_ID = "protocol-trust-other-client";
    private static final String OTHER_CLIENT_SECRET = "protocol-trust-other-secret";
    private static final String AUDIENCE = "dsp-true-connector";
    private static final String AUDIENCE_SCOPE = "connector-audience";
    private static final String CONNECTOR_ROLE = "CONNECTOR";
    private static final String CATALOG_REQUEST_PATH = "/" + TENANT_ID + "/catalog/request";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Creates a second confidential client in the {@code platform} realm that meets every rule except the
     * authorized party: it has the audience scope and its service account holds {@code CONNECTOR}.
     */
    @BeforeAll
    static void createOtherPlatformClient() {
        RealmResource realm = platformRealm();
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(OTHER_CLIENT_ID);
        client.setSecret(OTHER_CLIENT_SECRET);
        client.setPublicClient(false);
        client.setServiceAccountsEnabled(true);
        client.setStandardFlowEnabled(false);
        client.setDirectAccessGrantsEnabled(false);
        client.setFullScopeAllowed(true);
        try (Response response = realm.clients().create(client)) {
            assertEquals(201, response.getStatus(), "precondition: the extra platform client must be created");
        }
        ClientResource created = clientResource(OTHER_CLIENT_ID);
        created.addDefaultClientScope(audienceScopeId());
        UserRepresentation serviceAccount = created.getServiceAccountUser();
        realm.users().get(serviceAccount.getId()).roles().realmLevel().add(List.of(connectorRole()));
    }

    @AfterAll
    static void deleteOtherPlatformClient() {
        RealmResource realm = platformRealm();
        realm.clients().findByClientId(OTHER_CLIENT_ID)
                .forEach(client -> realm.clients().get(client.getId()).remove());
    }

    @Test
    @DisplayName("Protocol route: a valid platform token passes the security chain")
    void validPlatformTokenPassesProtocolChain() throws Exception {
        String token = connectorAccessToken();
        JWTClaimsSet claims = claims(token);
        assertEquals(realmUrl(PLATFORM_REALM), claims.getIssuer());
        assertEquals(PLATFORM_CLIENT_ID, claims.getStringClaim("azp"));
        assertTrue(claims.getAudience().contains(AUDIENCE));
        assertTrue(realmRoles(claims).contains(CONNECTOR_ROLE));

        assertPassesProtocolChain(token);
    }

    @Test
    @DisplayName("Protocol route: a platform token of another client (wrong azp) returns 401")
    void platformTokenWithOtherAuthorizedPartyReturnsUnauthorized() throws Exception {
        String token = clientCredentialsToken(OTHER_CLIENT_ID, OTHER_CLIENT_SECRET);
        JWTClaimsSet claims = claims(token);
        assertEquals(realmUrl(PLATFORM_REALM), claims.getIssuer());
        assertEquals(OTHER_CLIENT_ID, claims.getStringClaim("azp"), "precondition: azp is the other client");
        assertTrue(claims.getAudience().contains(AUDIENCE), "precondition: token carries the audience");
        assertTrue(realmRoles(claims).contains(CONNECTOR_ROLE), "precondition: token carries CONNECTOR");

        performProtocol(token, status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol route: a platform token without the CONNECTOR role returns 401")
    void platformTokenWithoutConnectorRoleReturnsUnauthorized() throws Exception {
        RealmResource realm = platformRealm();
        String serviceAccountId = clientResource(PLATFORM_CLIENT_ID).getServiceAccountUser().getId();
        RoleRepresentation connector = connectorRole();
        realm.users().get(serviceAccountId).roles().realmLevel().remove(List.of(connector));
        try {
            String token = connectorAccessToken();
            JWTClaimsSet claims = claims(token);
            assertEquals(PLATFORM_CLIENT_ID, claims.getStringClaim("azp"));
            assertTrue(claims.getAudience().contains(AUDIENCE));
            assertFalse(realmRoles(claims).contains(CONNECTOR_ROLE), "precondition: token lacks CONNECTOR");

            performProtocol(token, status().isUnauthorized());
        } finally {
            realm.users().get(serviceAccountId).roles().realmLevel().add(List.of(connector));
        }
        assertPassesProtocolChain(connectorAccessToken());
    }

    @Test
    @DisplayName("Protocol route: a platform token without the dsp-true-connector audience returns 401")
    void platformTokenWithoutAudienceReturnsUnauthorized() throws Exception {
        ClientResource client = clientResource(PLATFORM_CLIENT_ID);
        String scopeId = audienceScopeId();
        client.removeDefaultClientScope(scopeId);
        try {
            String token = connectorAccessToken();
            JWTClaimsSet claims = claims(token);
            assertEquals(PLATFORM_CLIENT_ID, claims.getStringClaim("azp"));
            assertTrue(realmRoles(claims).contains(CONNECTOR_ROLE));
            List<String> audience = claims.getAudience();
            assertTrue(audience == null || !audience.contains(AUDIENCE), "precondition: token lacks the audience");

            performProtocol(token, status().isUnauthorized());
        } finally {
            client.addDefaultClientScope(scopeId);
        }
        assertPassesProtocolChain(connectorAccessToken());
    }

    @Test
    @DisplayName("Protocol route: tenant ADMIN, unbound tenant and super-admin tokens return 401")
    void nonPlatformTokensReturnUnauthorized() throws Exception {
        performProtocol(adminAccessToken(), status().isUnauthorized());
        performProtocol(tenantBAdminAccessToken(), status().isUnauthorized());
        performProtocol(superAdminAccessToken(), status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol route: a platform token re-signed with a foreign key returns 401")
    void resignedPlatformTokenReturnsUnauthorized() throws Exception {
        String original = connectorAccessToken();

        performProtocol(resign(original, claims(original)), status().isUnauthorized());
    }

    @Test
    @DisplayName("Protocol route: a tenant token rewritten to look like a platform token returns 401")
    void tenantTokenForgedAsPlatformTokenReturnsUnauthorized() throws Exception {
        String tenantToken = adminAccessToken();
        JWTClaimsSet forgedClaims = new JWTClaimsSet.Builder(claims(tenantToken))
                .issuer(realmUrl(PLATFORM_REALM))
                .claim("azp", PLATFORM_CLIENT_ID)
                .claim("realm_access", Map.of("roles", List.of(CONNECTOR_ROLE)))
                .build();

        // Re-signed with a foreign key carrying the tenant realm's key id.
        performProtocol(resign(tenantToken, forgedClaims), status().isUnauthorized());
        // Original tenant-realm signature kept over the rewritten claims.
        performProtocol(withOriginalSignature(tenantToken, forgedClaims), status().isUnauthorized());
    }

    @ParameterizedTest(name = "[{index}] GET {0}")
    @ValueSource(strings = {ApiEndpoints.PROPERTIES_V1 + "/", ApiEndpoints.CATALOG_DATASETS_V1,
            ApiEndpoints.NEGOTIATION_V1, ApiEndpoints.USERS_V1 + "/me"})
    @DisplayName("/api: a valid platform token returns 403, with or without an X-Tenant-Id header")
    void platformTokenOnApiReturnsForbidden(String route) throws Exception {
        String token = connectorAccessToken();

        mockMvc.perform(get(route).header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(route)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden());
    }

    /**
     * Grants the platform service account the {@code ADMIN} and {@code SUPER_ADMIN} realm roles. A privileged
     * role of another realm rejects the token as a whole: both the protocol route and {@code /api/**} answer 401.
     * The roles are deleted afterwards, which also removes their user and scope mappings.
     */
    @Test
    @DisplayName("A platform token carrying ADMIN and SUPER_ADMIN returns 401 on protocol and /api routes")
    void platformTokenWithWrongRealmRolesReturnsUnauthorized() throws Exception {
        RealmResource realm = platformRealm();
        List<String> extraRoles = List.of("ADMIN", "SUPER_ADMIN");
        try {
            String serviceAccountId = clientResource(PLATFORM_CLIENT_ID).getServiceAccountUser().getId();
            for (String role : extraRoles) {
                RoleRepresentation newRole = new RoleRepresentation();
                newRole.setName(role);
                realm.roles().create(newRole);
                RoleRepresentation created = realm.roles().get(role).toRepresentation();
                realm.users().get(serviceAccountId).roles().realmLevel().add(List.of(created));
                clientResource(PLATFORM_CLIENT_ID).getScopeMappings().realmLevel().add(List.of(created));
            }

            String token = connectorAccessToken();
            assertTrue(realmRoles(claims(token)).containsAll(List.of("ADMIN", "SUPER_ADMIN", CONNECTOR_ROLE)),
                    "precondition: token carries ADMIN, SUPER_ADMIN and CONNECTOR");

            performProtocol(token, status().isUnauthorized());
            for (String route : List.of(ApiEndpoints.PROPERTIES_V1 + "/", ApiEndpoints.CATALOG_DATASETS_V1,
                    ApiEndpoints.TENANTS_V1)) {
                mockMvc.perform(get(route).header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                        .andExpect(status().isUnauthorized());
            }
        } finally {
            for (String role : extraRoles) {
                if (realm.roles().list().stream().anyMatch(existing -> role.equals(existing.getName()))) {
                    realm.roles().deleteRole(role);
                }
            }
        }
    }

    private void performProtocol(String accessToken, ResultMatcher expectedStatus) throws Exception {
        mockMvc.perform(post(CATALOG_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken))
                        .content(CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(expectedStatus);
    }

    // The catalog itself is not seeded here, so the controller may answer with an error; the security
    // chain must not answer 401 or 403.
    private void assertPassesProtocolChain(String accessToken) throws Exception {
        mockMvc.perform(post(CATALOG_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken))
                        .content(CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertNotEquals(401, status, "the protocol chain rejected a valid platform token");
                    assertNotEquals(403, status, "the protocol chain denied a valid platform token");
                });
    }

    private static RealmResource platformRealm() {
        return keycloakAdminClient().realm(PLATFORM_REALM);
    }

    private static ClientResource clientResource(String clientId) {
        RealmResource realm = platformRealm();
        return realm.clients().get(realm.clients().findByClientId(clientId).get(0).getId());
    }

    private static RoleRepresentation connectorRole() {
        return platformRealm().roles().get(CONNECTOR_ROLE).toRepresentation();
    }

    private static String audienceScopeId() {
        return platformRealm().clientScopes().findAll().stream()
                .filter(scope -> AUDIENCE_SCOPE.equals(scope.getName()))
                .map(ClientScopeRepresentation::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The platform realm has no " + AUDIENCE_SCOPE + " scope"));
    }

    private static String clientCredentialsToken(String clientId, String clientSecret) throws Exception {
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(realmUrl(PLATFORM_REALM) + "/protocol/openid-connect/token"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "precondition: the token request must succeed");
        JsonNode accessToken = OBJECT_MAPPER.readTree(response.body()).get("access_token");
        if (accessToken == null || accessToken.asText().isBlank()) {
            throw new IllegalStateException("Keycloak token response does not contain a usable access_token field.");
        }
        return accessToken.asText();
    }

    private static JWTClaimsSet claims(String token) throws ParseException {
        return SignedJWT.parse(token).getJWTClaimsSet();
    }

    private static List<?> realmRoles(JWTClaimsSet claims) throws ParseException {
        Map<String, Object> realmAccess = claims.getJSONObjectClaim("realm_access");
        return realmAccess != null && realmAccess.get("roles") instanceof List<?> roles ? roles : List.of();
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

    /**
     * Replaces the payload of a signed token and keeps its original header and signature.
     */
    private static String withOriginalSignature(String original, JWTClaimsSet claims) throws ParseException {
        SignedJWT parsed = SignedJWT.parse(original);
        String payload = Base64URL.encode(claims.toString()).toString();
        return parsed.getHeader().toBase64URL() + "." + payload + "." + parsed.getSignature();
    }
}
