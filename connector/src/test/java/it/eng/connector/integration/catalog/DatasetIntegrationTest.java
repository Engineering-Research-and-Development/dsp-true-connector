package it.eng.connector.integration.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.wiremock.spring.InjectWireMock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;

import it.eng.catalog.model.Dataset;
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

public class DatasetIntegrationTest extends BaseIntegrationTest {
	
	@Autowired
	private ArtifactRepository artifactRepository;
	
	@Autowired
	private DatasetRepository datasetRepository;
	
	@Autowired
	private MongoTemplate mongoTemplate;
	
	@InjectWireMock 
	private WireMockServer wiremock;
	
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
		assertTrue(apiRespList.getData().size() > 2);
		
		// cleanup
		datasetRepository.deleteById(datasetFile.getId());
		datasetRepository.deleteById(datasetExternal.getId());
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
    			get(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + datasetExternal.getId()).contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andReturn();
		
		String jsonSingle = resultSingle.getResponse().getContentAsString();
		GenericApiResponse<Dataset> apiRespSingle = CatalogSerializer.deserializePlain(jsonSingle, typeRef);

		assertNotNull(apiRespSingle);
		assertTrue(apiRespSingle.isSuccess());
		// Object equals won't work because the db Dataset has version
		assertEquals(datasetExternal.getId(), apiRespSingle.getData().getId());
		
		// cleanup
		datasetRepository.deleteById(datasetExternal.getId());
	}
	
	@Test
	@DisplayName("Dataset API - get fail")
	@WithUserDetails(TestUtil.API_USER)
	public void getDataset_fail() throws Exception {		
		MvcResult resultFail = mockMvc.perform(
    			get(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + "1").contentType(MediaType.APPLICATION_JSON))
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
		Dataset dataset = Dataset.Builder.newInstance()
				.hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
				.build();
		
		TypeReference<GenericApiResponse<Dataset>> typeRef = new TypeReference<GenericApiResponse<Dataset>>() {};
		
		MockPart datasetPart = new MockPart("dataset", CatalogSerializer.serializePlain(dataset).getBytes());
		MockPart urlPart = new MockPart("url", CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue().getBytes());
		
		MvcResult result = mockMvc.perform(
				multipart(ApiEndpoints.CATALOG_DATASETS_V1)
				.part(datasetPart).part(urlPart).contentType(MediaType.APPLICATION_JSON))
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
		// 1 from initial data + 1 from test
		assertEquals(2, datasetRepository.findAll().size());
		
		// check if the artifact is inserted in the database
		Artifact artifactFromDb = artifactRepository.findById(datasetFromDb.getArtifact().getId()).get();

		assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), artifactFromDb.getValue());
		assertEquals(ArtifactType.EXTERNAL, artifactFromDb.getArtifactType());
		// 1 from initial data + 1 from test
		assertEquals(2, artifactRepository.findAll().size());

		
		// cleanup
		datasetRepository.deleteById(dataset.getId());
		artifactRepository.deleteById(artifactFromDb.getId());
	}
	
	@Test
	@DisplayName("Dataset API - upload file")
	@WithUserDetails(TestUtil.API_USER)
	public void uploadArtifactFile() throws Exception {
		Dataset dataset = Dataset.Builder.newInstance()
				.hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
				.build();
		datasetRepository.save(dataset);
		
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
				.file(filePart).part(datasetPart).contentType(MediaType.APPLICATION_JSON))
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
		// 1 from initial data + 1 from test
		assertEquals(2, datasetRepository.findAll().size());
		
		// check if the Artifact is inserted in the database
		Artifact artifactFromDb = artifactRepository.findById(datasetFromDb.getArtifact().getId()).get();

		assertEquals(filePart.getOriginalFilename(), artifactFromDb.getFilename());
		assertEquals(filePart.getContentType(), artifactFromDb.getContentType());
		assertEquals(ArtifactType.FILE, artifactFromDb.getArtifactType());
		// 1 from initial data + 1 from test
		assertEquals(2, artifactRepository.findAll().size());
		
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
		assertEquals(2, mongoTemplate.getCollection("fs.files").countDocuments());
		
		// cleanup
		datasetRepository.deleteById(dataset.getId());
		artifactRepository.deleteById(artifactFromDb.getId());
		ObjectId objectId = new ObjectId(artifactFromDb.getValue());
		gridFSBucket.delete(objectId);
	}

}
