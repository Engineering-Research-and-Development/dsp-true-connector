package it.eng.connector.integration.internal;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.tools.auth.internal.InternalCallTokenIssuer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.repository.ArtifactRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /internal/**} chain in {@code INTERNAL} mode, plus the {@code /api} routes moved or kept by the
 * internal zone.
 */
class InternalCallSecurityIT extends BaseIntegrationTest implements InternalChainSecurityContract {

    @Autowired
    private InternalCallTokenIssuer internalCallTokenIssuer;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DistributionRepository distributionRepository;
    @Autowired
    private DataServiceRepository dataServiceRepository;
    @Autowired
    private ArtifactRepository artifactRepository;

    private InternalChainFixtures fixtures;
    private Dataset dataset;

    @BeforeEach
    void storeDataset() {
        fixtures = new InternalChainFixtures(datasetRepository, distributionRepository, dataServiceRepository,
                artifactRepository);
        fixtures.clear();
        dataset = fixtures.persistDataset(TENANT_ID);
    }

    @AfterEach
    void removeDataset() {
        fixtures.clear();
    }

    @Override
    public MockMvc internalChainMockMvc() {
        return mockMvc;
    }

    @Override
    public InternalCallTokenIssuer internalCallTokenIssuer() {
        return internalCallTokenIssuer;
    }

    @Override
    public String internalChainDatasetId() {
        return dataset.getId();
    }

    @Override
    public String administratorToken() throws Exception {
        String response = mockMvc.perform(post(ApiEndpoints.AUTH_V1 + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("email", "admin@mail.com", "password", "password"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = jsonMapper.readTree(response);
        return body.path("access_token").asText();
    }

    @Test
    @DisplayName("Role.INTERNAL has the authority the internal-call token grants")
    void internalRoleMatchesTokenAuthority() {
        assertEquals(InternalCallTokenIssuer.ROLE_INTERNAL, Role.INTERNAL.authorityName());
    }

    @Test
    @DisplayName("/internal: an INTERNAL-mode administrator token of this connector returns 401 on enforce")
    void administratorTokenOnEnforceReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(ApiEndpoints.INTERNAL_AGREEMENTS_V1 + "/some-agreement/enforce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + administratorToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api: POST /api/v1/offers/validate no longer exists (404 for an admin)")
    void offerValidateOnApiIsGone() throws Exception {
        mockMvc.perform(post("/api/v1/offers/validate")
                        .with(user("admin@mail.com").roles(Role.ADMIN.name()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializePlain(CatalogMockObjectUtil.OFFER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/api: POST /api/v1/agreements/{id}/enforce no longer exists (404 for an admin)")
    void agreementEnforceOnApiIsGone() throws Exception {
        mockMvc.perform(post("/api/v1/agreements/some-agreement/enforce")
                        .with(user("admin@mail.com").roles(Role.ADMIN.name()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/api: GET /api/v1/datasets/{id} still returns 200 for an admin")
    void datasetByIdOnApiStillWorks() throws Exception {
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + dataset.getId())
                        .with(user("admin@mail.com").roles(Role.ADMIN.name())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/api: GET /api/v1/datasets/{id}/artifact still returns 200 for an admin")
    void datasetArtifactOnApiStillWorks() throws Exception {
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + dataset.getId() + "/artifact")
                        .with(user("admin@mail.com").roles(Role.ADMIN.name())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/internal: GET artifact with a valid internal-call token returns 200")
    void internalArtifactReturnsOk() throws Exception {
        mockMvc.perform(get(ApiEndpoints.INTERNAL_DATASETS_V1 + "/" + dataset.getId() + "/artifact")
                        .header(HttpHeaders.AUTHORIZATION, internalCallTokenIssuer.authorizationHeader(TENANT_ID)))
                .andExpect(status().isOk());
    }
}
