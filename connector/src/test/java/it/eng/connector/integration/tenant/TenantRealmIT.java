package it.eng.connector.integration.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.auth.keycloak.realm.RealmCredentialsService;
import it.eng.tools.configuration.TenantIndexInitializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.data.mongodb.core.MongoTemplate;

public class TenantRealmIT extends BaseIntegrationTest {

    private static final String SECRET = "plain-secret-value";

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private RealmCredentialsService realmCredentialsService;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private TenantIndexInitializer tenantIndexInitializer;

    @AfterEach
    void cleanup() {
        tenantRepository.deleteById("realm-it-a");
        tenantRepository.deleteById("realm-it-b");
        realmCredentialsService.delete("realm-it-a");
    }

    private Tenant tenant(String id, String realm) {
        return Tenant.Builder.newInstance().id(id).name(id).participantId("urn:" + id).realm(realm).build();
    }

    @Test
    @DisplayName("Unique realm index rejects a second tenant with the same realm but tolerates null realms")
    void realmIndexEnforcesUniqueness() {
        tenantIndexInitializer.ensureIndexes();
        tenantRepository.save(tenant("realm-it-a", "shared-realm"));
        assertThrows(DuplicateKeyException.class, () -> tenantRepository.save(tenant("realm-it-b", "shared-realm")));
        tenantRepository.save(tenant("realm-it-b", null));
    }

    @Test
    @DisplayName("Realm secret is persisted encrypted")
    void secretStoredAsCiphertext() {
        realmCredentialsService.save("realm-it-a", SECRET);

        Document raw = mongoTemplate.getCollection("realm_credentials")
                .find(new Document("_id", "realm-it-a")).first();

        assertNotNull(raw);
        assertNotEquals(SECRET, raw.getString("clientSecret"));
        assertFalse(raw.toJson().contains(SECRET));
        assertEquals(SECRET, realmCredentialsService.getDecrypted("realm-it-a").orElseThrow());
        assertTrue(realmCredentialsService.exists("realm-it-a"));
    }

    @Test
    @DisplayName("Tenant admins get 403 on the realm-credentials endpoint")
    void tenantAdminForbidden() throws Exception {
        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/engineering" + ApiEndpoints.TENANT_REALM_CREDENTIALS)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientSecret\":\"" + SECRET + "\"}"))
                .andExpect(status().isForbidden());
    }
}
