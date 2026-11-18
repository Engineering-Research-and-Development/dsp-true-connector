package it.eng.connector.integration;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.catalog.model.DataService;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.auth.keycloak.KeycloakDiscoveryClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Tenant;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Negative token matrix on {@code /api/**} with two tenant realms trusted at once ({@code tenant-EngineeringA}
 * bound to tenant {@code engineering-a}, {@code tenant-EngineeringB} bound to {@code engineering-b}).
 *
 * <p>Every variant token comes from the real Keycloak container. Realm changes (extra client, removed client
 * scope, user attribute, claim mapper) go through the Keycloak admin API and are undone in {@link #restoreRealms()}.
 * Tenant {@code engineering-b} and the per-tenant data services are inserted in the test setup and removed
 * afterwards, so the class can run repeatedly against the same containers.
 *
 * <p>Where each matrix row lives:
 * <ul>
 *   <li>tenant isolation, unknown issuer, wrong {@code aud}, wrong {@code azp}, missing or foreign
 *       {@code tenant_id}, tampered signature, roles on admin and protocol routes, connector-admin with a
 *       {@code tenant_id}: this class;</li>
 *   <li>tenant token carrying {@code SUPER_ADMIN} (401 and audit event): {@code KeycloakRealmRoleViolationIT};</li>
 *   <li>foreign-key signature, expired {@code exp}, future {@code nbf} (Keycloak cannot issue them):
 *       {@code TenantTokenMatrixTest} in the tools module, plus the tampered-token cases here.</li>
 * </ul>
 */
class KeycloakMultiIssuerApiIT extends BaseKeycloakIntegrationTest {

    private static final String ADMIN_ROUTE = ApiEndpoints.CATALOG_DATA_SERVICES_V1;
    private static final String SUPER_ADMIN_ROUTE = ApiEndpoints.TENANTS_V1;
    private static final String PROTOCOL_ROUTE = "/" + TENANT_ID + "/catalog/request";
    private static final String TENANT_A_REALM = "tenant-EngineeringA";
    private static final String TENANT_B_ID = "engineering-b";
    private static final String TENANT_B_REALM = "tenant-EngineeringB";
    private static final String ADMIN_REALM = "connector-admin";
    private static final String TENANT_ADMIN_USERNAME = "admin";
    private static final String UI_CLIENT_ID = "keycloak-api";
    private static final String OTHER_UI_CLIENT_ID = "t57-other-ui";
    private static final String AUDIENCE_SCOPE = "connector-audience";
    private static final String TENANT_ID_ATTRIBUTE = "tenant_id";
    private static final String ENDPOINT_A = "http://dataservice-a.t57.example";
    private static final String ENDPOINT_B = "http://dataservice-b.t57.example";

    @MockitoSpyBean
    private KeycloakDiscoveryClient discoveryClient;

    @Autowired
    private DataServiceRepository dataServiceRepository;

    private final List<Runnable> restoreActions = new ArrayList<>();
    private DataService dataServiceA;
    private DataService dataServiceB;

    @BeforeEach
    void bindTenantBAndSeedData() {
        tenantRepository.deleteById(TENANT_B_ID);
        tenantRepository.save(tenantB(true, null));
        dataServiceA = dataServiceRepository.save(
                CatalogMockObjectUtil.createNewDataService(TENANT_ID).injectEndpointURL(ENDPOINT_A));
        dataServiceB = dataServiceRepository.save(
                CatalogMockObjectUtil.createNewDataService(TENANT_B_ID).injectEndpointURL(ENDPOINT_B));
        Mockito.clearInvocations(discoveryClient);
    }

    @AfterEach
    void cleanUp() {
        for (int i = restoreActions.size() - 1; i >= 0; i--) {
            restoreActions.get(i).run();
        }
        restoreActions.clear();
        dataServiceRepository.deleteById(dataServiceA.getId());
        dataServiceRepository.deleteById(dataServiceB.getId());
        tenantRepository.deleteById(TENANT_B_ID);
    }

    // --- tenant binding ---------------------------------------------------------------------------------

    @Test
    @DisplayName("Each tenant admin token authenticates as its own tenant and sees only that tenant's data")
    void eachTenantTokenSeesOnlyItsOwnData() throws Exception {
        String bodyA = perform(adminAccessToken(), status().isOk());
        assertTrue(bodyA.contains(ENDPOINT_A));
        assertFalse(bodyA.contains(ENDPOINT_B));

        String bodyB = perform(tenantBAdminAccessToken(), status().isOk());
        assertTrue(bodyB.contains(ENDPOINT_B));
        assertFalse(bodyB.contains(ENDPOINT_A));
    }

    @Test
    @DisplayName("A realm without an enabled tenant is rejected with no outbound request for its issuer")
    void unregisteredIssuerIsRejectedWithoutOutboundRequest() throws Exception {
        String tenantBToken = tenantBAdminAccessToken();
        perform(tenantBToken, status().isOk());

        Tenant enabled = tenantRepository.findById(TENANT_B_ID).orElseThrow();
        tenantRepository.save(tenantB(false, enabled.getVersion()));
        Mockito.clearInvocations(discoveryClient);
        perform(tenantBToken, status().isUnauthorized());

        tenantRepository.deleteById(TENANT_B_ID);
        perform(tenantBToken, status().isUnauthorized());

        verify(discoveryClient, never()).fetch(anyString());
        // Tenant A is unaffected.
        perform(adminAccessToken(), status().isOk());
    }

    // --- claims of a real token -------------------------------------------------------------------------

    @Test
    @DisplayName("Tenant token with a wrong aud (right azp and tenant_id) is rejected")
    void wrongAudienceIsRejected() throws Exception {
        RealmResource realm = keycloakAdminClient().realm(TENANT_A_REALM);
        ClientResource client = uiClient(realm);
        String scopeId = clientScopeId(realm, AUDIENCE_SCOPE);
        client.removeDefaultClientScope(scopeId);
        restoreActions.add(() -> client.addDefaultClientScope(scopeId));

        String token = adminAccessToken();
        assertFalse(claims(token).getAudience().contains("dsp-true-connector"), "precondition: no connector audience");
        assertEquals(UI_CLIENT_ID, claims(token).getStringClaim("azp"));
        assertEquals(TENANT_ID, claims(token).getStringClaim(TENANT_ID_ATTRIBUTE));
        perform(token, status().isUnauthorized());

        restoreAll();
        perform(adminAccessToken(), status().isOk());
    }

    @Test
    @DisplayName("Tenant token with a wrong azp (right aud, role and tenant_id) is rejected")
    void wrongAuthorizedPartyIsRejected() throws Exception {
        RealmResource realm = keycloakAdminClient().realm(TENANT_A_REALM);
        ClientRepresentation representation = new ClientRepresentation();
        representation.setClientId(OTHER_UI_CLIENT_ID);
        representation.setPublicClient(true);
        representation.setDirectAccessGrantsEnabled(true);
        representation.setFullScopeAllowed(true);
        representation.setDefaultClientScopes(List.of("roles", "profile", "basic", "org", AUDIENCE_SCOPE));
        String clientUuid;
        try (Response response = realm.clients().create(representation)) {
            clientUuid = CreatedResponseUtil.getCreatedId(response);
        }
        restoreActions.add(() -> realm.clients().get(clientUuid).remove());

        String token = adminAccessToken(OTHER_UI_CLIENT_ID);
        JWTClaimsSet tokenClaims = claims(token);
        assertEquals(OTHER_UI_CLIENT_ID, tokenClaims.getStringClaim("azp"));
        assertTrue(tokenClaims.getAudience().contains("dsp-true-connector"), "precondition: audience present");
        assertEquals(TENANT_ID, tokenClaims.getStringClaim(TENANT_ID_ATTRIBUTE));
        assertTrue(tokenClaims.getJSONObjectClaim("realm_access").get("roles").toString().contains("ADMIN"));
        perform(token, status().isUnauthorized());

        restoreAll();
        perform(adminAccessToken(), status().isOk());
    }

    @Test
    @DisplayName("Tenant token without tenant_id is rejected")
    void missingTenantIdIsRejected() throws Exception {
        setTenantIdAttribute(null);

        String token = adminAccessToken();
        assertTrue(claims(token).getClaim(TENANT_ID_ATTRIBUTE) == null, "precondition: no tenant_id claim");
        perform(token, status().isUnauthorized());

        restoreAll();
        perform(adminAccessToken(), status().isOk());
    }

    @Test
    @DisplayName("Tenant A token carrying the tenant_id of tenant B is rejected, although tenant B is trusted")
    void otherTenantIdIsRejected() throws Exception {
        setTenantIdAttribute(TENANT_B_ID);

        String token = adminAccessToken();
        assertEquals(TENANT_B_ID, claims(token).getStringClaim(TENANT_ID_ATTRIBUTE));
        assertEquals(realmUrl(TENANT_A_REALM), claims(token).getIssuer());
        perform(token, status().isUnauthorized());

        restoreAll();
        perform(adminAccessToken(), status().isOk());
    }

    @Test
    @DisplayName("Connector-admin token carrying a tenant_id is rejected")
    void connectorAdminTokenWithTenantIdIsRejected() throws Exception {
        RealmResource realm = keycloakAdminClient().realm(ADMIN_REALM);
        ClientResource client = uiClient(realm);
        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName("t57-tenant-id");
        mapper.setProtocol("openid-connect");
        mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
        Map<String, String> config = new HashMap<>();
        config.put("claim.name", TENANT_ID_ATTRIBUTE);
        config.put("claim.value", TENANT_ID);
        config.put("jsonType.label", "String");
        config.put("access.token.claim", "true");
        config.put("id.token.claim", "false");
        mapper.setConfig(config);
        try (Response response = client.getProtocolMappers().createMapper(mapper)) {
            String mapperId = CreatedResponseUtil.getCreatedId(response);
            restoreActions.add(() -> client.getProtocolMappers().delete(mapperId));
        }

        String token = superAdminAccessToken();
        assertEquals(TENANT_ID, claims(token).getStringClaim(TENANT_ID_ATTRIBUTE));
        assertEquals(realmUrl(ADMIN_REALM), claims(token).getIssuer());
        perform(token, status().isUnauthorized());

        restoreAll();
        perform(superAdminAccessToken(), status().isOk());
    }

    // --- signature --------------------------------------------------------------------------------------

    @Test
    @DisplayName("A real token whose payload was changed after signing is rejected (bad signature)")
    void tamperedPayloadIsRejected() throws Exception {
        String token = adminAccessToken();

        perform(tamper(token, builder -> builder.claim(TENANT_ID_ATTRIBUTE, TENANT_B_ID)), status().isUnauthorized());
        perform(tamper(token, builder -> builder.expirationTime(
                new java.util.Date(System.currentTimeMillis() + 86_400_000L))), status().isUnauthorized());
        perform(tamper(token, builder -> builder.issuer(realmUrl(TENANT_B_REALM))), status().isUnauthorized());
        perform(token, status().isOk());
    }

    // --- roles ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Tenant admin: 200 on a ROLE_ADMIN route, 403 on a SUPER_ADMIN-only route, 401 on a protocol route")
    void tenantAdminRoleMatrix() throws Exception {
        String token = adminAccessToken();
        String tokenB = tenantBAdminAccessToken();

        for (String candidate : List.of(token, tokenB)) {
            perform(candidate, status().isOk());
            mockMvc.perform(get(SUPER_ADMIN_ROUTE).header(HttpHeaders.AUTHORIZATION, bearerToken(candidate)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(PROTOCOL_ROUTE)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken(candidate))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        assertNotEquals(token, tokenB);
    }

    // --- helpers ----------------------------------------------------------------------------------------

    private String perform(String accessToken, ResultMatcher expectedStatus) throws Exception {
        return mockMvc.perform(get(ADMIN_ROUTE).header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken)))
                .andExpect(expectedStatus)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void restoreAll() {
        for (int i = restoreActions.size() - 1; i >= 0; i--) {
            restoreActions.get(i).run();
        }
        restoreActions.clear();
    }

    private static ClientResource uiClient(RealmResource realm) {
        return realm.clients().get(realm.clients().findByClientId(UI_CLIENT_ID).get(0).getId());
    }

    private static String clientScopeId(RealmResource realm, String name) {
        return realm.clientScopes().findAll().stream()
                .filter(scope -> name.equals(scope.getName()))
                .map(ClientScopeRepresentation::getId)
                .findFirst().orElseThrow();
    }

    /** Sets the {@code tenant_id} attribute of the tenant A admin, or removes it for {@code null}. */
    private void setTenantIdAttribute(String value) {
        RealmResource realm = keycloakAdminClient().realm(TENANT_A_REALM);
        UserResource user = realm.users().get(realm.users().searchByUsername(TENANT_ADMIN_USERNAME, true)
                .get(0).getId());
        UserRepresentation representation = user.toRepresentation();
        Map<String, List<String>> original = representation.getAttributes() == null
                ? new HashMap<>() : new HashMap<>(representation.getAttributes());
        assertEquals(List.of(TENANT_ID), original.get(TENANT_ID_ATTRIBUTE), "precondition: seeded tenant_id");
        updateAttribute(user, value == null ? null : List.of(value));
        restoreActions.add(() -> updateAttribute(user, original.get(TENANT_ID_ATTRIBUTE)));
    }

    private static void updateAttribute(UserResource user, List<String> value) {
        UserRepresentation representation = user.toRepresentation();
        Map<String, List<String>> attributes = representation.getAttributes() == null
                ? new HashMap<>() : new HashMap<>(representation.getAttributes());
        if (value == null) {
            attributes.remove(TENANT_ID_ATTRIBUTE);
        } else {
            attributes.put(TENANT_ID_ATTRIBUTE, value);
        }
        representation.setAttributes(attributes);
        user.update(representation);
    }

    private static JWTClaimsSet claims(String token) throws ParseException {
        return SignedJWT.parse(token).getJWTClaimsSet();
    }

    /** Replaces the payload and keeps header and signature, so the signature no longer matches. */
    private static String tamper(String token, UnaryOperator<JWTClaimsSet.Builder> change) throws ParseException {
        String[] parts = token.split("\\.");
        JWTClaimsSet changed = change.apply(new JWTClaimsSet.Builder(claims(token))).build();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(changed.toString().getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + payload + "." + parts[2];
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
}
