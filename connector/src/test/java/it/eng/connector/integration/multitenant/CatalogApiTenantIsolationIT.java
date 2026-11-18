package it.eng.connector.integration.multitenant;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant isolation of the catalog API ({@code /api/v1/catalogs}, {@code /datasets}, {@code /dataservices},
 * {@code /distributions}, {@code /artifacts}).
 *
 * <p>Each tenant owns a full catalog graph. For every endpoint a tenant admin gets its own data, never sees the
 * other tenant's data in a list, gets 404 for the other tenant's ids, cannot change or delete them, and cannot
 * link them into its own documents.
 */
public class CatalogApiTenantIsolationIT extends AbstractTenantIsolationIT {

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

    private CatalogGraph graphA;
    private CatalogGraph graphB;
    private final List<String> createdDistributionIds = new ArrayList<>();
    private final List<String> createdCatalogIds = new ArrayList<>();

    private record CatalogGraph(Catalog catalog, Dataset dataset, DataService dataService,
                                Distribution distribution, Artifact artifact) {

        /** Ids of every document of the graph; none may appear in a response to the other tenant. */
        String[] idsAsArray() {
            return new String[]{catalog.getId(), dataset.getId(), dataService.getId(),
                    distribution.getId(), artifact.getId()};
        }
    }

    @BeforeEach
    void seedTwoTenants() {
        createdDistributionIds.clear();
        createdCatalogIds.clear();
        graphA = seedGraph(TENANT_A);
        graphB = seedGraph(TENANT_B);
    }

    @AfterEach
    void cleanup() {
        for (CatalogGraph graph : List.of(graphA, graphB)) {
            catalogRepository.deleteById(graph.catalog().getId());
            datasetRepository.deleteById(graph.dataset().getId());
            distributionRepository.deleteById(graph.distribution().getId());
            dataServiceRepository.deleteById(graph.dataService().getId());
            artifactRepository.deleteById(graph.artifact().getId());
        }
        createdDistributionIds.forEach(distributionRepository::deleteById);
        createdCatalogIds.forEach(catalogRepository::deleteById);
    }

    // ---------------------------------------------------------------- catalogs

    @Test
    @DisplayName("GET /catalogs returns the tenant's own catalog and nothing of the other tenant")
    void getCatalog_returnsOnlyOwnCatalog() throws Exception {
        MvcResult resultA = mockMvc.perform(get(ApiEndpoints.CATALOG_CATALOGS_V1).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(graphA.catalog().getId(), data(resultA).path("@id").asText());
        assertBodyDoesNotContain(resultA, graphB.idsAsArray());

        MvcResult resultB = mockMvc.perform(get(ApiEndpoints.CATALOG_CATALOGS_V1).with(adminOf(TENANT_B)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(graphB.catalog().getId(), data(resultB).path("@id").asText());
        assertBodyDoesNotContain(resultB, graphA.idsAsArray());
    }

    @Test
    @DisplayName("GET /catalogs/{id}: own catalog 200, other tenant's catalog 404")
    void getCatalogById_ownFound_otherNotFound() throws Exception {
        MvcResult own = mockMvc.perform(get(ApiEndpoints.CATALOG_CATALOGS_V1 + "/" + graphA.catalog().getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertBodyDoesNotContain(own, graphB.idsAsArray());

        MvcResult other = mockMvc.perform(get(ApiEndpoints.CATALOG_CATALOGS_V1 + "/" + graphB.catalog().getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isNotFound()).andReturn();
        assertBodyDoesNotContain(other, graphB.catalog().getTitle(), graphB.dataset().getId());
    }

    @Test
    @DisplayName("PUT /catalogs/{id} of the other tenant's catalog is 404 and leaves it unchanged")
    void updateCatalog_otherTenant_notFoundAndUnchanged() throws Exception {
        Catalog update = Catalog.Builder.newInstance()
                .title("hijacked")
                .participantId("urn:example:hijacker")
                .build();

        mockMvc.perform(put(ApiEndpoints.CATALOG_CATALOGS_V1 + "/" + graphB.catalog().getId())
                        .with(adminOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializePlain(update)))
                .andExpect(status().isNotFound());

        Catalog stored = catalogRepository.findById(graphB.catalog().getId()).orElseThrow();
        assertEquals(graphB.catalog().getTitle(), stored.getTitle());
        assertEquals(TENANT_B, stored.getTenantId());
    }

    @Test
    @DisplayName("DELETE /catalogs/{id} of the other tenant's catalog is 404 and does not delete it")
    void deleteCatalog_otherTenant_notFoundAndKept() throws Exception {
        mockMvc.perform(delete(ApiEndpoints.CATALOG_CATALOGS_V1 + "/" + graphB.catalog().getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isNotFound());

        assertTrue(catalogRepository.findById(graphB.catalog().getId()).isPresent());
    }

    @Test
    @DisplayName("POST /catalogs cannot link datasets, data services or distributions of the other tenant")
    void createCatalog_withOtherTenantReferences_isRejected() throws Exception {
        Catalog catalog = Catalog.Builder.newInstance()
                .title("Catalog linking tenant B")
                .participantId("urn:example:linker")
                .dataset(Set.of(datasetStub(graphB.dataset().getId())))
                .service(Set.of(dataServiceStub(graphB.dataService().getId())))
                .distribution(Set.of(distributionStub(graphB.distribution().getId(), graphA.dataService())))
                .build();
        createdCatalogIds.add(catalog.getId());

        mockMvc.perform(post(ApiEndpoints.CATALOG_CATALOGS_V1)
                        .with(adminOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializePlain(catalog)))
                .andExpect(status().isBadRequest());

        assertTrue(catalogRepository.findById(catalog.getId()).isEmpty());
    }

    @Test
    @DisplayName("PUT /catalogs/{id} on the own catalog cannot link a dataset of the other tenant")
    void updateOwnCatalog_withOtherTenantDataset_isRejected() throws Exception {
        Catalog update = Catalog.Builder.newInstance()
                .title(graphA.catalog().getTitle())
                .participantId(graphA.catalog().getParticipantId())
                .dataset(Set.of(graphA.dataset(), datasetStub(graphB.dataset().getId())))
                .build();

        mockMvc.perform(put(ApiEndpoints.CATALOG_CATALOGS_V1 + "/" + graphA.catalog().getId())
                        .with(adminOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializePlain(update)))
                .andExpect(status().isBadRequest());

        MvcResult own = mockMvc.perform(get(ApiEndpoints.CATALOG_CATALOGS_V1 + "/" + graphA.catalog().getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertBodyDoesNotContain(own, graphB.idsAsArray());
        assertBodyDoesNotContain(own, graphB.dataset().getTitle());
    }

    @Test
    @DisplayName("POST /catalogs with the id of the other tenant's catalog is rejected and does not overwrite it")
    void createCatalog_withOtherTenantId_doesNotOverwrite() throws Exception {
        Catalog takeover = Catalog.Builder.newInstance()
                .id(graphB.catalog().getId())
                .title("taken over")
                .participantId("urn:example:hijacker")
                .build();

        mockMvc.perform(post(ApiEndpoints.CATALOG_CATALOGS_V1)
                        .with(adminOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializePlain(takeover)))
                .andExpect(status().isBadRequest());

        Catalog stored = catalogRepository.findById(graphB.catalog().getId()).orElseThrow();
        assertEquals(graphB.catalog().getTitle(), stored.getTitle());
        assertEquals(TENANT_B, stored.getTenantId());
    }

    // ---------------------------------------------------------------- datasets

    @Test
    @DisplayName("GET /datasets lists only the tenant's own datasets")
    void getDatasets_returnsOnlyOwn() throws Exception {
        MvcResult resultA = mockMvc.perform(get(ApiEndpoints.CATALOG_DATASETS_V1).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        Set<String> idsA = ids(data(resultA));
        assertTrue(idsA.contains(graphA.dataset().getId()));
        assertBodyDoesNotContain(resultA, graphB.idsAsArray());

        MvcResult resultB = mockMvc.perform(get(ApiEndpoints.CATALOG_DATASETS_V1).with(adminOf(TENANT_B)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(Set.of(graphB.dataset().getId()), ids(data(resultB)));
    }

    @Test
    @DisplayName("GET /datasets/{id}, /formats and /artifact: own dataset 200, other tenant's dataset 404")
    void getDatasetById_formats_artifact_ownFound_otherNotFound() throws Exception {
        String own = ApiEndpoints.CATALOG_DATASETS_V1 + "/" + graphA.dataset().getId();
        String other = ApiEndpoints.CATALOG_DATASETS_V1 + "/" + graphB.dataset().getId();

        mockMvc.perform(get(own).with(adminOf(TENANT_A))).andExpect(status().isOk());
        mockMvc.perform(get(own + "/formats").with(adminOf(TENANT_A))).andExpect(status().isOk());
        mockMvc.perform(get(own + "/artifact").with(adminOf(TENANT_A))).andExpect(status().isOk());

        for (String path : List.of(other, other + "/formats", other + "/artifact")) {
            MvcResult result = mockMvc.perform(get(path).with(adminOf(TENANT_A)))
                    .andExpect(status().isNotFound()).andReturn();
            assertBodyDoesNotContain(result, graphB.artifact().getValue(), graphB.distribution().getId());
        }
    }

    @Test
    @DisplayName("PUT /datasets/{id} of the other tenant's dataset is 404 and leaves it unchanged")
    void updateDataset_otherTenant_notFoundAndUnchanged() throws Exception {
        Dataset update = Dataset.Builder.newInstance()
                .title("hijacked")
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .build();
        MockMultipartHttpServletRequestBuilder request = multipart(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + graphB.dataset().getId());
        request.with(r -> {
            r.setMethod("PUT");
            return r;
        });

        mockMvc.perform(request
                        .part(new MockPart("dataset", CatalogSerializer.serializePlain(update).getBytes(StandardCharsets.UTF_8)))
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isNotFound());

        Dataset stored = datasetRepository.findById(graphB.dataset().getId()).orElseThrow();
        assertEquals(graphB.dataset().getTitle(), stored.getTitle());
        assertEquals(graphB.artifact().getId(), stored.getArtifact().getId());
    }

    @Test
    @DisplayName("DELETE /datasets/{id} of the other tenant's dataset is 404 and keeps the dataset and its artifact")
    void deleteDataset_otherTenant_notFoundAndKept() throws Exception {
        mockMvc.perform(delete(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + graphB.dataset().getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isNotFound());

        assertTrue(datasetRepository.findById(graphB.dataset().getId()).isPresent());
        assertTrue(artifactRepository.findById(graphB.artifact().getId()).isPresent());
        assertTrue(catalogRepository.findById(graphB.catalog().getId()).orElseThrow().getDataset().stream()
                .anyMatch(ds -> ds.getId().equals(graphB.dataset().getId())));
    }

    @Test
    @DisplayName("PUT /datasets/{id} on the own dataset cannot link a distribution of the other tenant")
    void updateOwnDataset_withOtherTenantDistribution_isRejected() throws Exception {
        Dataset update = Dataset.Builder.newInstance()
                .title(graphA.dataset().getTitle())
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .distribution(Set.of(distributionStub(graphB.distribution().getId(), graphA.dataService())))
                .build();
        MockMultipartHttpServletRequestBuilder request = multipart(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + graphA.dataset().getId());
        request.with(r -> {
            r.setMethod("PUT");
            return r;
        });

        mockMvc.perform(request
                        .part(new MockPart("dataset", CatalogSerializer.serializePlain(update).getBytes(StandardCharsets.UTF_8)))
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isBadRequest());

        MvcResult own = mockMvc.perform(get(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + graphA.dataset().getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertBodyDoesNotContain(own, graphB.distribution().getId(), graphB.distribution().getTitle());
    }

    @Test
    @DisplayName("POST /datasets with the id of the other tenant's dataset is rejected and does not overwrite it")
    void createDataset_withOtherTenantId_doesNotOverwrite() throws Exception {
        Dataset takeover = Dataset.Builder.newInstance()
                .id(graphB.dataset().getId())
                .title("taken over")
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .build();

        mockMvc.perform(multipart(ApiEndpoints.CATALOG_DATASETS_V1)
                        .part(new MockPart("dataset", CatalogSerializer.serializePlain(takeover).getBytes(StandardCharsets.UTF_8)))
                        .part(new MockPart("url", "https://hijacker.example.com/data".getBytes(StandardCharsets.UTF_8)))
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isBadRequest());

        Dataset stored = datasetRepository.findById(graphB.dataset().getId()).orElseThrow();
        assertEquals(graphB.dataset().getTitle(), stored.getTitle());
        assertEquals(TENANT_B, stored.getTenantId());
        assertEquals(graphB.artifact().getId(), stored.getArtifact().getId());
    }

    // ---------------------------------------------------------------- data services

    @Test
    @DisplayName("GET /dataservices lists only the tenant's own data services")
    void getDataServices_returnsOnlyOwn() throws Exception {
        MvcResult resultA = mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertTrue(ids(data(resultA)).contains(graphA.dataService().getId()));
        assertBodyDoesNotContain(resultA, graphB.idsAsArray());

        MvcResult resultB = mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1).with(adminOf(TENANT_B)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(Set.of(graphB.dataService().getId()), ids(data(resultB)));
    }

    @Test
    @DisplayName("GET /dataservices/{id}: own 200, other tenant's 404")
    void getDataServiceById_ownFound_otherNotFound() throws Exception {
        mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1 + "/" + graphA.dataService().getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk());

        MvcResult other = mockMvc.perform(get(ApiEndpoints.CATALOG_DATA_SERVICES_V1 + "/" + graphB.dataService().getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isNotFound()).andReturn();
        assertBodyDoesNotContain(other, graphB.dataService().getEndpointURL());
    }

    @Test
    @DisplayName("PUT and DELETE /dataservices/{id} of the other tenant are 404 and change nothing")
    void updateAndDeleteDataService_otherTenant_notFoundAndUnchanged() throws Exception {
        String path = ApiEndpoints.CATALOG_DATA_SERVICES_V1 + "/" + graphB.dataService().getId();
        DataService update = DataService.Builder.newInstance()
                .endpointURL("https://hijacker.example.com")
                .build();

        mockMvc.perform(put(path).with(adminOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializePlain(update)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(path).with(adminOf(TENANT_A)))
                .andExpect(status().isNotFound());

        DataService stored = dataServiceRepository.findById(graphB.dataService().getId()).orElseThrow();
        assertEquals(graphB.dataService().getEndpointURL(), stored.getEndpointURL());
    }

    @Test
    @DisplayName("POST /dataservices with the id of the other tenant's data service is rejected and does not overwrite it")
    void createDataService_withOtherTenantId_doesNotOverwrite() throws Exception {
        DataService takeover = DataService.Builder.newInstance()
                .id(graphB.dataService().getId())
                .title("taken over")
                .build();

        mockMvc.perform(post(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                        .with(adminOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializePlain(takeover)))
                .andExpect(status().isBadRequest());

        DataService stored = dataServiceRepository.findById(graphB.dataService().getId()).orElseThrow();
        assertEquals(graphB.dataService().getTitle(), stored.getTitle());
        assertEquals(TENANT_B, stored.getTenantId());
    }

    // ---------------------------------------------------------------- distributions

    @Test
    @DisplayName("GET /distributions lists only the tenant's own distributions")
    void getDistributions_returnsOnlyOwn() throws Exception {
        MvcResult resultA = mockMvc.perform(get(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertTrue(ids(data(resultA)).contains(graphA.distribution().getId()));
        assertBodyDoesNotContain(resultA, graphB.idsAsArray());

        MvcResult resultB = mockMvc.perform(get(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1).with(adminOf(TENANT_B)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(Set.of(graphB.distribution().getId()), ids(data(resultB)));
    }

    @Test
    @DisplayName("GET /distributions/{id}: own 200, other tenant's 404")
    void getDistributionById_ownFound_otherNotFound() throws Exception {
        mockMvc.perform(get(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1 + "/" + graphA.distribution().getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk());

        MvcResult other = mockMvc.perform(get(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1 + "/" + graphB.distribution().getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isNotFound()).andReturn();
        assertBodyDoesNotContain(other, graphB.dataService().getId());
    }

    @Test
    @DisplayName("PUT and DELETE /distributions/{id} of the other tenant are 404 and change nothing")
    void updateAndDeleteDistribution_otherTenant_notFoundAndUnchanged() throws Exception {
        String path = ApiEndpoints.CATALOG_DISTRIBUTIONS_V1 + "/" + graphB.distribution().getId();
        Distribution update = Distribution.Builder.newInstance()
                .title("hijacked")
                .format("HTTP:PULL")
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .accessService(graphA.dataService())
                .build();

        mockMvc.perform(put(path).with(adminOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializePlain(update)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(path).with(adminOf(TENANT_A)))
                .andExpect(status().isNotFound());

        Distribution stored = distributionRepository.findById(graphB.distribution().getId()).orElseThrow();
        assertEquals(graphB.distribution().getTitle(), stored.getTitle());
        assertEquals(graphB.dataService().getId(), stored.getAccessService().getId());
    }

    @Test
    @DisplayName("POST /distributions cannot use a data service of the other tenant as access service")
    void createDistribution_withOtherTenantDataService_isRejected() throws Exception {
        Distribution distribution = Distribution.Builder.newInstance()
                .title("Distribution linking tenant B")
                .format("HTTP:PULL")
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .accessService(dataServiceStub(graphB.dataService().getId()))
                .build();
        createdDistributionIds.add(distribution.getId());

        mockMvc.perform(post(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1)
                        .with(adminOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializePlain(distribution)))
                .andExpect(status().isBadRequest());

        assertTrue(distributionRepository.findById(distribution.getId()).isEmpty());
    }

    @Test
    @DisplayName("POST /distributions with the id of the other tenant's distribution is rejected and does not overwrite it")
    void createDistribution_withOtherTenantId_doesNotOverwrite() throws Exception {
        Distribution takeover = distributionStub(graphB.distribution().getId(), graphA.dataService());

        mockMvc.perform(post(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1)
                        .with(adminOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CatalogSerializer.serializePlain(takeover)))
                .andExpect(status().isBadRequest());

        Distribution stored = distributionRepository.findById(graphB.distribution().getId()).orElseThrow();
        assertEquals(graphB.distribution().getTitle(), stored.getTitle());
        assertEquals(TENANT_B, stored.getTenantId());
    }

    // ---------------------------------------------------------------- artifacts

    @Test
    @DisplayName("GET /artifacts lists only artifacts of the tenant's datasets; other tenant's artifact id is 404")
    void artifacts_ownListedAndFound_otherNotListedNotFound() throws Exception {
        MvcResult list = mockMvc.perform(get(ApiEndpoints.CATALOG_ARTIFACT_V1).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertTrue(ids(data(list)).contains(graphA.artifact().getId()));
        assertBodyDoesNotContain(list, graphB.artifact().getId(), graphB.artifact().getValue());

        mockMvc.perform(get(ApiEndpoints.CATALOG_ARTIFACT_V1 + "/" + graphA.artifact().getId()).with(adminOf(TENANT_A)))
                .andExpect(status().isOk());
        mockMvc.perform(get(ApiEndpoints.CATALOG_ARTIFACT_V1 + "/" + graphB.artifact().getId()).with(adminOf(TENANT_A)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- stubs carrying only another tenant's id

    private static Dataset datasetStub(String id) {
        return Dataset.Builder.newInstance()
                .id(id)
                .title("stub")
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .build();
    }

    private static DataService dataServiceStub(String id) {
        return DataService.Builder.newInstance()
                .id(id)
                .title("stub")
                .build();
    }

    private static Distribution distributionStub(String id, DataService accessService) {
        return Distribution.Builder.newInstance()
                .id(id)
                .title("stub")
                .format("HTTP:PULL")
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .accessService(accessService)
                .build();
    }

    // ---------------------------------------------------------------- seed

    private CatalogGraph seedGraph(String tenantId) {
        String suffix = tenantId + "-" + UUID.randomUUID();
        Artifact artifact = artifactRepository.save(Artifact.Builder.newInstance()
                .artifactType(ArtifactType.EXTERNAL)
                .value("https://" + suffix + ".example.com/data")
                .build());
        DataService dataService = dataServiceRepository.save(DataService.Builder.newInstance()
                .title("Data service " + suffix)
                .endpointURL("https://" + suffix + ".example.com/service")
                .tenantId(tenantId)
                .build());
        Distribution distribution = distributionRepository.save(Distribution.Builder.newInstance()
                .title("Distribution " + suffix)
                .format("HTTP:PULL")
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .accessService(dataService)
                .tenantId(tenantId)
                .build());
        Dataset dataset = datasetRepository.save(Dataset.Builder.newInstance()
                .title("Dataset " + suffix)
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .distribution(new HashSet<>(Set.of(distribution)))
                .artifact(artifact)
                .tenantId(tenantId)
                .build());
        Catalog catalog = catalogRepository.save(Catalog.Builder.newInstance()
                .title("Catalog " + suffix)
                .participantId("urn:connector:" + tenantId)
                .dataset(new HashSet<>(Set.of(dataset)))
                .service(new HashSet<>(Set.of(dataService)))
                .distribution(new HashSet<>(Set.of(distribution)))
                .tenantId(tenantId)
                .build());
        return new CatalogGraph(catalog, dataset, dataService, distribution, artifact);
    }

}
