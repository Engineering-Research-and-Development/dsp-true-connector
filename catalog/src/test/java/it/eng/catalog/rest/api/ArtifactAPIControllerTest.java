package it.eng.catalog.rest.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.ArtifactRequest;
import it.eng.catalog.service.ArtifactService;
import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.model.ArtifactType;
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
	void testUploadFile() {
		ArtifactRequest artifactRequest = new ArtifactRequest(ArtifactType.FILE, null);
		when(artifactService.uploadArtifact(file, MockObjectUtil.DATASET.getId(), artifactRequest)).thenReturn(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE);
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.uploadArtifact(file, MockObjectUtil.DATASET.getId(), artifactRequest);
		
		assertTrue(StringUtils.isNotBlank(response.getBody().getData().toString()));
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	void testListArtifacts() {
		when(artifactService.getArtifacts(null))
		.thenReturn(List.of(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE, it.eng.tools.util.MockObjectUtil.ARTIFACT_EXTERNAL));
		
		ResponseEntity<GenericApiResponse<JsonNode>> response = controller.getArtifacts(null);
		
		assertTrue(StringUtils.isNotBlank(response.getBody().getData().toString()));
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

}
