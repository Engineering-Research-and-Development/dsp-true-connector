package it.eng.connector.integration.catalog;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CatalogIntegrationTest extends BaseIntegrationTest {

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

    private Catalog catalog;
    private Dataset dataset;
//    private Artifact artifact;

    @BeforeEach
    public void populateCatalog() {
        catalog = CatalogMockObjectUtil.createNewCatalog();
        dataset = catalog.getDataset().stream().findFirst().get();

        catalogRepository.save(catalog);
        datasetRepository.saveAll(catalog.getDataset());
        dataServiceRepository.saveAll(catalog.getService());
        distributionRepository.saveAll(catalog.getDistribution());
        artifactRepository.save(dataset.getArtifact());
    }

    @AfterEach
    public void cleanup() {
        datasetRepository.deleteAll();
        catalogRepository.deleteAll();
        dataServiceRepository.deleteAll();
        distributionRepository.deleteAll();
        artifactRepository.deleteAll();
    }

    @Test
    @DisplayName("Get catalog - success")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getCatalogSuccessfulTest() throws Exception {

        uploadFile();

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);

        final ResultActions result =
                mockMvc.perform(
                        post("/catalog/request")
                                .content(body)
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        Catalog catalogResponse = CatalogSerializer.deserializeProtocol(response, Catalog.class);
        assertNotNull(catalogResponse);
        assertFalse(catalogResponse.getDataset().isEmpty());

//		remove the file from S3 after the test
        removeFiles();
    }

    @Test
    @DisplayName("Get catalog - check if datasets which files are not in S3 are removed from catalog response")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getCatalogWithoutDatasetsThatHaveNoFilesTest() throws Exception {

        uploadFile();

//      check if catalog and dataset are in the database and that the dataset is linked to the file in S3
        assertTrue(catalogRepository.findById(catalog.getId()).isPresent());
        assertTrue(datasetRepository.findById(dataset.getId()).isPresent());
        assertTrue(s3ClientService.listFiles(s3Properties.getBucketName()).stream()
                .anyMatch(file -> file.equals(dataset.getId())));

        removeFiles();

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);

        final ResultActions result =
                mockMvc.perform(
                        post("/catalog/request")
                                .content(body)
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError catalogError = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(catalogError);
    }

    @Test
    @DisplayName("Get catalog - unauthorized")
    public void getCatalogUnauthorizedTest() throws Exception {

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);

        final ResultActions result =
                mockMvc.perform(
                        post("/catalog/request")
                                .content(body)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Basic YXNkckBtYWlsLmNvbTpwYXNzd29yZA=="));
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(error);
    }

    @Test
    @DisplayName("Get catalog - not valid catalog request message")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void notValidCatalogRequestMessageTest() throws Exception {

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);

        final ResultActions result =
                mockMvc.perform(
                        post("/catalog/request")
                                .content(body)
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(error);
        assertNotNull(error.getReason().stream()
                .filter(reason -> reason.getValue().contains("expected dspace:CatalogRequestMessage"))
                .findFirst()
                .get());
    }

    @Test
    @DisplayName("Get dataset - success")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getDatasetSuccessfulTest() throws Exception {
        uploadFile();
        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);

        final ResultActions result =
                mockMvc.perform(
                        get("/catalog/datasets/" + dataset.getId())
                                .content(body)
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        Dataset datasetResponse = CatalogSerializer.deserializeProtocol(response, Dataset.class);
        assertNotNull(datasetResponse);

        removeFiles();
    }

    @Test
    @DisplayName("Get dataset - not valid dataset request message")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void notValidDatasetRequestMessageTest() throws Exception {

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.CATALOG_REQUEST_MESSAGE);

        final ResultActions result =
                mockMvc.perform(
                        get("/catalog/datasets/" + TestUtil.DATASET_ID)
                                .content(body)
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(error);
        assertNotNull(error.getReason().stream()
                .filter(reason -> reason.getValue().contains("expected dspace:DatasetRequestMessage"))
                .findFirst()
                .get());
    }

    @Test
    @DisplayName("Get dataset - no dataset found")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void noDatasetFoundTest() throws Exception {

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);

        final ResultActions result =
                mockMvc.perform(
                        get("/catalog/datasets/1")
                                .content(body)
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(error);
        assertNotNull(error.getReason().stream()
                .filter(reason -> reason.getValue().contains("Dataset with id: 1 not found"))
                .findFirst()
                .get());
    }

    @Test
    @DisplayName("Get dataset - no valid dataset found")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void noValidDatasetFoundTest() throws Exception {

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);

        final ResultActions result =
                mockMvc.perform(
                        get("/catalog/datasets/" + dataset.getId())
                                .content(body)
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(error);
    }

    @Test
    @DisplayName("Get dataset - dataset with null policies")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getDatasetWithNullPoliciesTest() throws Exception {
        // given
        Dataset datasetNullPolicies = Dataset.Builder.newInstance()
                .id("null-policies-dataset")
                .hasPolicy(new HashSet<>())
                .distribution(new HashSet<>(Arrays.asList(CatalogMockObjectUtil.DISTRIBUTION)))
                .build();
        datasetRepository.save(datasetNullPolicies);

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);

        // when
        final ResultActions result = mockMvc.perform(
                get("/catalog/datasets/" + datasetNullPolicies.getId())
                        .content(body)
                        .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(error);
        assertNotNull(error.getReason().stream()
                .filter(reason -> reason.getValue().contains("Dataset with id: " + datasetNullPolicies.getId() + " not found"))
                .findFirst()
                .get());
    }

    @Test
    @DisplayName("Get dataset - dataset without distributions")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getDatasetWithoutDistributionsTest() throws Exception {
        // given
        Dataset datasetNoDistributions = Dataset.Builder.newInstance()
                .id("no-distributions-dataset")
                .hasPolicy(new HashSet<>(Arrays.asList(CatalogMockObjectUtil.OFFER)))
                .build();
        datasetRepository.save(datasetNoDistributions);

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);

        // when
        final ResultActions result = mockMvc.perform(
                get("/catalog/datasets/" + datasetNoDistributions.getId())
                        .content(body)
                        .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(error);
        assertNotNull(error.getReason().stream()
                .filter(reason -> reason.getValue().contains("Dataset with id: " + datasetNoDistributions.getId() + " not found"))
                .findFirst()
                .get());
    }

    @Test
    @DisplayName("Get dataset - dataset with null distributions")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getDatasetWithNullDistributionsTest() throws Exception {
        // given
        Dataset datasetNullDistributions = Dataset.Builder.newInstance()
                .id("null-distributions-dataset")
                .hasPolicy(new HashSet<>(Arrays.asList(CatalogMockObjectUtil.OFFER)))
                .distribution(new HashSet<>())
                .build();
        datasetRepository.save(datasetNullDistributions);

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);

        // when
        final ResultActions result = mockMvc.perform(
                get("/catalog/datasets/" + datasetNullDistributions.getId())
                        .content(body)
                        .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(error);
        assertNotNull(error.getReason().stream()
                .filter(reason -> reason.getValue().contains("Dataset with id: " + datasetNullDistributions.getId() + " not found"))
                .findFirst()
                .get());
    }

    @Test
    @DisplayName("Get dataset - dataset with null data services")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getDatasetWithNullDataServicesTest() throws Exception {
        // given
        Distribution distributionNullServices = Distribution.Builder.newInstance()
                .accessService(new HashSet<>())
                .build();
        Dataset datasetNullServices = Dataset.Builder.newInstance()
                .id("null-services-dataset")
                .hasPolicy(new HashSet<>(Arrays.asList(CatalogMockObjectUtil.OFFER)))
                .distribution(new HashSet<>(Arrays.asList(distributionNullServices)))
                .build();
        datasetRepository.save(datasetNullServices);

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);

        // when
        final ResultActions result = mockMvc.perform(
                get("/catalog/datasets/" + datasetNullServices.getId())
                        .content(body)
                        .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(error);
        assertNotNull(error.getReason().stream()
                .filter(reason -> reason.getValue().contains("Dataset with id: " + datasetNullServices.getId() + " not found"))
                .findFirst()
                .get());
    }

    @Test
    @DisplayName("Get dataset - dataset without file in S3")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void getDatasetWithoutFileInS3Test() throws Exception {
        // given
        Dataset datasetNoFile = Dataset.Builder.newInstance()
                .id("no-file-dataset")
                .hasPolicy(new HashSet<>(Arrays.asList(CatalogMockObjectUtil.OFFER)))
                .distribution(new HashSet<>(Arrays.asList(CatalogMockObjectUtil.DISTRIBUTION)))
                .artifact(CatalogMockObjectUtil.ARTIFACT_FILE)  // Using local artifact type
                .build();
        datasetRepository.save(datasetNoFile);

        String body = CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET_REQUEST_MESSAGE);

        // when
        final ResultActions result = mockMvc.perform(
                get("/catalog/datasets/" + datasetNoFile.getId())
                        .content(body)
                        .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        CatalogError error = CatalogSerializer.deserializeProtocol(response, CatalogError.class);
        assertNotNull(error);
        assertNotNull(error.getReason().stream()
                .filter(reason -> reason.getValue().contains("Dataset with id: " + datasetNoFile.getId() + " not found"))
                .findFirst()
                .get());
    }

    private void uploadFile() throws Exception {
        String fileContent = "Hello, World!";

        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                fileContent.getBytes()
        );

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(file.getOriginalFilename())
                .build();

        try {
            s3ClientService.uploadFile(file.getInputStream(), s3Properties.getBucketName(), dataset.getId(),
                            file.getContentType(), contentDisposition.toString())
                    .get();
        } catch (Exception e) {
            throw new Exception("File storing aborted, " + e.getLocalizedMessage());
        }

        Thread.sleep(2000); // wait for the file to be uploaded to S3
    }

    private void removeFiles() {
        // Ensure the S3 bucket is empty before the test
        List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());
        if (files != null) {
            for (String file : files) {
                s3ClientService.deleteFile(s3Properties.getBucketName(), file);
            }
        }
    }
}
