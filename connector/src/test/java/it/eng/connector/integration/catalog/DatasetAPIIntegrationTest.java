package it.eng.connector.integration.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.wiremock.spring.InjectWireMock;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
public class DatasetAPIIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private S3ClientService s3ClientService;

    @Autowired
    private S3BucketProvisionService s3BucketProvisionService;

    @Autowired
    private S3Properties s3Properties;

    @InjectWireMock
    private WireMockServer wiremock;

    @BeforeEach
    public void cleanup() {
        catalogRepository.deleteAll();
        artifactRepository.deleteAll();
        datasetRepository.deleteAll();
        
        // Ensure S3 bucket is completely clean
        if (s3BucketProvisionService.bucketExists(s3Properties.getBucketName())) {
            List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());
            if (files != null && !files.isEmpty()) {
                for (String file : files) {
                    s3ClientService.deleteFile(s3Properties.getBucketName(), file);
                }
                // Wait for S3 cleanup to complete to ensure test isolation
                waitForS3Cleanup();
            }
        }
    }

    /**
     * Waits for S3 bucket to be completely empty after cleanup.
     * This ensures proper test isolation by waiting for all file deletions to propagate.
     */
    private void waitForS3Cleanup() {
        int maxRetries = 10;
        int delayMs = 100;
        
        for (int i = 0; i < maxRetries; i++) {
            List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());
            if (files == null || files.isEmpty()) {
                log.debug("S3 bucket cleanup completed successfully");
                return;
            }
            log.debug("Waiting for S3 bucket cleanup to complete. Remaining files: {}, Attempt: {}/{}", 
                     files.size(), i + 1, maxRetries);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("S3 cleanup wait was interrupted");
                return;
            }
        }
        log.warn("S3 bucket cleanup did not complete within expected time");
    }

    @Test
    @DisplayName("Dataset API - get by id")
    public void getDatasetById() throws Exception {
        Artifact artifactExternal = Artifact.Builder.newInstance()
                .artifactType(ArtifactType.EXTERNAL)
                .createdBy(CatalogMockObjectUtil.CREATOR)
                .created(CatalogMockObjectUtil.NOW)
                .lastModifiedDate(CatalogMockObjectUtil.NOW)
                .lastModifiedBy(CatalogMockObjectUtil.CREATOR)
                .value("https://example.com/employees")
                .build();

        Artifact artifactFile = Artifact.Builder.newInstance()
                .artifactType(ArtifactType.FILE)
                .contentType(MediaType.APPLICATION_JSON.getType())
                .createdBy(CatalogMockObjectUtil.CREATOR)
                .created(CatalogMockObjectUtil.NOW)
                .lastModifiedDate(CatalogMockObjectUtil.NOW)
                .lastModifiedBy(CatalogMockObjectUtil.CREATOR)
                .filename("Employees.txt")
                .value(new ObjectId().toHexString())
                .build();

        Dataset datasetFile = Dataset.Builder.newInstance()
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .artifact(artifactFile)
                .build();

        Dataset datasetExternal = Dataset.Builder.newInstance()
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .artifact(artifactExternal)
                .build();

        datasetRepository.save(datasetFile);
        datasetRepository.save(datasetExternal);

        TypeReference<GenericApiResponse<List<Dataset>>> typeRef = new TypeReference<GenericApiResponse<List<Dataset>>>() {
        };

        MvcResult resultList = mockMvc.perform(
                        adminGet(ApiEndpoints.CATALOG_DATASETS_V1).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String jsonList = resultList.getResponse().getContentAsString();
        GenericApiResponse<List<Dataset>> apiRespList = CatalogSerializer.deserializePlain(jsonList, typeRef);

        assertNotNull(apiRespList);
        assertNotNull(apiRespList.getData());
        assertTrue(apiRespList.getData().size() >= 2);

    }

    @Test
    @DisplayName("Dataset API - get all")
    public void getAllDatasets() throws Exception {
        Artifact artifactExternal = Artifact.Builder.newInstance()
                .artifactType(ArtifactType.EXTERNAL)
                .createdBy(CatalogMockObjectUtil.CREATOR)
                .created(CatalogMockObjectUtil.NOW)
                .lastModifiedDate(CatalogMockObjectUtil.NOW)
                .lastModifiedBy(CatalogMockObjectUtil.CREATOR)
                .value("https://example.com/employees")
                .build();

        Dataset datasetExternal = Dataset.Builder.newInstance()
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .artifact(artifactExternal)
                .build();

        datasetRepository.save(datasetExternal);

        TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {
        };

        MvcResult resultSingle = mockMvc.perform(
                        adminGet(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + datasetExternal.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String jsonSingle = resultSingle.getResponse().getContentAsString();
        GenericApiResponse<Dataset> apiRespSingle = CatalogSerializer.deserializePlain(jsonSingle, typeRef);

        assertNotNull(apiRespSingle);
        assertTrue(apiRespSingle.isSuccess());
        // Object equals won't work because the db Dataset has version
        assertEquals(datasetExternal.getId(), apiRespSingle.getData().getId());

    }

    @Test
    @DisplayName("Dataset API - get fail")
    public void getDataset_fail() throws Exception {
        MvcResult resultFail = mockMvc.perform(
                        adminGet(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + "1"))
                .andExpect(status().isNotFound())
                .andReturn();

        TypeReference<GenericApiResponse<String>> typeRefFail = new TypeReference<GenericApiResponse<String>>() {
        };

        String jsonFail = resultFail.getResponse().getContentAsString();
        GenericApiResponse<String> apiRespFail = CatalogSerializer.deserializePlain(jsonFail, typeRefFail);

        assertNotNull(apiRespFail);
        assertNull(apiRespFail.getData());
        assertFalse(apiRespFail.isSuccess());
        assertNotNull(apiRespFail.getMessage());

    }

    @Test
    @DisplayName("Dataset API - upload external")
    public void uploadArtifactExternal() throws Exception {
        int initialDatasetSize = datasetRepository.findAll().size();
        int initialArtifactSize = artifactRepository.findAll().size();

        Catalog catalog = Catalog.Builder.newInstance()
                .dataset(Set.of())
                .build();

        catalogRepository.save(catalog);

        Dataset dataset = Dataset.Builder.newInstance()
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .build();


        TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {
        };

        MockPart datasetPart = new MockPart("dataset",
                Objects.requireNonNull(CatalogSerializer.serializePlain(dataset)).getBytes());
        MockPart urlPart = new MockPart("url", CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue().getBytes());

                MvcResult result = mockMvc.perform(
                        multipart(ApiEndpoints.CATALOG_DATASETS_V1)
                                .part(datasetPart).part(urlPart)
                                .headers(adminHeaders()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<Dataset> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        // check if response is correct
        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertEquals(dataset.getId(), apiResp.getData().getId());
        assertTrue(dataset.getHasPolicy().contains(CatalogMockObjectUtil.OFFER));
        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), apiResp.getData().getArtifact().getValue());
        assertEquals(ArtifactType.EXTERNAL, apiResp.getData().getArtifact().getArtifactType());

        // check if the Dataset is inserted in the database
        Dataset datasetFromDb = datasetRepository.findById(dataset.getId()).get();

        assertTrue(datasetFromDb.getHasPolicy().contains(CatalogMockObjectUtil.OFFER));
        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), datasetFromDb.getArtifact().getValue());
        assertEquals(ArtifactType.EXTERNAL, datasetFromDb.getArtifact().getArtifactType());
        assertEquals(initialDatasetSize + 1, datasetRepository.findAll().size());

        // check if the artifact is inserted in the database
        Artifact artifactFromDb = artifactRepository.findById(datasetFromDb.getArtifact().getId()).get();

        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), artifactFromDb.getValue());
        assertEquals(ArtifactType.EXTERNAL, artifactFromDb.getArtifactType());
        assertEquals(initialArtifactSize + 1, artifactRepository.findAll().size());

    }

    @Test
    @DisplayName("Dataset API - upload file")
    public void uploadArtifactFile() throws Exception {
        int initialDatasetSize = datasetRepository.findAll().size();
        int initialArtifactSize = artifactRepository.findAll().size();
        int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();

        Dataset dataset = Dataset.Builder.newInstance()
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .build();
        datasetRepository.save(dataset);

        Catalog catalog = Catalog.Builder.newInstance()
                .dataset(Set.of(dataset))
                .build();

        catalogRepository.save(catalog);

        String fileContent = "Hello, World!";

        MockMultipartFile filePart
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                fileContent.getBytes()
        );

        MockPart datasetPart = new MockPart("dataset",
                Objects.requireNonNull(CatalogSerializer.serializePlain(dataset)).getBytes());


        TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {
        };

        MvcResult result = mockMvc.perform(
                        multipart(ApiEndpoints.CATALOG_DATASETS_V1)
                                .file(filePart).part(datasetPart)
                                .headers(adminHeaders()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<Dataset> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        // check if response is correct
        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertEquals(dataset.getId(), apiResp.getData().getId());
        assertTrue(dataset.getHasPolicy().contains(CatalogMockObjectUtil.OFFER));
        assertEquals(filePart.getOriginalFilename(), apiResp.getData().getArtifact().getFilename());
        assertEquals(ArtifactType.FILE, apiResp.getData().getArtifact().getArtifactType());

        // check if the Dataset is inserted in the database
        Dataset datasetFromDb = datasetRepository.findById(dataset.getId()).get();

        assertTrue(datasetFromDb.getHasPolicy().contains(CatalogMockObjectUtil.OFFER));
        assertEquals(filePart.getOriginalFilename(), datasetFromDb.getArtifact().getFilename());
        assertEquals(ArtifactType.FILE, datasetFromDb.getArtifact().getArtifactType());
        assertEquals(initialDatasetSize + 1, datasetRepository.findAll().size());

        // check if the Artifact is inserted in the database
        Artifact artifactFromDb = artifactRepository.findById(datasetFromDb.getArtifact().getId()).get();

        assertEquals(filePart.getOriginalFilename(), artifactFromDb.getFilename());
        assertEquals(filePart.getContentType(), artifactFromDb.getContentType());
        assertEquals(ArtifactType.FILE, artifactFromDb.getArtifactType());
        assertEquals(initialArtifactSize + 1, artifactRepository.findAll().size());

        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        s3ClientService.downloadFile(s3Properties.getBucketName(), artifactFromDb.getValue(), mockHttpServletResponse);

        ResponseBytes<GetObjectResponse> fileFromStorage = ResponseBytes.fromByteArray(GetObjectResponse.builder()
                        .contentType(mockHttpServletResponse.getContentType())
                        .contentDisposition(mockHttpServletResponse.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                        .build(),
                mockHttpServletResponse.getContentAsByteArray());

        ContentDisposition contentDisposition = ContentDisposition.parse(fileFromStorage.response().contentDisposition());

        assertEquals(filePart.getContentType(), fileFromStorage.response().contentType());
        assertEquals(filePart.getOriginalFilename(), contentDisposition.getFilename());
        assertEquals(fileContent, fileFromStorage.asUtf8String());
        // + 1 from test
        checkIfEndBucketFileCountIsAsExpected(startingBucketFileCount + 1);

    }

    @Test
    @DisplayName("Dataset API - fail, no URL nor File")
    public void uploadArtifactFail() throws Exception {
        Dataset dataset = Dataset.Builder.newInstance()
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .build();

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        MockPart datasetPart = new MockPart("dataset",
                Objects.requireNonNull(CatalogSerializer.serializePlain(dataset)).getBytes());

        MvcResult result = mockMvc.perform(
                        multipart(ApiEndpoints.CATALOG_DATASETS_V1)
                                .part(datasetPart)
                                .headers(adminHeaders()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        // check if response is correct
        assertNotNull(apiResp);
        assertFalse(apiResp.isSuccess());
        assertNull(apiResp.getData());
        assertNotNull(apiResp.getMessage());
    }

    @Test
    @DisplayName("Dataset API - update from external to file")
    public void updateArtifactExternalToFile() throws Exception {
        Artifact artifactExternal = Artifact.Builder.newInstance()
                .artifactType(ArtifactType.EXTERNAL)
                .createdBy(CatalogMockObjectUtil.CREATOR)
                .created(CatalogMockObjectUtil.NOW)
                .lastModifiedDate(CatalogMockObjectUtil.NOW)
                .lastModifiedBy(CatalogMockObjectUtil.CREATOR)
                .value("https://example.com/employees")
                .build();

        Dataset dataset = Dataset.Builder.newInstance()
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .artifact(artifactExternal)
                .build();

        Catalog catalog = Catalog.Builder.newInstance()
                .dataset(Set.of(dataset))
                .build();

        catalogRepository.save(catalog);
        artifactRepository.save(artifactExternal);
        datasetRepository.save(dataset);

        String fileContent = "Hello, World!";

        MockMultipartFile filePart
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                fileContent.getBytes()
        );

        TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {
        };

        MockMultipartHttpServletRequestBuilder builder =
                MockMvcRequestBuilders.multipart(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + dataset.getId());
        builder.with(new RequestPostProcessor() {
            @Override
            public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
                request.setMethod("PUT");
                return request;
            }
        });

        int initialDatasetSize = datasetRepository.findAll().size();
        int initialArtifactSize = artifactRepository.findAll().size();
        int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();

        MvcResult result = mockMvc.perform(
                        builder.file(filePart)
                                .headers(adminHeaders()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<Dataset> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        // check if response is correct
        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertEquals(dataset.getId(), apiResp.getData().getId());
        assertTrue(dataset.getHasPolicy().contains(CatalogMockObjectUtil.OFFER));
        assertEquals(filePart.getOriginalFilename(), apiResp.getData().getArtifact().getFilename());
        assertEquals(ArtifactType.FILE, apiResp.getData().getArtifact().getArtifactType());

        // check if the Dataset is inserted in the database
        Dataset datasetFromDb = datasetRepository.findById(dataset.getId()).get();

        assertTrue(datasetFromDb.getHasPolicy().contains(CatalogMockObjectUtil.OFFER));
        assertEquals(filePart.getOriginalFilename(), datasetFromDb.getArtifact().getFilename());
        assertEquals(ArtifactType.FILE, datasetFromDb.getArtifact().getArtifactType());
        assertEquals(initialDatasetSize, datasetRepository.findAll().size());

        // check if the Artifact is inserted in the database
        Artifact artifactFromDb = artifactRepository.findById(datasetFromDb.getArtifact().getId()).get();

        assertEquals(filePart.getOriginalFilename(), artifactFromDb.getFilename());
        assertEquals(filePart.getContentType(), artifactFromDb.getContentType());
        assertEquals(ArtifactType.FILE, artifactFromDb.getArtifactType());
        assertEquals(initialArtifactSize, artifactRepository.findAll().size());

        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        s3ClientService.downloadFile(s3Properties.getBucketName(), artifactFromDb.getValue(), mockHttpServletResponse);

        ResponseBytes<GetObjectResponse> fileFromStorage = ResponseBytes.fromByteArray(GetObjectResponse.builder()
                        .contentType(mockHttpServletResponse.getContentType())
                        .contentDisposition(mockHttpServletResponse.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                        .build(),
                mockHttpServletResponse.getContentAsByteArray());

        ContentDisposition contentDisposition = ContentDisposition.parse(fileFromStorage.response().contentDisposition());

        assertEquals(filePart.getContentType(), fileFromStorage.response().contentType());
        assertEquals(filePart.getOriginalFilename(), contentDisposition.getFilename());
        assertEquals(fileContent, fileFromStorage.asUtf8String());
        // + 1 from test
        checkIfEndBucketFileCountIsAsExpected(startingBucketFileCount + 1);
    }

    @Test
    @DisplayName("Dataset API - update from file to external")
    public void updateArtifactFileToExternal() throws Exception {

        Dataset dataset = Dataset.Builder.newInstance()
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .build();

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
                    file.getContentType(), contentDisposition.toString());
        } catch (Exception e) {
            throw new Exception("File storing aborted, " + e.getLocalizedMessage());
        }

        Artifact artifactFile = Artifact.Builder.newInstance()
                .artifactType(ArtifactType.FILE)
                .contentType(file.getContentType())
                .createdBy(CatalogMockObjectUtil.CREATOR)
                .created(CatalogMockObjectUtil.NOW)
                .lastModifiedDate(CatalogMockObjectUtil.NOW)
                .lastModifiedBy(CatalogMockObjectUtil.CREATOR)
                .filename(file.getOriginalFilename())
                .value(dataset.getId())
                .build();

        Dataset datasetWithFile = Dataset.Builder.newInstance()
                .id(dataset.getId())
                .hasPolicy(dataset.getHasPolicy())
                .artifact(artifactFile)
                .build();

        Catalog catalog = Catalog.Builder.newInstance()
                .dataset(Set.of(dataset))
                .build();

        catalogRepository.save(catalog);
        artifactRepository.save(artifactFile);
        datasetRepository.save(datasetWithFile);

        MockPart urlPart = new MockPart("url", CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue().getBytes());

        TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {
        };

        int initialDatasetSize = datasetRepository.findAll().size();
        int initialArtifactSize = artifactRepository.findAll().size();

        MockMultipartHttpServletRequestBuilder builder =
                MockMvcRequestBuilders.multipart(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + datasetWithFile.getId());
        builder.with(new RequestPostProcessor() {
            @Override
            public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
                request.setMethod("PUT");
                return request;
            }
        });

        MvcResult result = mockMvc.perform(
                        builder.part(urlPart)
                                .headers(adminHeaders()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<Dataset> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        // check if response is correct
        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertEquals(datasetWithFile.getId(), apiResp.getData().getId());
        assertTrue(datasetWithFile.getHasPolicy().contains(CatalogMockObjectUtil.OFFER));
        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), apiResp.getData().getArtifact().getValue());
        assertEquals(ArtifactType.EXTERNAL, apiResp.getData().getArtifact().getArtifactType());

        // check if the Dataset is inserted in the database
        Dataset datasetFromDb = datasetRepository.findById(datasetWithFile.getId()).get();

        assertTrue(datasetFromDb.getHasPolicy().contains(CatalogMockObjectUtil.OFFER));
        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), datasetFromDb.getArtifact().getValue());
        assertEquals(ArtifactType.EXTERNAL, datasetFromDb.getArtifact().getArtifactType());
        assertEquals(initialDatasetSize, datasetRepository.findAll().size());

        // check if the artifact is inserted in the database
        Artifact artifactFromDb = artifactRepository.findById(datasetFromDb.getArtifact().getId()).get();

        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), artifactFromDb.getValue());
        assertEquals(ArtifactType.EXTERNAL, artifactFromDb.getArtifactType());
        assertEquals(initialArtifactSize, artifactRepository.findAll().size());

        // check if the original file is deleted from S3
        verifyFileDeleted(datasetWithFile.getId());
    }

    @Test
    @DisplayName("Dataset API - delete dataset with file")
    public void deleteDatasetWithFile() throws Exception {

        Dataset dataset = Dataset.Builder.newInstance()
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .build();

        Catalog catalog = Catalog.Builder.newInstance()
                .dataset(Set.of(dataset))
                .build();

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
                    file.getContentType(), contentDisposition.toString());
        } catch (Exception e) {
            throw new Exception("File storing aborted, " + e.getLocalizedMessage());
        }

        Artifact artifactFile = Artifact.Builder.newInstance()
                .artifactType(ArtifactType.FILE)
                .contentType(file.getContentType())
                .createdBy(CatalogMockObjectUtil.CREATOR)
                .created(CatalogMockObjectUtil.NOW)
                .lastModifiedDate(CatalogMockObjectUtil.NOW)
                .lastModifiedBy(CatalogMockObjectUtil.CREATOR)
                .filename(file.getOriginalFilename())
                .value(dataset.getId())
                .build();

        Dataset datasetWithFile = Dataset.Builder.newInstance()
                .id(dataset.getId())
                .hasPolicy(dataset.getHasPolicy())
                .artifact(artifactFile)
                .build();

        artifactRepository.save(artifactFile);
        datasetRepository.save(datasetWithFile);
        catalogRepository.save(catalog);

        int initialDatasetSize = datasetRepository.findAll().size();
        int initialArtifactSize = artifactRepository.findAll().size();

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        MvcResult result = mockMvc.perform(
                        adminDelete(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + datasetWithFile.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        // check if response is correct
        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertNull(apiResp.getData());
        assertNotNull(apiResp.getMessage());

        // check if the Dataset is deleted from the database
        assertEquals(Optional.empty(), datasetRepository.findById(dataset.getId()));
        assertEquals(initialDatasetSize - 1, datasetRepository.findAll().size());

        // check if the artifact is deleted from the database
        assertEquals(Optional.empty(), artifactRepository.findById(artifactFile.getId()));
        assertEquals(initialArtifactSize - 1, artifactRepository.findAll().size());

        // check if the file is deleted from S3 storage
        verifyFileDeleted(datasetWithFile.getId());

    }

    @Test
    @DisplayName("Dataset API - delete dataset with external")
    public void deleteDatasetWithExternal() throws Exception {

        Artifact artifactExternal = Artifact.Builder.newInstance()
                .artifactType(ArtifactType.EXTERNAL)
                .createdBy(CatalogMockObjectUtil.CREATOR)
                .created(CatalogMockObjectUtil.NOW)
                .lastModifiedDate(CatalogMockObjectUtil.NOW)
                .lastModifiedBy(CatalogMockObjectUtil.CREATOR)
                .value("https://example.com/employees")
                .build();

        Dataset dataset = Dataset.Builder.newInstance()
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .artifact(artifactExternal)
                .build();

        Catalog catalog = Catalog.Builder.newInstance()
                .dataset(Set.of(dataset))
                .build();

        catalogRepository.save(catalog);
        artifactRepository.save(artifactExternal);
        datasetRepository.save(dataset);

        int initialDatasetSize = datasetRepository.findAll().size();
        int initialArtifactSize = artifactRepository.findAll().size();

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        MvcResult result = mockMvc.perform(
                        adminDelete(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + dataset.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        // check if response is correct
        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertNull(apiResp.getData());
        assertNotNull(apiResp.getMessage());

        // check if the Dataset is deleted from the database
        assertEquals(Optional.empty(), datasetRepository.findById(dataset.getId()));
        assertEquals(initialDatasetSize - 1, datasetRepository.findAll().size());

        // check if the artifact is deleted from the database
        assertEquals(Optional.empty(), artifactRepository.findById(artifactExternal.getId()));
        assertEquals(initialArtifactSize - 1, artifactRepository.findAll().size());

    }

    @Test
    @DisplayName("Dataset API - delete fail")
    public void deleteDatasetFail() throws Exception {

        int initialDatasetSize = datasetRepository.findAll().size();
        int initialArtifactSize = artifactRepository.findAll().size();

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        MvcResult result = mockMvc.perform(
                        adminDelete(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + 1))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        // check if response is correct
        assertNotNull(apiResp);
        assertFalse(apiResp.isSuccess());
        assertNull(apiResp.getData());
        assertNotNull(apiResp.getMessage());


        // check that the count hasn't changed
        assertEquals(initialDatasetSize, datasetRepository.findAll().size());
        assertEquals(initialArtifactSize, artifactRepository.findAll().size());

    }

    private void checkIfEndBucketFileCountIsAsExpected(int expectedEndBucketFileCount) throws InterruptedException {
        // Wait for S3 to reflect the deletion (max 5 seconds)
        int maxRetries = 10;
        int delayMs = 500;
        // this is to ensure that the test functions correctly since the count will never -10000
        int endBucketFileCount = -10000;
        for (int i = 0; i < maxRetries; i++) {
            endBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();
            if (endBucketFileCount == expectedEndBucketFileCount) break;
            log.debug("Waiting for S3 bucket file count to stabilize. Current count: {}", endBucketFileCount);
            Thread.sleep(delayMs);
        }
        assertEquals(expectedEndBucketFileCount, endBucketFileCount);
    }

    /**
     * Verifies that a specific file has been deleted from S3 storage.
     * This method is more reliable than checking file counts as it directly
     * tests the file deletion rather than relying on potentially inconsistent counts.
     * 
     * @param fileId the ID/key of the file to verify deletion for
     * @throws InterruptedException if the thread is interrupted during waiting
     */
    private void verifyFileDeleted(String fileId) throws InterruptedException {
        int maxRetries = 20; // Increased retries for better reliability
        int delayMs = 250;   // Reduced delay for faster retries
        
        for (int i = 0; i < maxRetries; i++) {
            boolean fileExists = s3ClientService.fileExists(s3Properties.getBucketName(), fileId);
            if (!fileExists) {
                log.debug("File {} successfully deleted from S3", fileId);
                return;
            }
            log.debug("Waiting for file {} to be deleted from S3. Attempt: {}/{}", fileId, i + 1, maxRetries);
            Thread.sleep(delayMs);
        }
        
        // Final assertion with descriptive message
        assertFalse(s3ClientService.fileExists(s3Properties.getBucketName(), fileId), 
                   "File " + fileId + " was not deleted from S3 after " + maxRetries + " retries");
    }
}
