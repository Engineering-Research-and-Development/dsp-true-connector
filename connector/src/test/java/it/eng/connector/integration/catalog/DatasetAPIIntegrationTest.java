package it.eng.connector.integration.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.wiremock.spring.InjectWireMock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;

public class DatasetAPIIntegrationTest extends BaseIntegrationTest {
	
	@Autowired
	private CatalogRepository catalogRepository;
	
	@Autowired
	private ArtifactRepository artifactRepository;
	
	@Autowired
	private DatasetRepository datasetRepository;
	
	@Autowired
	private MongoTemplate mongoTemplate;
	
	@InjectWireMock 
	private WireMockServer wiremock;
	
	private static final String CONTENT_TYPE_FIELD = "_contentType";
	private static final String DATASET_ID_METADATA = "datasetId";
	
	@AfterEach
	private void cleanup() {
		catalogRepository.deleteAll();
		artifactRepository.deleteAll();
		datasetRepository.deleteAll();
		mongoTemplate.getCollection(FS_FILES).drop();
		mongoTemplate.getCollection(FS_CHUNKS).drop();
	}
	
	@Test
	@DisplayName("Dataset API - get by id")
	@WithUserDetails(TestUtil.API_USER)
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
		
		TypeReference<GenericApiResponse<List<Dataset>>> typeRef = new TypeReference<GenericApiResponse<List<Dataset>>>() {};
		
		MvcResult resultList = mockMvc.perform(
    			get(ApiEndpoints.CATALOG_DATASETS_V1).contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String jsonList = resultList.getResponse().getContentAsString();
		GenericApiResponse<List<Dataset>> apiRespList =  CatalogSerializer.deserializePlain(jsonList, typeRef);
		  
		assertNotNull(apiRespList.getData());
		assertTrue(apiRespList.getData().size() >= 2);
		
	}
	
	@Test
	@DisplayName("Dataset API - get all")
	@WithUserDetails(TestUtil.API_USER)
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
		
		TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {};
		
		MvcResult resultSingle = mockMvc.perform(
    			get(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + datasetExternal.getId()))
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
	@WithUserDetails(TestUtil.API_USER)
	public void getDataset_fail() throws Exception {		
		MvcResult resultFail = mockMvc.perform(
    			get(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + "1"))
			.andExpect(status().isNotFound())
			.andReturn();
		
		TypeReference<GenericApiResponse<String>> typeRefFail = new TypeReference<GenericApiResponse<String>>() {};

		String jsonFail = resultFail.getResponse().getContentAsString();
		GenericApiResponse<String> apiRespFail = CatalogSerializer.deserializePlain(jsonFail, typeRefFail);
		
		assertNotNull(apiRespFail);
		assertNull(apiRespFail.getData());
		assertFalse(apiRespFail.isSuccess());
		assertNotNull(apiRespFail.getMessage());
		
	}
	
	@Test
	@DisplayName("Dataset API - upload external")
	@WithUserDetails(TestUtil.API_USER)
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
		
		
		TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {};
		
		MockPart datasetPart = new MockPart("dataset", CatalogSerializer.serializePlain(dataset).getBytes());
		MockPart urlPart = new MockPart("url", CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue().getBytes());
		
		MvcResult result = mockMvc.perform(
				multipart(ApiEndpoints.CATALOG_DATASETS_V1)
				.part(datasetPart).part(urlPart))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String json = result.getResponse().getContentAsString();
		GenericApiResponse<Dataset> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
		
		// check if response is correct
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
	@WithUserDetails(TestUtil.API_USER)
	public void uploadArtifactFile() throws Exception {
		int initialDatasetSize = datasetRepository.findAll().size();
		int initialArtifactSize = artifactRepository.findAll().size();
		long initialFileSize = mongoTemplate.getCollection(FS_FILES).countDocuments();
		
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
		
		MockPart datasetPart = new MockPart("dataset", CatalogSerializer.serializePlain(dataset).getBytes());

		
		TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {};
		
		MvcResult result = mockMvc.perform(
				multipart(ApiEndpoints.CATALOG_DATASETS_V1)
				.file(filePart).part(datasetPart))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String json = result.getResponse().getContentAsString();
		GenericApiResponse<Dataset> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
		  
		// check if response is correct
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
		
		// check if the file is inserted in the database
		GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
		ObjectId fileIdentifier = new ObjectId(artifactFromDb.getValue());
		Bson query = Filters.eq("_id", fileIdentifier);
		GridFSFile fileInDb = gridFSBucket.find(query).first();
		GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(fileInDb.getObjectId());
		GridFsResource gridFsResource = new GridFsResource(fileInDb, gridFSDownloadStream);
		
		assertEquals(filePart.getContentType(), gridFsResource.getContentType());
		assertEquals(filePart.getOriginalFilename(), gridFsResource.getFilename());
		assertEquals(fileContent, gridFsResource.getContentAsString(StandardCharsets.UTF_8));
		// 1 from initial data + 1 from test
		assertEquals(initialFileSize + 1, mongoTemplate.getCollection(FS_FILES).countDocuments());
		
	}

	@Test
	@DisplayName("Dataset API - fail, no URL nor File")
	@WithUserDetails(TestUtil.API_USER)
	public void uploadArtifactFail() throws Exception {
		Dataset dataset = Dataset.Builder.newInstance()
				.hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
				.build();
		
		TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {};
		
		MockPart datasetPart = new MockPart("dataset", CatalogSerializer.serializePlain(dataset).getBytes());
		
		MvcResult result = mockMvc.perform(
				multipart(ApiEndpoints.CATALOG_DATASETS_V1)
				.part(datasetPart))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String json = result.getResponse().getContentAsString();
		GenericApiResponse<String> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
		
		// check if response is correct
		assertFalse(apiResp.isSuccess());
		assertNull(apiResp.getData());
		assertNotNull(apiResp.getMessage());
	}
	
	@Test
	@DisplayName("Dataset API - update from external to file")
	@WithUserDetails(TestUtil.API_USER)
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
		
		artifactRepository.save(artifactExternal);
		datasetRepository.save(dataset);
		
		int initialDatasetSize = datasetRepository.findAll().size();
		int initialArtifactSize = artifactRepository.findAll().size();
		long initialFileSize = mongoTemplate.getCollection(FS_FILES).countDocuments();
		
		String fileContent = "Hello, World!";
		
		MockMultipartFile filePart 
		= new MockMultipartFile(
				"file", 
				"hello.txt", 
				MediaType.TEXT_PLAIN_VALUE, 
				fileContent.getBytes()
				);
		
		TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {};
		
		MockMultipartHttpServletRequestBuilder builder =
		            MockMvcRequestBuilders.multipart(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + dataset.getId());
		    builder.with(new RequestPostProcessor() {
		        @Override
		        public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
		            request.setMethod("PUT");
		            return request;
		        }
		    });
		    
		MvcResult result = mockMvc.perform(
				builder.file(filePart))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String json = result.getResponse().getContentAsString();
		GenericApiResponse<Dataset> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
		  
		// check if response is correct
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
		
		// check if the file is inserted in the database
		GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
		ObjectId fileIdentifier = new ObjectId(artifactFromDb.getValue());
		Bson query = Filters.eq("_id", fileIdentifier);
		GridFSFile fileInDb = gridFSBucket.find(query).first();
		GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(fileInDb.getObjectId());
		GridFsResource gridFsResource = new GridFsResource(fileInDb, gridFSDownloadStream);
		
		assertEquals(filePart.getContentType(), gridFsResource.getContentType());
		assertEquals(filePart.getOriginalFilename(), gridFsResource.getFilename());
		assertEquals(fileContent, gridFsResource.getContentAsString(StandardCharsets.UTF_8));
		assertEquals(initialFileSize + 1, mongoTemplate.getCollection(FS_FILES).countDocuments());
		
	}
	
	@Test
	@DisplayName("Dataset API - update from file to external")
	@WithUserDetails(TestUtil.API_USER)
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
		
		GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
		Document doc = new Document();
		//TODO check what happens if file.getContentType() is null
		doc.append(CONTENT_TYPE_FIELD, file.getContentType());
		doc.append(DATASET_ID_METADATA, dataset.getId());
		GridFSUploadOptions options = new GridFSUploadOptions()
				.chunkSizeBytes(1048576) // 1MB chunk size
				.metadata(doc);
		ObjectId fileId = gridFSBucket.uploadFromStream(file.getOriginalFilename(), file.getInputStream(), options);
		
		Artifact artifactFile = Artifact.Builder.newInstance()
				.artifactType(ArtifactType.FILE)
				.contentType(file.getContentType())
				.createdBy(CatalogMockObjectUtil.CREATOR)
				.created(CatalogMockObjectUtil.NOW)
				.lastModifiedDate(CatalogMockObjectUtil.NOW)
				.lastModifiedBy(CatalogMockObjectUtil.CREATOR)
				.filename(file.getOriginalFilename())
				.value(fileId.toHexString())
				.build();
		
		Dataset datasetWithFile = Dataset.Builder.newInstance()
				.id(dataset.getId())
				.hasPolicy(dataset.getHasPolicy())
				.artifact(artifactFile)
				.build();
		
		artifactRepository.save(artifactFile);
		datasetRepository.save(datasetWithFile);
		
		int initialDatasetSize = datasetRepository.findAll().size();
		int initialArtifactSize = artifactRepository.findAll().size();
		long initialFileSize = mongoTemplate.getCollection(FS_FILES).countDocuments();
		
		MockPart urlPart = new MockPart("url", CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue().getBytes());
		
		TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {};
		
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
				builder.part(urlPart))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String json = result.getResponse().getContentAsString();
		GenericApiResponse<Dataset> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
		  
		// check if response is correct
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

		// check if the file is inserted in the database and old one removed
		assertEquals(initialFileSize - 1, mongoTemplate.getCollection(FS_FILES).countDocuments());
		
	}
	
	@Test
	@DisplayName("Dataset API - delete dataset with file")
	@WithUserDetails(TestUtil.API_USER)
	public void deleteDatasetWithFile() throws Exception {
		int initialDatasetSize = datasetRepository.findAll().size();
		int initialArtifactSize = artifactRepository.findAll().size();
		long initialFileSize = mongoTemplate.getCollection(FS_FILES).countDocuments();
		
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
		
		GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
		Document doc = new Document();
		//TODO check what happens if file.getContentType() is null
		doc.append(CONTENT_TYPE_FIELD, file.getContentType());
		doc.append(DATASET_ID_METADATA, dataset.getId());
		GridFSUploadOptions options = new GridFSUploadOptions()
				.chunkSizeBytes(1048576) // 1MB chunk size
				.metadata(doc);
		ObjectId fileId = gridFSBucket.uploadFromStream(file.getOriginalFilename(), file.getInputStream(), options);
		
		Artifact artifactFile = Artifact.Builder.newInstance()
				.artifactType(ArtifactType.FILE)
				.contentType(file.getContentType())
				.createdBy(CatalogMockObjectUtil.CREATOR)
				.created(CatalogMockObjectUtil.NOW)
				.lastModifiedDate(CatalogMockObjectUtil.NOW)
				.lastModifiedBy(CatalogMockObjectUtil.CREATOR)
				.filename(file.getOriginalFilename())
				.value(fileId.toHexString())
				.build();
		
		Dataset datasetWithFile = Dataset.Builder.newInstance()
				.id(dataset.getId())
				.hasPolicy(dataset.getHasPolicy())
				.artifact(artifactFile)
				.build();
		
		artifactRepository.save(artifactFile);
		datasetRepository.save(datasetWithFile);
		catalogRepository.save(catalog);
		
		
		TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {};
		
		MvcResult result = mockMvc.perform(
				delete(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + datasetWithFile.getId()))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String json = result.getResponse().getContentAsString();
		GenericApiResponse<String> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
		  
		// check if response is correct
		assertTrue(apiResp.isSuccess());
		assertNull(apiResp.getData());
		assertNotNull(apiResp.getMessage());

		// check if the Dataset is deleted from the database
		assertEquals(Optional.empty(), datasetRepository.findById(dataset.getId()));
		assertEquals(initialDatasetSize, datasetRepository.findAll().size());

		// check if the artifact is deleted from the database
		assertEquals(Optional.empty(), artifactRepository.findById(artifactFile.getId()));
		assertEquals(initialArtifactSize, artifactRepository.findAll().size());

		// check if the file is deleted from the database
		assertEquals(initialFileSize, mongoTemplate.getCollection(FS_FILES).countDocuments());
		
	}
	
	@Test
	@DisplayName("Dataset API - delete dataset with external")
	@WithUserDetails(TestUtil.API_USER)
	public void deleteDatasetWithExternal() throws Exception {
		int initialDatasetSize = datasetRepository.findAll().size();
		int initialArtifactSize = artifactRepository.findAll().size();
		
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
		
		
		TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {};
		
		MvcResult result = mockMvc.perform(
				delete(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + dataset.getId()))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String json = result.getResponse().getContentAsString();
		GenericApiResponse<String> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
		
		// check if response is correct
		assertTrue(apiResp.isSuccess());
		assertNull(apiResp.getData());
		assertNotNull(apiResp.getMessage());

		// check if the Dataset is deleted from the database
		assertEquals(Optional.empty(), datasetRepository.findById(dataset.getId()));
		assertEquals(initialDatasetSize, datasetRepository.findAll().size());

		// check if the artifact is deleted from the database
		assertEquals(Optional.empty(), artifactRepository.findById(artifactExternal.getId()));
		assertEquals(initialArtifactSize, artifactRepository.findAll().size());

	}
	
	@Test
	@DisplayName("Dataset API - delete fail")
	@WithUserDetails(TestUtil.API_USER)
	public void deleteDatasetFail() throws Exception {
		
		int initialDatasetSize = datasetRepository.findAll().size();
		int initialArtifactSize = artifactRepository.findAll().size();
		long initialFileSize = mongoTemplate.getCollection(FS_FILES).countDocuments();
		
		TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {};
		
		MvcResult result = mockMvc.perform(
				delete(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + 1))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String json = result.getResponse().getContentAsString();
		GenericApiResponse<String> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
		
		// check if response is correct
		assertFalse(apiResp.isSuccess());
		assertNull(apiResp.getData());
		assertNotNull(apiResp.getMessage());


		// check that the count hasn't changed
		assertEquals(initialDatasetSize, datasetRepository.findAll().size());
		assertEquals(initialArtifactSize, artifactRepository.findAll().size());
		assertEquals(initialFileSize, mongoTemplate.getCollection(FS_FILES).countDocuments());

	}
	

}
