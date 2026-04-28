package it.eng.connector.integration;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KeycloakSecurityIT extends BaseKeycloakIntegrationTest {

    private static final String CATALOG_REQUEST_PATH = "/" + TENANT_ID + "/catalog/request";

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
    private S3ClientService s3ClientService;

    @Autowired
    private S3Properties s3Properties;

    @AfterEach
    void cleanupCatalogData() {
        cleanupState();
    }

    @BeforeEach
    void prepareCatalogData() {
        cleanupState();
    }

    private void cleanupState() {
        datasetRepository.deleteAll();
        catalogRepository.deleteAll();
        dataServiceRepository.deleteAll();
        distributionRepository.deleteAll();
        artifactRepository.deleteAll();
        removeFiles();
    }

    @Test
    @DisplayName("GET /api/v1/properties without bearer token returns 401 in Keycloak mode")
    void getPropertiesWithoutBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(ApiEndpoints.PROPERTIES_V1 + "/")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/properties with invalid bearer token returns 401 in Keycloak mode")
    void getPropertiesWithInvalidBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(ApiEndpoints.PROPERTIES_V1 + "/")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("not-a-valid-jwt"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/properties with connector token returns 403 in Keycloak mode")
    void getPropertiesWithConnectorTokenReturnsForbidden() throws Exception {
        mockMvc.perform(get(ApiEndpoints.PROPERTIES_V1 + "/")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(connectorAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/properties with admin token returns 200 in Keycloak mode")
    void getPropertiesWithAdminTokenReturnsOk() throws Exception {
        mockMvc.perform(get(ApiEndpoints.PROPERTIES_V1 + "/")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /api/v1/users with admin token returns 404 in Keycloak mode")
    void getUsersWithAdminTokenReturnsNotFound() throws Exception {
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{tenantId}/catalog/request without bearer token returns 401 in Keycloak mode")
    void catalogRequestWithoutBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(CATALOG_REQUEST_PATH)
                        .content(catalogRequestBody())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /{tenantId}/catalog/request with admin token returns 403 in Keycloak mode")
    void catalogRequestWithAdminTokenReturnsForbidden() throws Exception {
        mockMvc.perform(post(CATALOG_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminAccessToken()))
                        .content(catalogRequestBody())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /{tenantId}/catalog/request with connector token returns 200 in Keycloak mode")
    void catalogRequestWithConnectorTokenReturnsOk() throws Exception {
        populateCatalog();
        uploadFile();

        ResultActions result = mockMvc.perform(post(CATALOG_REQUEST_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(connectorAccessToken()))
                .content(catalogRequestBody())
                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        Catalog catalogResponse = CatalogSerializer.deserializeProtocol(
                result.andReturn().getResponse().getContentAsString(),
                Catalog.class
        );
        assertNotNull(catalogResponse);
        assertFalse(catalogResponse.getDataset().isEmpty());
    }

    private void populateCatalog() {
        Catalog catalog = CatalogMockObjectUtil.createNewCatalog();
        catalog.injectTenantId(TENANT_ID);
        Dataset dataset = catalog.getDataset().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Catalog test fixture does not contain a dataset."));
        dataset.injectTenantId(TENANT_ID);

        catalogRepository.save(catalog);
        datasetRepository.saveAll(catalog.getDataset());
        dataServiceRepository.saveAll(catalog.getService());
        distributionRepository.saveAll(catalog.getDistribution());

        artifactRepository.save(dataset.getArtifact());
    }

    private String catalogRequestBody() {
        return CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);
    }

    private void uploadFile() throws Exception {
        Dataset dataset = datasetRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Catalog dataset was not persisted before S3 upload."));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(file.getOriginalFilename())
                .build();

        Map<String, String> destinationS3Properties = createS3EndpointProperties(dataset.getId());
        try {
            s3ClientService.uploadFile(file.getInputStream(), destinationS3Properties,
                            file.getContentType(), contentDisposition.toString())
                    .get();
        } catch (Exception exception) {
            throw new Exception("File storing aborted, " + exception.getLocalizedMessage(), exception);
        }

        waitUntilFileIsVisible(dataset.getId());
    }

    private void waitUntilFileIsVisible(String objectKey) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());
            if (files != null && files.contains(objectKey)) {
                return;
            }
            Thread.sleep(200L);
        }
        throw new IllegalStateException("Uploaded file '" + objectKey + "' was not visible in S3 within timeout.");
    }

    private void removeFiles() {
        List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());
        if (files != null) {
            for (String file : files) {
                s3ClientService.deleteFile(s3Properties.getBucketName(), file);
            }
        }
    }
}
