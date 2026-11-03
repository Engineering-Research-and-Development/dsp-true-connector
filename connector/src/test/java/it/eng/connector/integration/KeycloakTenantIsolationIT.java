package it.eng.connector.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.DataService;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.AuditEventRepository;
import it.eng.tools.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tenant isolation with two tenant realms trusted at once ({@code tenant-EngineeringA} bound to
 * {@code engineering-a}, {@code tenant-EngineeringB} bound to {@code engineering-b}) and the {@code connector-admin}
 * super-admin realm.
 *
 * <p>Flows covered: tenant admins create and read their own data services through the API and never see the other
 * tenant's; a tenant admin sending {@code X-Tenant-Id} gets 403; a super-admin sees both tenants, or one tenant with
 * the header (one {@code TENANT_DELEGATION} audit event); unknown and disabled tenants get 400; a tampered tenant
 * realm ({@code SUPER_ADMIN} granted there) gets 401 and a {@code SECURITY_REALM_ROLE_VIOLATION} audit event; the
 * super-admin sets realm credentials and lists a tenant's users, a tenant without credentials gets 409.
 *
 * <p>The switch-off ({@code application.keycloak.user-management=false} gives 404) needs its own Spring context and
 * is covered by {@code KeycloakUserManagementDisabledIT}. The negative token matrix lives in
 * {@code KeycloakMultiIssuerApiIT}.
 */
class KeycloakTenantIsolationIT extends BaseKeycloakIntegrationTest {

    private static final String DATA_SERVICES = ApiEndpoints.CATALOG_DATA_SERVICES_V1;
    private static final String CATALOGS = ApiEndpoints.CATALOG_CATALOGS_V1;
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_B_ID = "engineering-b";
    private static final String TENANT_B_REALM = "tenant-EngineeringB";
    private static final String TENANT_A_REALM = "tenant-EngineeringA";
    private static final String UI_CLIENT_ID = "keycloak-api";
    private static final String SECRET_A = "connector-user-reader-secret-a";
    private static final long EVENT_TIMEOUT_SECONDS = 10;
    private static final long EVENT_SETTLE_MILLIS = 1000;

    @Autowired
    private CatalogRepository catalogRepository;
    @Autowired
    private DataServiceRepository dataServiceRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private TenantService tenantService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Runnable> restoreActions = new ArrayList<>();

    @BeforeEach
    void setUpTenantBAndAudit() {
        tenantRepository.deleteById(TENANT_B_ID);
        tenantRepository.save(tenantB(true));
        catalogRepository.deleteAll();
        dataServiceRepository.deleteAll();
        auditEventRepository.deleteAll(auditEvents(AuditEventType.TENANT_DELEGATION));
        auditEventRepository.deleteAll(auditEvents(AuditEventType.SECURITY_REALM_ROLE_VIOLATION));
    }

    @AfterEach
    void cleanUp() {
        for (int i = restoreActions.size() - 1; i >= 0; i--) {
            restoreActions.get(i).run();
        }
        restoreActions.clear();
        dataServiceRepository.deleteAll();
        catalogRepository.deleteAll();
        tenantService.clearRealmCredentials(TENANT_ID);
        tenantRepository.deleteById(TENANT_B_ID);
        auditEventRepository.deleteAll(auditEvents(AuditEventType.TENANT_DELEGATION));
        auditEventRepository.deleteAll(auditEvents(AuditEventType.SECURITY_REALM_ROLE_VIOLATION));
    }

    // --- two tenants ------------------------------------------------------------------------------------

    @Test
    @DisplayName("Tenant A and tenant B admins each create and read a catalog and data service and never see the other's")
    void tenantAdminsSeeOnlyTheirOwnData() throws Exception {
        String tokenA = adminAccessToken();
        String tokenB = tenantBAdminAccessToken();
        String catalogA = createCatalog(tokenA);
        String catalogB = createCatalog(tokenB);
        String serviceA = createDataService(tokenA);
        String serviceB = createDataService(tokenB);

        assertThat(fetch(CATALOGS, tokenA, null)).contains(catalogA).doesNotContain(catalogB);
        assertThat(fetch(CATALOGS, tokenB, null)).contains(catalogB).doesNotContain(catalogA);
        assertThat(fetch(DATA_SERVICES, tokenA, null)).contains(serviceA).doesNotContain(serviceB);
        assertThat(fetch(DATA_SERVICES, tokenB, null)).contains(serviceB).doesNotContain(serviceA);
        assertThat(dataServiceRepository.findById(serviceA).orElseThrow().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(dataServiceRepository.findById(serviceB).orElseThrow().getTenantId()).isEqualTo(TENANT_B_ID);
    }

    @Test
    @DisplayName("A tenant admin sending X-Tenant-Id gets 403, even for its own tenant")
    void tenantAdminWithTenantHeaderIsForbidden() throws Exception {
        mockMvc.perform(get(DATA_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken()))
                        .header(TENANT_HEADER, TENANT_B_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(DATA_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tenantBAdminAccessToken()))
                        .header(TENANT_HEADER, TENANT_B_ID))
                .andExpect(status().isForbidden());
        assertNoAuditEvent(AuditEventType.TENANT_DELEGATION);
    }

    // --- super-admin --------------------------------------------------------------------------------------

    @Test
    @DisplayName("Super-admin sees both tenants without the header and one tenant with it, with one audit event")
    void superAdminSeesBothOrDelegatesToOneTenant() throws Exception {
        String tokenA = adminAccessToken();
        String tokenB = tenantBAdminAccessToken();
        createCatalog(tokenA);
        createCatalog(tokenB);
        String serviceA = createDataService(tokenA);
        String serviceB = createDataService(tokenB);
        String superAdminToken = superAdminAccessToken();

        assertThat(fetch(DATA_SERVICES, superAdminToken, null)).contains(serviceA).contains(serviceB);
        assertNoAuditEvent(AuditEventType.TENANT_DELEGATION);

        assertThat(fetch(DATA_SERVICES, superAdminToken, TENANT_B_ID)).contains(serviceB).doesNotContain(serviceA);

        AuditEvent event = awaitSingleEvent(AuditEventType.TENANT_DELEGATION);
        assertThat(event.getTenantId()).isEqualTo(TENANT_B_ID);
        assertThat(event.getDetails()).containsEntry("targetTenantId", TENANT_B_ID).containsKey("principal");
    }

    @Test
    @DisplayName("Super-admin with an unknown or a disabled tenant gets 400 and no delegation event")
    void superAdminWithUnknownOrDisabledTenantIsBadRequest() throws Exception {
        String superAdminToken = superAdminAccessToken();
        mockMvc.perform(get(DATA_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminToken))
                        .header(TENANT_HEADER, "no-such-tenant"))
                .andExpect(status().isBadRequest());

        Tenant enabled = tenantRepository.findById(TENANT_B_ID).orElseThrow();
        tenantRepository.save(Tenant.Builder.newInstance()
                .id(TENANT_B_ID).name("EngineeringB").participantId("urn:connector:engineering-b")
                .realm(TENANT_B_REALM).enabled(false).version(enabled.getVersion()).build());
        mockMvc.perform(get(DATA_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminToken))
                        .header(TENANT_HEADER, TENANT_B_ID))
                .andExpect(status().isBadRequest());

        assertNoAuditEvent(AuditEventType.TENANT_DELEGATION);
    }

    // --- tampered realm -----------------------------------------------------------------------------------

    @Test
    @DisplayName("SUPER_ADMIN granted in tenant-EngineeringA gets 401 and one SECURITY_REALM_ROLE_VIOLATION event")
    void tamperedTenantRealmIsRejectedAndAudited() throws Exception {
        RealmResource realm = keycloakAdminClient().realm(TENANT_A_REALM);
        UserRepresentation user = realm.users().searchByUsername("admin", true).get(0);
        grantRealmRole(realm, realm.users().get(user.getId()).roles(), "SUPER_ADMIN");

        mockMvc.perform(get(DATA_SERVICES)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken())))
                .andExpect(status().isUnauthorized());

        AuditEvent event = awaitSingleEvent(AuditEventType.SECURITY_REALM_ROLE_VIOLATION);
        assertThat(event.getDetails())
                .containsEntry("realm", TENANT_A_REALM)
                .containsEntry("issuerType", "TENANT")
                .containsEntry("subject", user.getId());
    }

    // --- user management ----------------------------------------------------------------------------------

    @Test
    @DisplayName("Super-admin sets realm credentials for engineering-a and lists its users; without them 409")
    void superAdminManagesRealmCredentialsAndListsUsers() throws Exception {
        String superAdminToken = superAdminAccessToken();
        String credentialsUrl = ApiEndpoints.TENANTS_V1 + "/" + TENANT_ID + "/realm-credentials";

        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminToken))
                        .header(TENANT_HEADER, TENANT_ID))
                .andExpect(status().isConflict());

        mockMvc.perform(put(credentialsUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientSecret\":\"" + SECRET_A + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialsConfigured").value(true));

        MvcResult users = mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminToken))
                        .header(TENANT_HEADER, TENANT_ID))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(users.getResponse().getContentAsString()).get("data");
        List<String> usernames = new ArrayList<>();
        data.forEach(user -> usernames.add(user.get("username").asText()));
        assertThat(usernames).contains("admin").doesNotContain("adminb", "superadmin@mail.com");

        mockMvc.perform(delete(credentialsUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminToken))
                        .header(TENANT_HEADER, TENANT_ID))
                .andExpect(status().isConflict());
    }

    // --- helpers ------------------------------------------------------------------------------------------

    private String createCatalog(String accessToken) throws Exception {
        Catalog catalog = CatalogMockObjectUtil.createNewCatalog(null);
        return submit(CATALOGS, accessToken, CatalogSerializer.serializePlain(catalog));
    }

    private String createDataService(String accessToken) throws Exception {
        DataService dataService = CatalogMockObjectUtil.createNewDataService(null);
        return submit(DATA_SERVICES, accessToken, CatalogSerializer.serializePlain(dataService));
    }

    // Returns the id of the created resource.
    private String submit(String url, String accessToken, String json) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post(url)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("@id").asText();
    }

    private String fetch(String url, String accessToken, String tenantHeader) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get(url)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken));
        if (tenantHeader != null) {
            request.header(TENANT_HEADER, tenantHeader);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // Tampers with a realm the way a realm administrator could: creates the role when missing, adds it to the
    // client's scope (the seeded clients do not use the full scope) and grants it to the user.
    private void grantRealmRole(RealmResource realm, RoleMappingResource userRoles, String roleName) {
        if (realm.roles().list().stream().noneMatch(role -> roleName.equals(role.getName()))) {
            RoleRepresentation newRole = new RoleRepresentation();
            newRole.setName(roleName);
            realm.roles().create(newRole);
            restoreActions.add(() -> realm.roles().deleteRole(roleName));
        }
        List<RoleRepresentation> roles = List.of(realm.roles().get(roleName).toRepresentation());
        String clientUuid = realm.clients().findByClientId(UI_CLIENT_ID).get(0).getId();
        RoleMappingResource clientScope = realm.clients().get(clientUuid).getScopeMappings();
        clientScope.realmLevel().add(roles);
        restoreActions.add(() -> clientScope.realmLevel().remove(roles));
        userRoles.realmLevel().add(roles);
        restoreActions.add(() -> userRoles.realmLevel().remove(roles));
    }

    private AuditEvent awaitSingleEvent(AuditEventType type) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(EVENT_TIMEOUT_SECONDS);
        while (auditEvents(type).isEmpty() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(200);
        }
        // Give a duplicate event time to arrive before counting.
        TimeUnit.MILLISECONDS.sleep(EVENT_SETTLE_MILLIS);
        List<AuditEvent> events = auditEvents(type);
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private void assertNoAuditEvent(AuditEventType type) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(EVENT_SETTLE_MILLIS);
        assertThat(auditEvents(type)).isEmpty();
    }

    private List<AuditEvent> auditEvents(AuditEventType type) {
        return auditEventRepository.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }

    private Tenant tenantB(boolean enabled) {
        return Tenant.Builder.newInstance()
                .id(TENANT_B_ID)
                .name("EngineeringB")
                .participantId("urn:connector:engineering-b")
                .realm(TENANT_B_REALM)
                .enabled(enabled)
                .build();
    }
}
