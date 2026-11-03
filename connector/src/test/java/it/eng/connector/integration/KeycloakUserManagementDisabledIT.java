package it.eng.connector.integration;

import it.eng.tools.controller.ApiEndpoints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code application.keycloak.user-management=false} removes the Keycloak user API.
 */
@TestPropertySource(properties = "application.keycloak.user-management=false")
class KeycloakUserManagementDisabledIT extends BaseKeycloakIntegrationTest {

    @Test
    @DisplayName("Users API answers 404 for a super-admin when user management is switched off")
    void usersApiIsGone() throws Exception {
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminAccessToken()))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isNotFound());
    }
}
