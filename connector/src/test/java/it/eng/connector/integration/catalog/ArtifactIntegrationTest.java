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
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;

public class ArtifactIntegrationTest extends BaseIntegrationTest {
	
	@Autowired
	private ArtifactRepository artifactRepository;
	
	@Autowired
	private DatasetRepository datasetRepository;
	
	@Autowired
	private MongoTemplate mongoTemplate;
	
	@InjectWireMock 
	private WireMockServer wiremock;
	
	@Test
	@DisplayName("Artifact API - get")
	@WithUserDetails(TestUtil.API_USER)
	public void getArtifact() throws Exception {
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
		
		artifactRepository.save(artifactFile);
		artifactRepository.save(artifactExternal);
		
		TypeReference<GenericApiResponse<List<Artifact>>> typeRef = new TypeReference<GenericApiResponse<List<Artifact>>>() {};
		
		MvcResult resultList = mockMvc.perform(
    			get(ApiEndpoints.CATALOG_ARTIFACT_V1))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String jsonList = resultList.getResponse().getContentAsString();
		GenericApiResponse<List<Artifact>> apiRespList =  ToolsSerializer.deserializePlain(jsonList, typeRef);
		  
		assertNotNull(apiRespList.getData());
		assertTrue(apiRespList.getData().size() > 2);
		
		MvcResult resultSingle = mockMvc.perform(
    			get(ApiEndpoints.CATALOG_ARTIFACT_V1 + "/" + artifactFile.getId()).contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andReturn();
		
		String jsonSingle = resultSingle.getResponse().getContentAsString();
		GenericApiResponse<List<Artifact>> apiRespSingle = ToolsSerializer.deserializePlain(jsonSingle, typeRef);

		assertNotNull(apiRespSingle);
		assertTrue(apiRespSingle.isSuccess());
		assertEquals(1, apiRespSingle.getData().size());
		// Object equals won't work because the db Artifact has version and the static Artifact doesn't
		assertEquals(artifactFile.getId(), apiRespSingle.getData().get(0).getId());
		
		//fail scenario
		
		MvcResult resultFail = mockMvc.perform(
    			get(ApiEndpoints.CATALOG_ARTIFACT_V1 + "/" + "1").contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNotFound())
			.andReturn();
		
		TypeReference<GenericApiResponse<String>> typeRefFail = new TypeReference<GenericApiResponse<String>>() {};

		String jsonFail = resultFail.getResponse().getContentAsString();
		GenericApiResponse<String> apiRespFail = ToolsSerializer.deserializePlain(jsonFail, typeRefFail);
		
		assertNotNull(apiRespFail);
		assertNull(apiRespFail.getData());
		assertFalse(apiRespFail.isSuccess());
		assertNotNull(apiRespFail.getMessage());
		
		// cleanup
		artifactRepository.deleteById(artifactFile.getId());
		artifactRepository.deleteById(artifactExternal.getId());
	}
	
	@Test
	@DisplayName("Artifact API - upload external")
	@WithUserDetails(TestUtil.API_USER)
	public void uploadArtifactExternal() throws Exception {
		Dataset dataset = Dataset.Builder.newInstance()
				.hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
				.build();
		datasetRepository.save(dataset);
		
		TypeReference<GenericApiResponse<Artifact>> typeRef = new TypeReference<GenericApiResponse<Artifact>>() {};
		
		MockPart part = new MockPart("url", CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue().getBytes());
		
		MvcResult result = mockMvc.perform(
				multipart(ApiEndpoints.CATALOG_ARTIFACT_V1 + "/upload/" + dataset.getId())
				.part(part))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String json = result.getResponse().getContentAsString();
		GenericApiResponse<Artifact> apiResp =  ToolsSerializer.deserializePlain(json, typeRef);
		
		// check if response is correct
		assertTrue(apiResp.isSuccess());
		assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), apiResp.getData().getValue());
		assertEquals(ArtifactType.EXTERNAL, apiResp.getData().getArtifactType());

		// check if the artifact is inserted in the database
		Artifact artifactFromDb = artifactRepository.findById(apiResp.getData().getId()).get();

		assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), artifactFromDb.getValue());
		assertEquals(ArtifactType.EXTERNAL, artifactFromDb.getArtifactType());

		// check if the Dataset is updated with the inserted Artifact
		Dataset datasetFromDb = datasetRepository.findById(dataset.getId()).get();

		assertEquals(artifactFromDb.getId(), datasetFromDb.getArtifact().getId());
		
		// cleanup
		datasetRepository.deleteById(dataset.getId());
		artifactRepository.deleteById(apiResp.getData().getId());
	}
	
	@Test
	@DisplayName("Artifact API - upload file")
	@WithUserDetails(TestUtil.API_USER)
	public void uploadArtifactFile() throws Exception {
		Dataset dataset = Dataset.Builder.newInstance()
				.hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
				.build();
		datasetRepository.save(dataset);
		
		TypeReference<GenericApiResponse<Artifact>> typeRef = new TypeReference<GenericApiResponse<Artifact>>() {};
		
		String fileContent = "Hello, World!";
		
		MockMultipartFile file 
	      = new MockMultipartFile(
	        "file", 
	        "hello.txt", 
	        MediaType.TEXT_PLAIN_VALUE, 
	        fileContent.getBytes()
	      );
		
		MvcResult result = mockMvc.perform(
				multipart(ApiEndpoints.CATALOG_ARTIFACT_V1 + "/upload/" + dataset.getId())
				.file(file))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		
		String json = result.getResponse().getContentAsString();
		GenericApiResponse<Artifact> apiResp =  ToolsSerializer.deserializePlain(json, typeRef);
		  
		// check if response is correct
		assertTrue(apiResp.isSuccess());
		assertEquals(file.getOriginalFilename(), apiResp.getData().getFilename());
		assertEquals(ArtifactType.FILE, apiResp.getData().getArtifactType());
		
		// check if the artifact is inserted in the database
		Artifact artifactFromDb = artifactRepository.findById(apiResp.getData().getId()).get();

		assertEquals(file.getOriginalFilename(), artifactFromDb.getFilename());
		assertEquals(file.getContentType(), artifactFromDb.getContentType());
		assertEquals(ArtifactType.FILE, artifactFromDb.getArtifactType());

		// check if the Dataset is updated with the inserted Artifact
		Dataset datasetFromDb = datasetRepository.findById(dataset.getId()).get();

		assertEquals(artifactFromDb.getId(), datasetFromDb.getArtifact().getId());
		
		// check if the file is inserted in the database
		GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
		ObjectId fileIdentifier = new ObjectId(artifactFromDb.getValue());
		Bson query = Filters.eq("_id", fileIdentifier);
		GridFSFile fileInDb = gridFSBucket.find(query).first();
		GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(fileInDb.getObjectId());
		GridFsResource gridFsResource = new GridFsResource(fileInDb, gridFSDownloadStream);
		
		assertEquals(file.getContentType(), gridFsResource.getContentType());
		assertEquals(file.getOriginalFilename(), gridFsResource.getFilename());
		assertEquals(fileContent, gridFsResource.getContentAsString(StandardCharsets.UTF_8));
		
		// cleanup
		datasetRepository.deleteById(dataset.getId());
		artifactRepository.deleteById(apiResp.getData().getId());
	}
}
