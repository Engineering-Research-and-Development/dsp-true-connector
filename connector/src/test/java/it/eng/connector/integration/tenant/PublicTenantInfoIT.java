package it.eng.connector.integration.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.model.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PublicTenantInfoIT extends BaseIntegrationTest {

    private final String enabledTenantId = UUID.randomUUID().toString();
    private final String disabledTenantId = UUID.randomUUID().toString();
    private final String legacyTenantId = UUID.randomUUID().toString();
    private final String enabledTenantName = "Public Tenant IT " + UUID.randomUUID();
    private final String disabledTenantName = "Disabled Tenant IT " + UUID.randomUUID();
    private final String legacyTenantName = "Legacy Tenant IT " + UUID.randomUUID();
    private final String enabledTenantRealm = "tenant_" + enabledTenantName;

    @AfterEach
    void cleanUpTestTenants() {
        tenantRepository.deleteById(enabledTenantId);
        tenantRepository.deleteById(disabledTenantId);
        tenantRepository.deleteById(legacyTenantId);
    }

    @Test
    @DisplayName("Anonymous public tenant request returns only enabled tenants with name and realm")
    void anonymousRequestReturnsEnabledPublicTenantInfo() throws Exception {
        tenantRepository.save(buildTenant(enabledTenantId, enabledTenantName,
                "urn:tenant:" + UUID.randomUUID(), enabledTenantRealm, true));
        tenantRepository.save(buildTenant(disabledTenantId, disabledTenantName,
                "urn:tenant:" + UUID.randomUUID(), "tenant_disabled", false));
        tenantRepository.save(buildTenant(legacyTenantId, legacyTenantName,
                "urn:tenant:" + UUID.randomUUID(), null, true));

        String responseBody = mockMvc.perform(get("/public/tenant"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = jsonMapper.readTree(responseBody);

        assertTrue(response.isArray(), "Public tenant information must be a JSON array");
        for (JsonNode tenantInfo : response) {
            Set<String> fields = new HashSet<>();
            tenantInfo.fieldNames().forEachRemaining(fields::add);
            assertEquals(Set.of("name", "realm"), fields,
                    "Each public tenant object must expose only name and realm");
        }

        JsonNode enabledTenant = findTenantByName(response, enabledTenantName)
                .orElseThrow(() -> new AssertionError("Enabled test tenant was not returned"));
        assertEquals(enabledTenantName, enabledTenant.path("name").asText());
        assertEquals(enabledTenantRealm, enabledTenant.path("realm").asText());
        assertTrue(findTenantByName(response, disabledTenantName).isEmpty(),
                "Disabled tenants must not be returned");

        JsonNode legacyTenant = findTenantByName(response, legacyTenantName)
                .orElseThrow(() -> new AssertionError("Legacy test tenant was not returned"));
        assertNotNull(legacyTenant.get("realm"), "Legacy realm must be included in the response");
        assertTrue(legacyTenant.get("realm").isNull(), "A null legacy realm must be serialized as JSON null");
    }

    private Tenant buildTenant(String id, String name, String participantId, String realm, boolean enabled) {
        return Tenant.Builder.newInstance()
                .id(id)
                .name(name)
                .participantId(participantId)
                .realm(realm)
                .enabled(enabled)
                .build();
    }

    private Optional<JsonNode> findTenantByName(JsonNode tenants, String name) {
        for (JsonNode tenant : tenants) {
            if (name.equals(tenant.path("name").asText())) {
                return Optional.of(tenant);
            }
        }
        return Optional.empty();
    }
}
