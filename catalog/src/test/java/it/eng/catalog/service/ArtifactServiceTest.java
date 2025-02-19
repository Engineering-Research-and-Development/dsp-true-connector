package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.model.Artifact;
import it.eng.tools.repository.ArtifactRepository;

@ExtendWith(MockitoExtension.class)
public class ArtifactServiceTest {
	
	@Mock
	private InputStream inputStream;
	@Mock
	private GridFSBucket gridFSBucket;
	@Mock
	private MongoDatabase mongoDatabase;
	@Mock
	private MultipartFile file;
	@Mock
	private MongoTemplate mongoTemplate;
	@Mock
	private DatasetService datasetService;
	@Mock
	private ArtifactRepository artifactRepository;
	@Mock
	private DatasetRepository datasetRepository;

	@InjectMocks
	private ArtifactService artifactService;
	
	@Test
    @DisplayName("Get artifacts by id - success")
    public void getArtifactById_success() {
		when(artifactRepository.findById(CatalogMockObjectUtil.ARTIFACT_FILE.getId())).thenReturn(Optional.of(CatalogMockObjectUtil.ARTIFACT_FILE));

		List<Artifact> result = artifactService.getArtifacts(CatalogMockObjectUtil.ARTIFACT_FILE.getId());

		assertEquals(1, result.size());
        assertEquals(CatalogMockObjectUtil.ARTIFACT_FILE.getId(), result.get(0).getId());
        verify(artifactRepository).findById(CatalogMockObjectUtil.ARTIFACT_FILE.getId());
    }
	
	@Test
    @DisplayName("Get all artifacts - success")
    public void getAllArtifacts_success() {
		when(artifactRepository.findAll())
		.thenReturn(List.of(CatalogMockObjectUtil.ARTIFACT_FILE, CatalogMockObjectUtil.ARTIFACT_EXTERNAL));

		List<Artifact> result = artifactService.getArtifacts(null);

		assertEquals(2, result.size());
        assertEquals(CatalogMockObjectUtil.ARTIFACT_FILE.getId(), result.get(0).getId());
        verify(artifactRepository).findAll();
    }
	
	@Test
    @DisplayName("Upload file - success")
    public void uploadFile_success() throws IOException {
		ObjectId objectId = new ObjectId();
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(file.getContentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);
		when(file.getInputStream()).thenReturn(inputStream);
		when(gridFSBucket.uploadFromStream(anyString(), any(InputStream.class), any(GridFSUploadOptions.class))).thenReturn(objectId);
		when(file.getOriginalFilename()).thenReturn(CatalogMockObjectUtil.ARTIFACT_FILE.getFilename());
		when(artifactRepository.save(any(Artifact.class))).thenReturn(CatalogMockObjectUtil.ARTIFACT_FILE);
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class)) {
			buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
	          .thenReturn(gridFSBucket);

		Artifact artifact = artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET, file, null, null);
		
		assertEquals(artifact, CatalogMockObjectUtil.ARTIFACT_FILE);

		}
    }
	
	@Test
    @DisplayName("Upload file - fail")
    public void uploadFile_fail() throws IOException {
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(file.getContentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);
		when(file.getInputStream()).thenThrow(IOException.class);
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class)) {
			buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
	          .thenReturn(gridFSBucket);

			assertThrows(CatalogErrorAPIException.class, ()-> artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET, file, null, null));
		
		}
    }
	
	@Test
    @DisplayName("Upload external - success")
    public void uploadExternal_success() throws IOException {
		when(artifactRepository.save(any(Artifact.class))).thenReturn(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);

		Artifact artifact = artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET, null, CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null);
		
		assertEquals(artifact, CatalogMockObjectUtil.ARTIFACT_EXTERNAL);

    }
	
	@Test
    @DisplayName("Upload no data - fail")
    public void uploadNoData_fail() throws IOException {

		assertThrows(CatalogErrorAPIException.class, ()-> artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET, null, null, null));
		
    }
	
	@Test
    @DisplayName("Delete artifact file - success")
    public void deleteArtifactFile_success() {
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class)) {
			buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
	          .thenReturn(gridFSBucket);
			
			doNothing().when(gridFSBucket).delete(any(ObjectId.class));
			assertDoesNotThrow(() -> artifactService.deleteOldArtifact(CatalogMockObjectUtil.ARTIFACT_FILE));
		}
    }
	
	@Test
    @DisplayName("Delete artifact without file - success")
    public void deleteArtifactWithoutFile_success() {
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class)) {
			buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
	          .thenReturn(gridFSBucket);
			
			assertDoesNotThrow(() -> artifactService.deleteOldArtifact(CatalogMockObjectUtil.ARTIFACT_FILE));
		}
    }
	
	@Test
    @DisplayName("Delete artifact external - success")
    public void deleteArtifactExternal_success() {
		doNothing().when(artifactRepository).delete(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);
		assertDoesNotThrow(() -> artifactService.deleteOldArtifact(CatalogMockObjectUtil.ARTIFACT_EXTERNAL));
    }
	
}
