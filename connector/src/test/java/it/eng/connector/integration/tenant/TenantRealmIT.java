package it.eng.connector.integration.tenant;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.configuration.TenantIndexInitializer;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.service.FieldEncryptionService;
import it.eng.tools.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;

public class TenantRealmIT extends BaseIntegrationTest {

    private static final String SECRET = "plain-secret-value";

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private FieldEncryptionService fieldEncryptionService;
    @Autowired
    private TenantIndexInitializer tenantIndexInitializer;

    @AfterEach
    void cleanup() {
        tenantRepository.deleteById("realm-it-a");
        tenantRepository.deleteById("realm-it-b");
    }

    private Tenant tenant(String id, String realm) {
        return Tenant.Builder.newInstance()
                .id(id)
                .name(id)
                .participantId("urn:" + id)
                .realm(realm)
                .clientSecret(SECRET)
                .credentialsConfigured(true)
                .build();
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
        Tenant tenant = tenant("realm-it-a", "shared-realm");
        Tenant tenantFromResponse = tenantService.saveTenant(tenant);

        assertNotNull(tenantFromResponse.getId());
        assertNull(tenantFromResponse.getClientSecret());
        assertEquals(tenant.getName(), tenantFromResponse.getName());
        assertEquals(tenant.getParticipantId(), tenantFromResponse.getParticipantId());
        assertEquals(tenant.getRealm(), tenantFromResponse.getRealm());


        Tenant tenantInDb = tenantRepository.findById(tenant.getId()).get();

        assertNotEquals(SECRET, tenantInDb.getClientSecret());
        assertEquals(SECRET, fieldEncryptionService.decrypt(tenantInDb.getClientSecret()));
        assertEquals(SECRET, tenantService.getDecryptedClientSecret("realm-it-a").orElseThrow());
        assertTrue(tenantService.exists("realm-it-a"));
    }
}
