package it.eng.catalog.rest.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.service.ArtifactService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
class ArtifactAPIControllerTest {
	
	@Mock
	private ArtifactService artifactService;
	@Mock
	private MultipartFile file;
	@Mock
	private ObjectId objId;
	
	@InjectMocks
	private ArtifactAPIController controller;
	
	@Test
	@DisplayName("Upload artifact - success")
	public void testUploadArtifact() {
		when(artifactService.uploadArtifact(file, CatalogMockObjectUtil.DATASET.getId(), null)).thenReturn(CatalogMockObjectUtil.ARTIFACT_FILE);
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.uploadArtifact(file, null, CatalogMockObjectUtil.DATASET.getId());
		
		assertTrue(StringUtils.isNotBlank(response.getBody().getData().toString()));
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Upload artifact - fail")
	public void testUploadArtifact_failed() {
		when(artifactService.uploadArtifact(file, CatalogMockObjectUtil.DATASET.getId(), null)).thenThrow(CatalogErrorAPIException.class);
		
		assertThrows(CatalogErrorAPIException.class, ()-> controller.uploadArtifact(file, null, CatalogMockObjectUtil.DATASET.getId()));
		
	}

	@Test
	@DisplayName("Get all artifacts - success")
	public void testListArtifacts() {
		when(artifactService.getArtifacts(null))
		.thenReturn(List.of(CatalogMockObjectUtil.ARTIFACT_FILE, CatalogMockObjectUtil.ARTIFACT_EXTERNAL));
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.getArtifacts(null);
		
		
		assertTrue(response.getBody().getData().has(1));
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Get artifact by id - success")
	public void testGetArtifact() {
		when(artifactService.getArtifacts(CatalogMockObjectUtil.ARTIFACT_FILE.getId()))
		.thenReturn(List.of(CatalogMockObjectUtil.ARTIFACT_FILE));
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.getArtifacts(CatalogMockObjectUtil.ARTIFACT_FILE.getId());
		
		
		assertTrue(response.getBody().getData().has(0));
		assertFalse(response.getBody().getData().has(1));
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Delete artifact - success")
	public void testDeleteArtifact() {
		doNothing().when(artifactService).deleteArtifact(CatalogMockObjectUtil.ARTIFACT_FILE.getId());
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.deleteArtifact(CatalogMockObjectUtil.ARTIFACT_FILE.getId());
		
		assertTrue(StringUtils.isNotBlank(response.getBody().getMessage().toString()));
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}
	
	@Test
	@DisplayName("Delete artifact - fail")
	public void testDeleteArtifact_failed() {
		doThrow(CatalogErrorAPIException.class).when(artifactService).deleteArtifact(CatalogMockObjectUtil.ARTIFACT_FILE.getId());
		
		assertThrows(CatalogErrorAPIException.class, ()-> controller.deleteArtifact(CatalogMockObjectUtil.ARTIFACT_FILE.getId()));
		
	}

}
