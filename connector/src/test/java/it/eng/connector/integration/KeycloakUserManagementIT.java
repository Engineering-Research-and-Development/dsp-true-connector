package it.eng.connector.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Tenant;
import it.eng.tools.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the Keycloak user listing: a super-admin names a tenant with {@code X-Tenant-Id} and gets the users of
 * that tenant's realm only, read with the realm's own {@code connector-user-reader} client and the secret stored on
 * the tenant.
 */
class KeycloakUserManagementIT extends BaseKeycloakIntegrationTest {

    private static final String TENANT_B_ID = "engineering-b";
    private static final String TENANT_B_REALM = "tenant-EngineeringB";
    private static final String SECRET_A = "connector-user-reader-secret-a";
    private static final String SECRET_B = "connector-user-reader-secret-b";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Autowired
    private TenantService tenantService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpTenants() {
        tenantRepository.deleteById(TENANT_B_ID);
        tenantRepository.save(Tenant.Builder.newInstance()
                .id(TENANT_B_ID)
                .name("EngineeringB")
                .participantId("urn:connector:engineering-b")
                .realm(TENANT_B_REALM)
                .enabled(true)
                .build());
        tenantService.setRealmCredentials(TENANT_ID, SECRET_A);
        tenantService.setRealmCredentials(TENANT_B_ID, SECRET_B);
    }

    @AfterEach
    void cleanUp() {
        tenantService.clearRealmCredentials(TENANT_ID);
        tenantRepository.deleteById(TENANT_B_ID);
    }

    @Test
    @DisplayName("Super-admin with X-Tenant-Id engineering-a lists the users of tenant-EngineeringA only")
    void listsTenantAUsers() throws Exception {
        List<String> usernames = usernames(superAdminAccessToken(), TENANT_ID);

        assertTrue(usernames.contains("admin"));
        assertFalse(usernames.contains("adminb"));
        assertFalse(usernames.contains("superadmin@mail.com"));
    }

    @Test
    @DisplayName("Super-admin with X-Tenant-Id engineering-b lists the users of tenant-EngineeringB only")
    void listsTenantBUsers() throws Exception {
        List<String> usernames = usernames(superAdminAccessToken(), TENANT_B_ID);

        assertTrue(usernames.contains("adminb"));
        assertFalse(usernames.contains("admin"));
        assertFalse(usernames.contains("superadmin@mail.com"));
    }

    @Test
    @DisplayName("An unknown e-mail filter returns an empty list")
    void emailFilter() throws Exception {
        mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/nobody@example.org")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminAccessToken()))
                        .header(TENANT_HEADER, TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("Super-admin without X-Tenant-Id gets 400")
    void noHeaderBadRequest() throws Exception {
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminAccessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("X-Tenant-Id required"));
    }

    @Test
    @DisplayName("Tenant without stored realm secret gets 409")
    void noSecretConflict() throws Exception {
        tenantService.clearRealmCredentials(TENANT_B_ID);

        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminAccessToken()))
                        .header(TENANT_HEADER, TENANT_B_ID))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Tenant admin gets 403 on the user list, with and without X-Tenant-Id")
    void tenantAdminForbidden() throws Exception {
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken()))
                        .header(TENANT_HEADER, TENANT_B_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/me as tenant admin gets 403")
    void usersMeForbiddenForTenantAdmin() throws Exception {
        mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken())))
                .andExpect(status().isForbidden());
    }

    private List<String> usernames(String accessToken, String tenantId) throws Exception {
        MvcResult result = mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken))
                        .header(TENANT_HEADER, tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(true, data.isArray());
        List<String> usernames = new ArrayList<>();
        data.forEach(user -> usernames.add(user.get("username").asText()));
        return usernames;
    }
}
