package it.eng.connector.integration;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.controller.ApiEndpoints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "server.port=0")
class SwaggerUiExposureIT extends BaseIntegrationTest {

    @Test
    @DisplayName("OpenAPI groups are available when Swagger is enabled")
    void openApiGroupsAreAvailableWhenSwaggerIsEnabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs/management-api"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/v3/api-docs/dsp-protocol"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Swagger UI is configured to select the separate API groups")
    void swaggerUiConfigListsTheSeparateApiGroups() throws Exception {
        String response = mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode urls = jsonMapper.readTree(response).path("urls");
        Map<String, String> groups = new HashMap<>();
        urls.elements().forEachRemaining(url ->
                groups.put(url.path("name").asText(), url.path("url").asText()));

        assertEquals(Map.of(
                "management-api", "/v3/api-docs/management-api",
                "dsp-protocol", "/v3/api-docs/dsp-protocol"), groups);
    }

    @Test
    @DisplayName("Documented login examples authenticate management and protocol requests")
    void documentedLoginExamplesAuthorizeManagementAndProtocolRequests() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs/management-api"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode managementApi = jsonMapper.readTree(document);
        JsonNode examples = managementApi.path("components").path("examples");
        assertTrue(managementApi.path("paths").has(ApiEndpoints.USERS_V1 + "/me"));
        assertTrue(examples.has("login-admin.plain"));
        assertTrue(examples.has("login-connector.plain"));

        String adminToken = loginAndGetAccessToken(examples.path("login-admin.plain").path("value"));
        String connectorToken = loginAndGetAccessToken(examples.path("login-connector.plain").path("value"));
        mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        String protocolResponse = mockMvc.perform(post("/" + TENANT_ID + "/catalog/request")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + connectorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializeProtocol(
                                CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE)))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals("CatalogError", jsonMapper.readTree(protocolResponse).path("@type").asText());
    }

    private String loginAndGetAccessToken(final JsonNode loginExample) throws Exception {
        String loginResponse = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginExample.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readTree(loginResponse).path("access_token").asText();
    }
}
