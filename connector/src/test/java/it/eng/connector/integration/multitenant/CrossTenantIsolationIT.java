package it.eng.connector.integration.multitenant;

import it.eng.catalog.model.*;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.service.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying that catalog and dataset data is correctly isolated per tenant.
 * Tenant A must never see Tenant B's datasets through the protocol or API endpoints,
 * and the @DBRef cascade guard must reject cross-tenant dataset references.
 */
public class CrossTenantIsolationIT extends BaseIntegrationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Autowired
    private CatalogRepository catalogRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DataServiceRepository dataServiceRepository;
    @Autowired
    private DistributionRepository distributionRepository;
    @Autowired
    private ArtifactRepository artifactRepository;
    @Autowired
    private CatalogService catalogService;

    private Catalog catalogA;
    private Catalog catalogB;
    private Dataset datasetA;
    private Dataset datasetB;

    /** Tracks all persisted entity IDs so they can be removed in {@code cleanupTwoTenants}. */
    private final List<String> savedDataServiceIds = new ArrayList<>();
    private final List<String> savedDistributionIds = new ArrayList<>();
    private final List<String> savedArtifactIds = new ArrayList<>();

    @BeforeEach
    public void seedTwoTenants() {
        savedDataServiceIds.clear();
        savedDistributionIds.clear();
        savedArtifactIds.clear();

        ensureTenant(TENANT_A);
        ensureTenant(TENANT_B);

        datasetA = buildAndSaveExternalDataset(TENANT_A);
        datasetB = buildAndSaveExternalDataset(TENANT_B);

        catalogA = buildAndSaveCatalog(TENANT_A, datasetA);
        catalogB = buildAndSaveCatalog(TENANT_B, datasetB);
    }

    @AfterEach
    public void cleanupTwoTenants() {
        TenantContextHolder.clear();
        if (catalogA != null) {
            catalogRepository.deleteById(catalogA.getId());
        }
        if (catalogB != null) {
            catalogRepository.deleteById(catalogB.getId());
        }
        if (datasetA != null) {
            datasetRepository.deleteById(datasetA.getId());
        }
        if (datasetB != null) {
            datasetRepository.deleteById(datasetB.getId());
        }
        savedDistributionIds.forEach(distributionRepository::deleteById);
        savedDataServiceIds.forEach(dataServiceRepository::deleteById);
        savedArtifactIds.forEach(artifactRepository::deleteById);
        tenantRepository.deleteById(TENANT_A);
        tenantRepository.deleteById(TENANT_B);
    }

    @Test
    @DisplayName("catalogRequest for tenant-a returns only tenant-a datasets, not tenant-b datasets")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    void catalogRequest_tenantA_doesNotReturnTenantBDatasets() throws Exception {
        String requestBody = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);
        ResultActions result = mockMvc.perform(
                post("/" + TENANT_A + "/catalog/request")
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk());
        String responseBody = result.andReturn().getResponse().getContentAsString();
        assertTrue(responseBody.contains(datasetA.getId()),
                "Catalog response for tenant-a must contain tenant-a's dataset id");
        assertTrue(!responseBody.contains(datasetB.getId()),
                "Catalog response for tenant-a must NOT contain tenant-b's dataset id");
    }

    @Test
    @DisplayName("dataset endpoint for tenant-a returns 404 when requesting a tenant-b dataset")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    void datasetById_tenantA_cannotAccessTenantBDataset() throws Exception {
        mockMvc.perform(
                get("/" + TENANT_A + "/catalog/datasets/" + datasetB.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updateCatalogDatasetAfterSave with cross-tenant dataset throws InternalServerErrorAPIException")
    void updateCatalog_crossTenantDataset_isRejected() {
        TenantContextHolder.setTenantId(TENANT_A);
        try {
            assertThrows(it.eng.catalog.exceptions.InternalServerErrorAPIException.class,
                    () -> catalogService.updateCatalogDatasetAfterSave(datasetB),
                    "Dataset from a different tenant must be rejected by the service guard");
        } finally {
            TenantContextHolder.clear();
        }
    }

    // ---- helpers ----

    private void ensureTenant(String tenantId) {
        if (tenantRepository.findById(tenantId).isEmpty()) {
            tenantRepository.save(Tenant.Builder.newInstance()
                    .id(tenantId)
                    .name(tenantId)
                    .participantId("urn:example:" + tenantId)
                    .bucketName(s3Properties.getBucketName())
                    .enabled(true)
                    .build());
        }
    }

    /**
     * Creates and persists a dataset with an EXTERNAL artifact, bypassing the S3 file existence
     * check in {@code CatalogService.getCatalog()}. All @DBRef-referenced entities are saved first
     * so Spring Data can resolve them when the catalog is loaded.
     *
     * @param tenantId the tenant to associate with all created entities
     * @return the saved dataset
     */
    private Dataset buildAndSaveExternalDataset(String tenantId) {
        // Save DataService first to get a database-assigned ID
        DataService savedDataService = dataServiceRepository.save(CatalogMockObjectUtil.createNewDataService(tenantId));
        savedDataServiceIds.add(savedDataService.getId());

        // Build Distribution referencing the already-saved DataService
        Distribution distribution = Distribution.Builder.newInstance()
                .format("HttpData-PULL")
                .hasPolicy(new HashSet<>(Collections.singletonList(CatalogMockObjectUtil.createNewOffer())))
                .accessService(savedDataService)
                .build();
        distribution.injectTenantId(tenantId);
        Distribution savedDistribution = distributionRepository.save(distribution);
        savedDistributionIds.add(savedDistribution.getId());

        // Save EXTERNAL artifact so @DBRef from Dataset resolves correctly
        Artifact savedArtifact = artifactRepository.save(
                Artifact.Builder.newInstance()
                        .artifactType(ArtifactType.EXTERNAL)
                        .filename("external-data.txt")
                        .value("http://external-data.example.com/" + tenantId)
                        .build());
        savedArtifactIds.add(savedArtifact.getId());

        // Build and save Dataset referencing saved Distribution and Artifact
        Dataset dataset = Dataset.Builder.newInstance()
                .id(createNewId())
                .hasPolicy(new HashSet<>(Collections.singletonList(CatalogMockObjectUtil.createNewOffer())))
                .distribution(new HashSet<>(Collections.singletonList(savedDistribution)))
                .artifact(savedArtifact)
                .build();
        dataset.injectTenantId(tenantId);
        return datasetRepository.save(dataset);
    }

    /**
     * Builds a catalog containing the given dataset and saves it to the catalog repository.
     *
     * @param tenantId the tenant to associate with the catalog
     * @param dataset  the dataset to include in the catalog
     * @return the saved catalog
     */
    private Catalog buildAndSaveCatalog(String tenantId, Dataset dataset) {
        Distribution distribution = dataset.getDistribution().iterator().next();
        DataService dataService = distribution.getAccessService();

        Catalog catalog = Catalog.Builder.newInstance()
                .participantId("urn:example:" + tenantId)
                .dataset(new HashSet<>(Collections.singletonList(dataset)))
                .service(new HashSet<>(Collections.singletonList(dataService)))
                .distribution(new HashSet<>(Collections.singletonList(distribution)))
                .build();
        catalog.injectTenantId(tenantId);
        return catalogRepository.save(catalog);
    }
}
