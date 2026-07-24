package it.eng.connector.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

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

@TestPropertySource(properties = "application.auth.provider=DISABLED")
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "server.port=0"
        })
class DisabledSecurityIT extends BaseIntegrationTest {

    private static final String CATALOG_REQUEST_PATH = "/" + TENANT_ID + "/catalog/request";

    @DynamicPropertySource
    static void disabledSecurityProperties(DynamicPropertyRegistry registry) {
        registry.add("application.auth.provider", () -> "DISABLED");
        registry.add("server.port", () -> "0");
    }

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
        datasetRepository.deleteAll();
        catalogRepository.deleteAll();
        dataServiceRepository.deleteAll();
        distributionRepository.deleteAll();
        artifactRepository.deleteAll();
        removeFiles();
    }

    @Test
    @DisplayName("GET /api/v1/properties without authentication returns 200 in disabled mode")
    void getPropertiesWithoutAuthenticationReturnsOk() throws Exception {
        mockMvc.perform(get(ApiEndpoints.PROPERTIES_V1 + "/")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /api/v1/users without authentication returns 200 in disabled mode")
    void getUsersWithoutAuthenticationReturnsOk() throws Exception {
        mockMvc.perform(get(ApiEndpoints.USERS_V1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /api/v1/tenants without authentication returns 200 in disabled mode")
    void getTenantsWithoutAuthenticationReturnsOk() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /actuator/env without authentication returns 200 in disabled mode")
    void getActuatorEnvWithoutAuthenticationReturnsOk() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{tenantId}/catalog/request without authentication returns 200 in disabled mode")
    void catalogRequestWithoutAuthenticationReturnsOk() throws Exception {
        populateCatalog();
        uploadFile();

        ResultActions result = mockMvc.perform(post(CATALOG_REQUEST_PATH)
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

    @Test
    @DisplayName("Should not expose /auth controller")
    void authControllerShouldNotBeExposed() throws Exception {
        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/login"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/refresh"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(ApiEndpoints.AUTH_V1 + "/logout"))
                .andExpect(status().isNotFound());
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

        Thread.sleep(2000L);
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
