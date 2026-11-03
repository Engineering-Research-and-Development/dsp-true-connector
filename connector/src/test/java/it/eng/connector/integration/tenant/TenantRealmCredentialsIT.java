package it.eng.connector.integration.tenant;

import it.eng.connector.integration.BaseKeycloakIntegrationTest;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Tenant;
import it.eng.tools.service.TenantService;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the realm credentials endpoint: the secret is checked against the tenant's realm, stored
 * encrypted, and restricted to super-admins.
 *
 * <p>Uses the {@code connector-user-reader} seed client of {@code tenant-EngineeringA} (confidential, client
 * credentials, realm-management {@code view-users} only) with its demo secret. Super-admin calls use a real
 * {@code connector-admin} token.
 * */
class TenantRealmCredentialsIT extends BaseKeycloakIntegrationTest {

    private static final String CLIENT_SECRET = "connector-user-reader-secret-a";
    private static final String URL = ApiEndpoints.TENANTS_V1 + "/" + TENANT_ID + "/realm-credentials";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TenantService tenantService;

    @AfterEach
    void clearCredentials() {
        if (tenantRepository.findById(TENANT_ID).map(Tenant::isCredentialsConfigured).orElse(false)) {
            tenantService.clearRealmCredentials(TENANT_ID);
        }
    }

    @Test
    @DisplayName("Valid secret is accepted and stored encrypted, then removed again")
    void validSecretStoredEncrypted() throws Exception {
        mockMvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientSecret\":\"" + CLIENT_SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialsConfigured").value(true))
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist());

        Document raw = mongoTemplate.getCollection("tenants").find(new Document("_id", TENANT_ID)).first();
        assertNotNull(raw);
        assertFalse(raw.toJson().contains(CLIENT_SECRET), "the raw Mongo document must not hold the plaintext");
        assertTrue(raw.containsKey("clientSecret"));
        assertEquals(CLIENT_SECRET, tenantService.userManagementSecret(TENANT_ID).orElseThrow());

        mockMvc.perform(delete(URL).header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialsConfigured").value(false));
        assertTrue(tenantService.userManagementSecret(TENANT_ID).isEmpty());
    }

    @Test
    @DisplayName("Wrong secret is rejected with 400 and nothing is stored")
    void wrongSecretRejected() throws Exception {
        mockMvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientSecret\":\"not-the-secret\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(tenantService.userManagementSecret(TENANT_ID).isEmpty());
        Document raw = mongoTemplate.getCollection("tenants").find(new Document("_id", TENANT_ID)).first();
        assertNotNull(raw);
        assertFalse(raw.containsKey("clientSecret") && raw.get("clientSecret") != null);
    }

    @Test
    @DisplayName("Tenant admin gets 403")
    void tenantAdminForbidden() throws Exception {
        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientSecret\":\"" + CLIENT_SECRET + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(URL).header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken())))
                .andExpect(status().isForbidden());
    }
}
