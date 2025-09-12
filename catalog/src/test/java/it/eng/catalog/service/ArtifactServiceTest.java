package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.model.Artifact;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ArtifactServiceTest {

    @Mock
    private S3ClientService s3ClientService;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private InputStream inputStream;
    @Mock
    private MultipartFile file;
    @Mock
    private ArtifactRepository artifactRepository;
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
        when(file.getInputStream()).thenReturn(inputStream);
        when(s3ClientService.uploadFile(isNull(), anyString(), any(InputStream.class), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("etag"));
        when(artifactRepository.save(any(Artifact.class))).thenReturn(CatalogMockObjectUtil.ARTIFACT_FILE);

        Artifact artifact = artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), file, null, null);

        assertEquals(CatalogMockObjectUtil.ARTIFACT_FILE, artifact);
        verify(s3ClientService).uploadFile(eq(null), anyString(), eq(inputStream), eq(MediaType.APPLICATION_JSON_VALUE), anyString());
    }

    @Test
    @DisplayName("Upload file - fail")
    public void uploadFile_fail() throws IOException {
        when(file.getContentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);
        when(file.getInputStream()).thenReturn(inputStream);
        doThrow(RuntimeException.class).when(s3ClientService).uploadFile(isNull(), anyString(), any(InputStream.class), anyString(), anyString());

        assertThrows(CatalogErrorAPIException.class, () -> artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), file, null, null));
    }

    @Test
    @DisplayName("Upload external - success")
    public void uploadExternal_success() {
        when(artifactRepository.save(any(Artifact.class))).thenReturn(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);

        Artifact artifact = artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), null, CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null);

        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL, artifact);
    }

    @Test
    @DisplayName("Upload no data - fail")
    public void uploadNoData_fail() {
        assertThrows(CatalogErrorAPIException.class, () -> artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), null, null, null));
    }

    @Test
    @DisplayName("Delete artifact file - success")
    public void deleteArtifactFile_success() {
        when(s3Properties.getBucketName()).thenReturn("test-bucket");
        doNothing().when(s3ClientService).deleteFile(anyString(), anyString());
        doNothing().when(artifactRepository).delete(any(Artifact.class));

        assertDoesNotThrow(() -> artifactService.deleteArtifactAfterDatasetUpdate(CatalogMockObjectUtil.ARTIFACT_FILE, CatalogMockObjectUtil.ARTIFACT_EXTERNAL));

        verify(s3ClientService).deleteFile("test-bucket", CatalogMockObjectUtil.ARTIFACT_FILE.getValue());
        verify(artifactRepository).delete(CatalogMockObjectUtil.ARTIFACT_FILE);
    }

    @Test
    @DisplayName("Delete artifact external - success")
    public void deleteArtifactExternal_success() {
        doNothing().when(artifactRepository).delete(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);
        assertDoesNotThrow(() -> artifactService.deleteArtifactAfterDatasetUpdate(CatalogMockObjectUtil.ARTIFACT_EXTERNAL, CatalogMockObjectUtil.ARTIFACT_FILE));

        verify(artifactRepository).delete(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);
    }

}
