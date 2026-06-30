package it.eng.connector.integration.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import it.eng.catalog.model.*;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;

import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.service.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying tenant isolation for internal API calls.
 *
 * <p>Two independent tenants ({@value #TENANT_ALPHA} and {@value #TENANT_BETA}) are provisioned
 * with separate catalogs, datasets, and S3 artifacts.  The tests assert that:
 * <ol>
 *   <li>The DSP protocol catalog endpoint returns only the requesting tenant's data.</li>
 *   <li>The dataset listing API is scoped to the tenant when {@value TenantContextHolder#HEADER_X_TENANT_ID}
 *       is present.</li>
 *   <li>Offer validation via {@code /api/v1/offers/validate} is tenant-scoped: an offer from
 *       tenant A is invalid when validated in the context of tenant B.</li>
 * </ol>
 *
 * <p>These three paths cover all internal API call sites that were updated to propagate
 * {@value TenantContextHolder#HEADER_X_TENANT_ID}:
 * <ul>
 *   <li>{@code ContractNegotiationProviderService} → {@code /api/v1/offers/validate}</li>
 *   <li>{@code sendInternalRequest} (datasets / artifact fetch / agreement enforce)</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MultitenantInternalAPIIT extends BaseIntegrationTest {

    static final String TENANT_ALPHA = "tenant-alpha";
    static final String TENANT_BETA  = "tenant-beta";

    @Autowired private CatalogRepository    catalogRepository;
    @Autowired private DatasetRepository    datasetRepository;
    @Autowired private DataServiceRepository dataServiceRepository;
    @Autowired private DistributionRepository distributionRepository;
    @Autowired private ArtifactRepository   artifactRepository;
    @Autowired private S3ClientService      s3ClientService;
    @Autowired private S3Properties         s3Properties;

    private Catalog catalogAlpha;
    private Catalog catalogBeta;
    private Dataset datasetAlpha;
    private Dataset datasetBeta;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @BeforeEach
    void setupTwoTenants() throws Exception {
        // Provision tenants
        provisionTenant(TENANT_ALPHA);
        provisionTenant(TENANT_BETA);

        // Build independent catalog + dataset for each tenant
        catalogAlpha = CatalogMockObjectUtil.createNewCatalog(TENANT_ALPHA);
        datasetAlpha = catalogAlpha.getDataset().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Alpha catalog has no datasets"));

        catalogBeta = CatalogMockObjectUtil.createNewCatalog(TENANT_BETA);
        datasetBeta = catalogBeta.getDataset().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Beta catalog has no datasets"));

        // Persist alpha
        persistCatalog(catalogAlpha);
        // Persist beta
        persistCatalog(catalogBeta);

        // Upload S3 artifacts so CatalogService does not filter the datasets out
        uploadArtifact(datasetAlpha.getId());
        uploadArtifact(datasetBeta.getId());
    }

    @AfterEach
    void cleanup() {
        datasetRepository.deleteAll();
        catalogRepository.deleteAll();
        dataServiceRepository.deleteAll();
        distributionRepository.deleteAll();
        artifactRepository.deleteAll();
        tenantRepository.deleteById(TENANT_ALPHA);
        tenantRepository.deleteById(TENANT_BETA);
        removeAllS3Files();
    }

    // -----------------------------------------------------------------------
    // Protocol catalog request — tenant isolation
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("Protocol /catalog/request returns only alpha's dataset when called as alpha tenant")
    void protocolCatalogRequest_returnOnlyAlphaDataset() throws Exception {
        String catalogJson = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);

        MvcResult result = mockMvc.perform(
                        post("/" + TENANT_ALPHA + "/catalog/request")
                                .with(user(TestUtil.CONNECTOR_USER).password("password").roles("CONNECTOR"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(catalogJson))
                .andExpect(status().isOk())
                .andReturn();

        Catalog response = CatalogSerializer.deserializeProtocol(
                result.getResponse().getContentAsString(), Catalog.class);

        assertNotNull(response);
        assertEquals(1, response.getDataset().size());

        Dataset returned = response.getDataset().iterator().next();
        assertEquals(datasetAlpha.getId(), returned.getId(),
                "Alpha tenant catalog must contain alpha's dataset");
        assertFalse(response.getDataset().stream()
                        .anyMatch(ds -> ds.getId().equals(datasetBeta.getId())),
                "Alpha tenant catalog must NOT contain beta's dataset");
    }

    @Test
    @Order(2)
    @DisplayName("Protocol /catalog/request returns only beta's dataset when called as beta tenant")
    void protocolCatalogRequest_returnOnlyBetaDataset() throws Exception {
        String catalogJson = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);

        MvcResult result = mockMvc.perform(
                        post("/" + TENANT_BETA + "/catalog/request")
                                .with(user(TestUtil.CONNECTOR_USER).password("password").roles("CONNECTOR"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(catalogJson))
                .andExpect(status().isOk())
                .andReturn();

        Catalog response = CatalogSerializer.deserializeProtocol(
                result.getResponse().getContentAsString(), Catalog.class);

        assertNotNull(response);
        assertEquals(1, response.getDataset().size());

        Dataset returned = response.getDataset().iterator().next();
        assertEquals(datasetBeta.getId(), returned.getId(),
                "Beta tenant catalog must contain beta's dataset");
        assertFalse(response.getDataset().stream()
                        .anyMatch(ds -> ds.getId().equals(datasetAlpha.getId())),
                "Beta tenant catalog must NOT contain alpha's dataset");
    }

    // -----------------------------------------------------------------------
    // API /datasets — X-Tenant-Id header scoping
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/datasets with X-Tenant-Id: alpha returns only alpha's dataset")
    void datasetAPI_withAlphaTenantHeader_returnsOnlyAlphaDataset() throws Exception {
        MvcResult result = mockMvc.perform(
                        get(ApiEndpoints.CATALOG_DATASETS_V1)
                                .with(user("admin@mail.com").roles("ADMIN"))
                                .header(TenantContextHolder.HEADER_X_TENANT_ID, TENANT_ALPHA)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        TypeReference<GenericApiResponse<List<Dataset>>> typeRef = new TypeReference<>() {};
        GenericApiResponse<List<Dataset>> apiResp = CatalogSerializer.deserializePlain(
                result.getResponse().getContentAsString(), typeRef);

        assertNotNull(apiResp);
        List<Dataset> datasets = apiResp.getData();
        assertNotNull(datasets);

        boolean hasAlpha = datasets.stream().anyMatch(ds -> datasetAlpha.getId().equals(ds.getId()));
        boolean hasBeta  = datasets.stream().anyMatch(ds -> datasetBeta.getId().equals(ds.getId()));

        assertTrue(hasAlpha,  "Alpha dataset must be present for X-Tenant-Id: " + TENANT_ALPHA);
        assertFalse(hasBeta,  "Beta dataset must NOT be present for X-Tenant-Id: " + TENANT_ALPHA);
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/datasets with X-Tenant-Id: beta returns only beta's dataset")
    void datasetAPI_withBetaTenantHeader_returnsOnlyBetaDataset() throws Exception {
        MvcResult result = mockMvc.perform(
                        get(ApiEndpoints.CATALOG_DATASETS_V1)
                                .with(user("admin@mail.com").roles("ADMIN"))
                                .header(TenantContextHolder.HEADER_X_TENANT_ID, TENANT_BETA)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        TypeReference<GenericApiResponse<List<Dataset>>> typeRef = new TypeReference<>() {};
        GenericApiResponse<List<Dataset>> apiResp = CatalogSerializer.deserializePlain(
                result.getResponse().getContentAsString(), typeRef);

        assertNotNull(apiResp);
        List<Dataset> datasets = apiResp.getData();
        assertNotNull(datasets);

        boolean hasAlpha = datasets.stream().anyMatch(ds -> datasetAlpha.getId().equals(ds.getId()));
        boolean hasBeta  = datasets.stream().anyMatch(ds -> datasetBeta.getId().equals(ds.getId()));

        assertFalse(hasAlpha, "Alpha dataset must NOT be present for X-Tenant-Id: " + TENANT_BETA);
        assertTrue(hasBeta,   "Beta dataset must be present for X-Tenant-Id: " + TENANT_BETA);
    }

    // -----------------------------------------------------------------------
    // Offer validation — the primary internal call site
    // -----------------------------------------------------------------------

    @Test
    @Order(5)
    @DisplayName("POST /api/v1/offers/validate with X-Tenant-Id: alpha accepts alpha's offer")
    void offerValidate_withAlphaTenantHeader_acceptsAlphaOffer() throws Exception {
        Offer alphaOffer = getFirstOffer(datasetAlpha);

        mockMvc.perform(
                        post(ApiEndpoints.CATALOG_OFFERS_V1 + "/validate")
                                .with(user("admin@mail.com").roles("ADMIN"))
                                .header(TenantContextHolder.HEADER_X_TENANT_ID, TENANT_ALPHA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CatalogSerializer.serializePlain(alphaOffer)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/v1/offers/validate with X-Tenant-Id: beta rejects alpha's offer")
    void offerValidate_withBetaTenantHeader_rejectsAlphaOffer() throws Exception {
        Offer alphaOffer = getFirstOffer(datasetAlpha);

        mockMvc.perform(
                        post(ApiEndpoints.CATALOG_OFFERS_V1 + "/validate")
                                .with(user("admin@mail.com").roles("ADMIN"))
                                .header(TenantContextHolder.HEADER_X_TENANT_ID, TENANT_BETA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CatalogSerializer.serializePlain(alphaOffer)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    @DisplayName("POST /api/v1/offers/validate with X-Tenant-Id: beta accepts beta's offer")
    void offerValidate_withBetaTenantHeader_acceptsBetaOffer() throws Exception {
        Offer betaOffer = getFirstOffer(datasetBeta);

        mockMvc.perform(
                        post(ApiEndpoints.CATALOG_OFFERS_V1 + "/validate")
                                .with(user("admin@mail.com").roles("ADMIN"))
                                .header(TenantContextHolder.HEADER_X_TENANT_ID, TENANT_BETA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CatalogSerializer.serializePlain(betaOffer)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    @DisplayName("POST /api/v1/offers/validate with X-Tenant-Id: alpha rejects beta's offer")
    void offerValidate_withAlphaTenantHeader_rejectsBetaOffer() throws Exception {
        Offer betaOffer = getFirstOffer(datasetBeta);

        mockMvc.perform(
                        post(ApiEndpoints.CATALOG_OFFERS_V1 + "/validate")
                                .with(user("admin@mail.com").roles("ADMIN"))
                                .header(TenantContextHolder.HEADER_X_TENANT_ID, TENANT_ALPHA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CatalogSerializer.serializePlain(betaOffer)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // Helper utilities
    // -----------------------------------------------------------------------

    private void provisionTenant(String tenantId) {
        if (tenantRepository.findById(tenantId).isEmpty()) {
            tenantRepository.save(Tenant.Builder.newInstance()
                    .id(tenantId)
                    .name("Test Tenant " + tenantId)
                    .description("Multitenant IT test tenant")
                    .participantId("urn:connector:" + tenantId)
                    .enabled(true)
                    .automaticNegotiation(false)
                    .automaticTransfer(false)
                    .build());
        }
    }

    private void persistCatalog(Catalog catalog) {
        catalogRepository.save(catalog);
        catalog.getDataset().forEach(ds -> {
            datasetRepository.save(ds);
            if (ds.getArtifact() != null) {
                artifactRepository.save(ds.getArtifact());
            }
            distributionRepository.saveAll(ds.getDistribution());
            ds.getDistribution().forEach(dist ->
                    dataServiceRepository.save(dist.getAccessService()));
        });
        dataServiceRepository.saveAll(catalog.getService());
        distributionRepository.saveAll(catalog.getDistribution());
    }

    private void uploadArtifact(String datasetId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", datasetId + ".txt", MediaType.TEXT_PLAIN_VALUE,
                ("artifact-content-for-" + datasetId).getBytes());
        ContentDisposition cd = ContentDisposition.attachment().filename(file.getOriginalFilename()).build();

        Map<String, String> s3Props = createS3EndpointProperties(datasetId);
        try {
            s3ClientService.uploadFile(file.getInputStream(), s3Props,
                    file.getContentType(), cd.toString()).get();
        } catch (Exception e) {
            throw new Exception("Failed to upload artifact for dataset " + datasetId, e);
        }
        Thread.sleep(1000);
    }

    private void removeAllS3Files() {
        java.util.List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());
        if (files != null) {
            files.forEach(f -> s3ClientService.deleteFile(s3Properties.getBucketName(), f));
        }
    }

    private Offer getFirstOffer(Dataset dataset) {
        Offer existing = dataset.getHasPolicy().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Dataset has no offer: " + dataset.getId()));
        // Rebuild with target pointing to this dataset so validateOffer can match it.
        return Offer.Builder.newInstance()
                .id(existing.getId())
                .target(dataset.getId())
                .permission(existing.getPermission())
                .build();
    }
}
