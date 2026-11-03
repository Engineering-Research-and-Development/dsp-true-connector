package it.eng.connector.integration;

import com.auth0.jwt.JWT;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.repository.AuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that a tampered realm cannot grant a privileged role of another realm: the token gets 401 and one
 * {@link AuditEventType#SECURITY_REALM_ROLE_VIOLATION} audit event is written.
 *
 * <p>Each test adds a privileged realm role to a seeded user of the shared Keycloak container through the
 * Keycloak admin API and restores the realm afterwards.
 */
class KeycloakRealmRoleViolationIT extends BaseKeycloakIntegrationTest {

    private static final String TENANT_REALM = "tenant-EngineeringA";
    private static final String TENANT_ADMIN_USERNAME = "admin";
    private static final String UI_CLIENT_ID = "keycloak-api";
    private static final String PLATFORM_REALM = "platform";
    private static final String PLATFORM_CLIENT_ID = "dsp-connector-backend";
    private static final long EVENT_TIMEOUT_SECONDS = 10;
    private static final long EVENT_SETTLE_MILLIS = 1000;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private final List<Runnable> restoreActions = new ArrayList<>();

    @BeforeEach
    void removeViolationEvents() {
        auditEventRepository.deleteAll(violationEvents());
    }

    @AfterEach
    void restoreRealms() {
        for (int i = restoreActions.size() - 1; i >= 0; i--) {
            restoreActions.get(i).run();
        }
        restoreActions.clear();
        auditEventRepository.deleteAll(violationEvents());
    }

    @Test
    @DisplayName("A tenant token carrying SUPER_ADMIN gets 401 on /api/** and writes one audit event")
    void tenantTokenWithSuperAdminIsRejectedAndAudited() throws Exception {
        RealmResource realm = keycloakAdminClient().realm(TENANT_REALM);
        UserRepresentation user = realm.users().searchByUsername(TENANT_ADMIN_USERNAME, true).get(0);
        grantRealmRole(realm, UI_CLIENT_ID, realm.users().get(user.getId()).roles(), "SUPER_ADMIN");

        String token = adminAccessToken();
        assertThat(JWT.decode(token).getClaim("realm_access").asMap().get("roles").toString())
                .contains("SUPER_ADMIN");

        mockMvc.perform(get(ApiEndpoints.TENANTS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        AuditEvent event = awaitSingleViolationEvent();
        assertThat(event.getDetails())
                .containsEntry("realm", TENANT_REALM)
                .containsEntry("issuerType", "TENANT")
                .containsEntry("subject", user.getId())
                .containsEntry("path", ApiEndpoints.TENANTS_V1)
                .hasSize(4);
        assertThat(event.getUsername()).isNull();
    }

    @Test
    @DisplayName("A platform token carrying ADMIN gets 401 on a protocol route and writes one audit event")
    void platformTokenWithAdminIsRejectedAndAudited() throws Exception {
        RealmResource realm = keycloakAdminClient().realm(PLATFORM_REALM);
        ClientRepresentation client = realm.clients().findByClientId(PLATFORM_CLIENT_ID).get(0);
        UserRepresentation serviceAccount = realm.clients().get(client.getId()).getServiceAccountUser();
        grantRealmRole(realm, PLATFORM_CLIENT_ID, realm.users().get(serviceAccount.getId()).roles(), "ADMIN");

        String token = connectorAccessToken();
        assertThat(JWT.decode(token).getClaim("realm_access").asMap().get("roles").toString()).contains("ADMIN");

        mockMvc.perform(post("/" + TENANT_ID + "/catalog/request")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        AuditEvent event = awaitSingleViolationEvent();
        assertThat(event.getDetails())
                .containsEntry("realm", PLATFORM_REALM)
                .containsEntry("issuerType", "PLATFORM")
                .containsEntry("subject", serviceAccount.getId());
    }

    @Test
    @DisplayName("An ordinary invalid token writes no realm role violation event")
    void otherRejectionsAreNotAudited() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken("admin-cli")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        TimeUnit.MILLISECONDS.sleep(EVENT_SETTLE_MILLIS);
        assertThat(violationEvents()).isEmpty();
    }

    // Tampers with a realm the way a realm administrator could: creates the role when missing, adds it to the
    // client's scope (the seeded clients do not use the full scope) and grants it to the user.
    private void grantRealmRole(RealmResource realm, String clientId, RoleMappingResource userRoles, String roleName) {
        boolean created = realm.roles().list().stream().noneMatch(role -> roleName.equals(role.getName()));
        if (created) {
            RoleRepresentation newRole = new RoleRepresentation();
            newRole.setName(roleName);
            realm.roles().create(newRole);
            restoreActions.add(() -> realm.roles().deleteRole(roleName));
        }
        List<RoleRepresentation> roles = List.of(realm.roles().get(roleName).toRepresentation());
        String clientUuid = realm.clients().findByClientId(clientId).get(0).getId();
        RoleMappingResource clientScope = realm.clients().get(clientUuid).getScopeMappings();
        clientScope.realmLevel().add(roles);
        restoreActions.add(() -> clientScope.realmLevel().remove(roles));
        userRoles.realmLevel().add(roles);
        restoreActions.add(() -> userRoles.realmLevel().remove(roles));
    }

    private AuditEvent awaitSingleViolationEvent() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(EVENT_TIMEOUT_SECONDS);
        while (violationEvents().isEmpty() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(200);
        }
        // Give a duplicate event time to arrive before counting.
        TimeUnit.MILLISECONDS.sleep(EVENT_SETTLE_MILLIS);
        List<AuditEvent> events = violationEvents();
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private List<AuditEvent> violationEvents() {
        return auditEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuditEventType.SECURITY_REALM_ROLE_VIOLATION)
                .toList();
    }
}
