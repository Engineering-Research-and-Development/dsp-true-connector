package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
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
	private S3ClientService s3ClientService;
	@Mock
	private S3Properties s3Properties;
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
		when(file.getContentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);
		when(file.getOriginalFilename()).thenReturn(CatalogMockObjectUtil.ARTIFACT_FILE.getFilename());
	    when(s3ClientService.bucketExists(anyString())).thenReturn(false);
	    when(s3Properties.getBucketName()).thenReturn("test-bucket");
	    doNothing().when(s3ClientService).createBucket(anyString());
	    doNothing().when(s3ClientService).uploadFile(anyString(), anyString(), any(), anyString(), anyString());
		when(artifactRepository.save(any(Artifact.class))).thenReturn(CatalogMockObjectUtil.ARTIFACT_FILE);

		Artifact artifact = artifactService.uploadArtifact(file, null, null);
		
	    assertEquals(CatalogMockObjectUtil.ARTIFACT_FILE, artifact);
	    verify(s3ClientService).createBucket("test-bucket");
	    verify(s3ClientService).uploadFile(eq("test-bucket"), anyString(), any(), eq(MediaType.APPLICATION_JSON_VALUE), anyString());
    }
	
	@Test
    @DisplayName("Upload file - fail")
    public void uploadFile_fail() throws IOException {
		when(file.getContentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);
	    when(s3ClientService.bucketExists(anyString())).thenReturn(false);
		doThrow(RuntimeException.class).when(s3ClientService).uploadFile(anyString(), anyString(), any(),anyString(), anyString());
	    when(s3Properties.getBucketName()).thenReturn("test-bucket");
	    doNothing().when(s3ClientService).createBucket(anyString());

		assertThrows(CatalogErrorAPIException.class, ()-> artifactService.uploadArtifact(file, null, null));
    }
	
	@Test
    @DisplayName("Upload external - success")
    public void uploadExternal_success() throws IOException {
		when(artifactRepository.save(any(Artifact.class))).thenReturn(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);

		Artifact artifact = artifactService.uploadArtifact(null, CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null);
		
		assertEquals(artifact, CatalogMockObjectUtil.ARTIFACT_EXTERNAL);

    }
	
	@Test
    @DisplayName("Upload no data - fail")
    public void uploadNoData_fail() throws IOException {
		assertThrows(CatalogErrorAPIException.class, ()-> artifactService.uploadArtifact(null, null, null));
    }
	
	@Test
    @DisplayName("Delete artifact file - success")
    public void deleteArtifactFile_success() {
	    when(s3Properties.getBucketName()).thenReturn("test-bucket");
	    doNothing().when(s3ClientService).deleteFile(anyString(), anyString());
	    doNothing().when(artifactRepository).delete(any(Artifact.class));

			assertDoesNotThrow(() -> artifactService.deleteOldArtifact(CatalogMockObjectUtil.ARTIFACT_FILE));

	    verify(s3ClientService).deleteFile("test-bucket", CatalogMockObjectUtil.ARTIFACT_FILE.getValue());
	    verify(artifactRepository).delete(CatalogMockObjectUtil.ARTIFACT_FILE);
    }
	
	@Test
    @DisplayName("Delete artifact without file - success")
    public void deleteArtifactWithoutFile_success() {
		assertDoesNotThrow(() -> artifactService.deleteOldArtifact(CatalogMockObjectUtil.ARTIFACT_FILE));
    }
	
	@Test
    @DisplayName("Delete artifact external - success")
    public void deleteArtifactExternal_success() {
		doNothing().when(artifactRepository).delete(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);
		assertDoesNotThrow(() -> artifactService.deleteOldArtifact(CatalogMockObjectUtil.ARTIFACT_EXTERNAL));
    }
	
}
