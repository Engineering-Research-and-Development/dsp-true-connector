package it.eng.catalog.rest.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.service.ArtifactService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
class ArtifactAPIControllerTest {
	
	@Mock
	private ArtifactService artifactService;
	
	@InjectMocks
	private ArtifactAPIController controller;
	
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
}
