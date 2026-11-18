package it.eng.connector.integration.internal;

import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.connector.integration.BaseKeycloakIntegrationTest;
import it.eng.tools.auth.internal.InternalCallTokenIssuer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.repository.ArtifactRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /internal/**} chain in {@code KEYCLOAK} mode: the same rules as in {@code INTERNAL} mode, and no
 * Keycloak token, from any realm, is accepted.
 */
class KeycloakInternalCallSecurityIT extends BaseKeycloakIntegrationTest implements InternalChainSecurityContract {

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
        return adminAccessToken();
    }

    @Test
    @DisplayName("/internal: a platform (M2M) Keycloak token returns 401")
    void platformTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(InternalChainFixtures.formatsPath(dataset.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(connectorAccessToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/internal: a super-admin Keycloak token returns 401")
    void superAdminTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(InternalChainFixtures.formatsPath(dataset.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(superAdminAccessToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/internal: GET artifact with a valid internal-call token returns 200")
    void internalArtifactReturnsOk() throws Exception {
        mockMvc.perform(get(ApiEndpoints.INTERNAL_DATASETS_V1 + "/" + dataset.getId() + "/artifact")
                        .header(HttpHeaders.AUTHORIZATION, internalCallTokenIssuer.authorizationHeader(TENANT_ID)))
                .andExpect(status().isOk());
    }
}
