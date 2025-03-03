package it.eng.connector.integration.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.wiremock.spring.InjectWireMock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;

import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;

public class ArtifactAPIIntegrationTest extends BaseIntegrationTest {
	
	@Autowired
	private ArtifactRepository artifactRepository;
	
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
		assertTrue(apiRespList.getData().size() == 2);
		
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
		artifactRepository.deleteAll();
	}
}
