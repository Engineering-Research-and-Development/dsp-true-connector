package it.eng.connector.integration.tenant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jayway.jsonpath.JsonPath;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TenantAPIIT extends BaseIntegrationTest {

    private static final String NEW_TENANT_ID = "test-tenant-it";
    private static final String ENGINEERING_TENANT_ID = "engineering";
    private static final String NON_EXISTING_TENANT_ID = "non-existing-tenant-xyz";

    /** Tracks the server-generated UUID from API-created tenants for @AfterEach cleanup. */
    private String generatedTenantId;

    @Value("${application.callback.address:http://localhost:8080/}")
    private String baseCallbackAddress;

    @Autowired
    private TenantRepository tenantRepository;

    @AfterEach
    public void cleanup() {
        tenantRepository.deleteById(NEW_TENANT_ID);
        if (generatedTenantId != null) {
            tenantRepository.deleteById(generatedTenantId);
            generatedTenantId = null;
        }
    }

    private Tenant buildNewTenant() {
        return Tenant.Builder.newInstance()
                .id(NEW_TENANT_ID)
                .name("Test Tenant IT")
                .description("Integration test tenant")
                .connectorId("urn:connector:test-it")
                .callbackAddress("http://localhost:9999")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/tenants as SUPER_ADMIN returns 200 with GenericApiResponse")
    public void getTenantsAsSuperAdmin_returns200() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/v1/tenants as ADMIN returns 403")
    public void getTenants_asAdmin_returns403() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/tenants unauthenticated returns 401")
    public void getTenants_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/tenants/engineering returns the default Engineering tenant")
    public void getEngineeringTenant_exists() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1 + "/" + ENGINEERING_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.id").value("engineering"));
    }

    @Test
    @DisplayName("POST /api/v1/tenants as SUPER_ADMIN creates a tenant and returns 200 with server-generated UUID")
    public void createTenant_asSuperAdmin_returns200() throws Exception {
        Tenant newTenant = Tenant.Builder.newInstance()
                .id(NEW_TENANT_ID)
                .name("Test Tenant IT")
                .description("Integration test tenant")
                .connectorId("urn:connector:test-it")
                .callbackAddress("http://localhost:9999")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .build();

        MvcResult result = mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(newTenant))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.name").value("Test Tenant IT"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String returnedId = JsonPath.read(responseBody, "$.data.id");
        // Server generates a UUID — the caller-supplied id must be ignored
        assertThat(returnedId).isNotEqualTo(NEW_TENANT_ID);
        assertThat(returnedId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        // Track for cleanup
        generatedTenantId = returnedId;
    }

    @Test
    @DisplayName("POST /api/v1/tenants - server ignores caller id and returns a UUID")
    public void createTenant_serverGeneratesUuid_ignoringCallerId() throws Exception {
        Tenant request = Tenant.Builder.newInstance()
                .id("caller-supplied-id-should-be-ignored")
                .name("UUID Generation Test")
                .connectorId("urn:connector:uuid-test")
                .callbackAddress("http://ignored:9999")
                .enabled(true)
                .build();

        MvcResult result = mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String returnedId = JsonPath.read(responseBody, "$.data.id");
        assertThat(returnedId).isNotEqualTo("caller-supplied-id-should-be-ignored");
        assertThat(returnedId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        generatedTenantId = returnedId;
    }

    @Test
    @DisplayName("POST /api/v1/tenants - server derives callbackAddress from base URL and generated UUID")
    public void createTenant_derivesCallbackAddressFromBaseUrl() throws Exception {
        Tenant request = Tenant.Builder.newInstance()
                .id("will-be-replaced")
                .name("CallbackAddress Derivation Test")
                .connectorId("urn:connector:callback-test")
                .callbackAddress("http://ignored-by-server:9999")
                .enabled(true)
                .build();

        MvcResult result = mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.callbackAddress").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String returnedId = JsonPath.read(responseBody, "$.data.id");
        String returnedCallback = JsonPath.read(responseBody, "$.data.callbackAddress");

        String expectedBase = baseCallbackAddress.endsWith("/")
                ? baseCallbackAddress.substring(0, baseCallbackAddress.length() - 1)
                : baseCallbackAddress;
        assertThat(returnedCallback).isEqualTo(expectedBase + "/" + returnedId);
        generatedTenantId = returnedId;
    }

    @Test
    @DisplayName("DELETE /api/v1/tenants/{id} as SUPER_ADMIN removes the tenant")
    public void deleteTenant_asSuperAdmin_returns200() throws Exception {
        Tenant tenantToDelete = Tenant.Builder.newInstance()
                .id(NEW_TENANT_ID)
                .name("Tenant To Delete")
                .connectorId("urn:connector:delete-me")
                .callbackAddress("http://localhost:9998")
                .enabled(true)
                .build();
        tenantRepository.save(tenantToDelete);

        mockMvc.perform(delete(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} updates mutable fields and returns 200")
    public void updateTenant_asSuperAdmin_returns200() throws Exception {
        tenantRepository.save(buildNewTenant());

        Tenant updates = Tenant.Builder.newInstance()
                .id(NEW_TENANT_ID)
                .name("Updated Name")
                .description("Updated description")
                .connectorId("urn:connector:updated")
                .callbackAddress("http://localhost:9998")
                .automaticNegotiation(true)
                .automaticTransfer(false)
                .build();

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(updates))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.description").value("Updated description"))
                .andExpect(jsonPath("$.data.automaticNegotiation").value(true));
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} on non-existing tenant returns 404")
    public void updateTenant_nonExisting_returns404() throws Exception {
        Tenant updates = Tenant.Builder.newInstance()
                .id(NON_EXISTING_TENANT_ID)
                .name("Should Not Update")
                .connectorId("urn:connector:test")
                .callbackAddress("http://localhost:9999")
                .build();

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NON_EXISTING_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(updates))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id}/enable enables tenant and returns enabled=true")
    public void enableTenant_asSuperAdmin_returns200() throws Exception {
        Tenant disabled = Tenant.Builder.newInstance()
                .id(NEW_TENANT_ID)
                .name("Disabled Tenant")
                .connectorId("urn:connector:test-it")
                .callbackAddress("http://localhost:9999")
                .enabled(false)
                .build();
        tenantRepository.save(disabled);

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID + "/enable")
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id}/disable disables tenant and returns enabled=false")
    public void disableTenant_asSuperAdmin_returns200() throws Exception {
        tenantRepository.save(buildNewTenant());

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID + "/disable")
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id}/enable on non-existing tenant returns 404")
    public void enableTenant_nonExisting_returns404() throws Exception {
        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NON_EXISTING_TENANT_ID + "/enable")
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id}/disable on non-existing tenant returns 404")
    public void disableTenant_nonExisting_returns404() throws Exception {
        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NON_EXISTING_TENANT_ID + "/disable")
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("DELETE /api/v1/tenants/{id} on non-existing tenant returns 404")
    public void deleteTenant_nonExisting_returns404() throws Exception {
        mockMvc.perform(delete(ApiEndpoints.TENANTS_V1 + "/" + NON_EXISTING_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("DELETE /api/v1/tenants/{id} then GET returns 404")
    public void deleteTenant_thenGet_returns404() throws Exception {
        tenantRepository.save(buildNewTenant());

        mockMvc.perform(delete(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/v1/tenants/{id} for non-existing tenant returns 404")
    public void getTenantById_nonExisting_returns404() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1 + "/" + NON_EXISTING_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/tenants with missing required fields returns 400 with GenericApiResponse")
    public void createTenant_missingRequiredFields_returns400() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"missing id, name, connectorId and callbackAddress\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<>() {};
        GenericApiResponse<String> response = ToolsSerializer.deserializePlain(
                result.getResponse().getContentAsString(), typeRef);

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertThat(response.getMessage()).isNotBlank();
        assertNull(response.getData());
    }

    @Test
    @DisplayName("POST /api/v1/tenants as ADMIN returns 403")
    public void createTenant_asAdmin_returns403() throws Exception {
        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(buildNewTenant()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/v1/tenants/{id} as ADMIN returns 403")
    public void deleteTenant_asAdmin_returns403() throws Exception {
        mockMvc.perform(delete(ApiEndpoints.TENANTS_V1 + "/" + ENGINEERING_TENANT_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
