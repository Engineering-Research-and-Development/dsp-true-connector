package it.eng.catalog.rest.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import it.eng.catalog.model.Artifact;
import it.eng.catalog.service.ArtifactService;
import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
class ArtifactAPIControllerTest {
	
	@Mock
	private ArtifactService artifactService;
	@Mock
	private MultipartFile file;
	@Mock
	private ObjectId objId;
	@Mock
	private List<Artifact> gridFSList;
	
	@InjectMocks
	private ArtifactAPIController controller;
	
	@Test
	void testUploadFile() {
		when(artifactService.storeFile(file, MockObjectUtil.DATASET.getId())).thenReturn(objId);
		when(objId.toHexString()).thenReturn("StoredFile");
		ResponseEntity<String> response = controller.uploadFile(file, MockObjectUtil.DATASET.getId());
		assertNotNull(response);
		assertTrue(HttpStatus.OK.equals(response.getStatusCode()));	
	}

	@Test
	void testListArtifacts() {
		when(artifactService.listArtifacts(null)).thenReturn(gridFSList);
		ResponseEntity<GenericApiResponse<List<Artifact>>> response = controller.listArtifacts(null);
		assertNotNull(response);
		assertTrue(HttpStatus.OK.equals(response.getStatusCode()));
	}

}
