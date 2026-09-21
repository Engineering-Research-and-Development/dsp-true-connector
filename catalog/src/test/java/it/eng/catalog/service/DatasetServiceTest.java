package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.exceptions.InternalServerErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.Artifact;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantBucketResolver;
import it.eng.tools.service.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DatasetServiceTest {

    private static final String TENANT_ID = "engineering";

    @Mock
    private DatasetRepository repository;

    @Mock
    private CatalogService catalogService;

    @Mock
    private ArtifactService artifactService;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private S3ClientService s3ClientService;

    @Mock
    private TenantBucketResolver tenantBucketResolver;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Captor
    private ArgumentCaptor<Dataset> argCaptorDataset;

    @InjectMocks
    private DatasetService datasetService;

    private Dataset datasetWithoutDistributions = Dataset.Builder.newInstance()
            .hasPolicy(Stream.of(CatalogMockObjectUtil.OFFER).collect(Collectors.toCollection(HashSet::new)))
            .build();

    private Dataset datasetWithoutFormats = Dataset.Builder.newInstance()
            .hasPolicy(Stream.of(CatalogMockObjectUtil.OFFER).collect(Collectors.toCollection(HashSet::new)))
            .distribution(Stream.of(CatalogMockObjectUtil.DISTRIBUTION_FOR_UPDATE).collect(Collectors.toCollection(HashSet::new)))
            .build();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Get dataset by id - success")
    public void getDatasetById_success() {
        String bucketName = "test-bucket";
        when(tenantBucketResolver.resolveBucketName()).thenReturn(bucketName);
        when(s3ClientService.listFiles(bucketName))
                .thenReturn(List.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId()));
        when(repository.findByIdAndTenantId(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), TENANT_ID))
                .thenReturn(Optional.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT));

        Dataset result = datasetService.getDatasetById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());

        assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), result.getId());
        verify(repository).findByIdAndTenantId(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), TENANT_ID);
        verify(s3ClientService).listFiles(bucketName);
    }

    @Test
    @DisplayName("Get dataset by id - not found")
    public void getDatasetById_notFound() {
        when(repository.findByIdAndTenantId("1", TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(CatalogErrorException.class, () -> datasetService.getDatasetById("1"));

        verify(repository).findByIdAndTenantId("1", TENANT_ID);
    }

    @Test
    @DisplayName("Get formats from dataset - success")
    public void getFormatsFromDataset_success() {
        when(repository.findByIdAndTenantId(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), TENANT_ID))
                .thenReturn(Optional.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT));

        List<String> formats = datasetService.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());

        assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getDistribution().stream().findFirst().get().getFormat(), formats.get(0));
        verify(repository).findByIdAndTenantId(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), TENANT_ID);
    }

    @Test
    @DisplayName("Get formats from dataset - no distributions found")
    public void getFormatsFromDataset_noDistributionsFound() {
        when(repository.findByIdAndTenantId(datasetWithoutDistributions.getId(), TENANT_ID))
                .thenReturn(Optional.of(datasetWithoutDistributions));

        assertThrows(ResourceNotFoundAPIException.class, () -> datasetService.getFormatsFromDataset(datasetWithoutDistributions.getId()));

        verify(repository).findByIdAndTenantId(datasetWithoutDistributions.getId(), TENANT_ID);
    }

    @Test
    @DisplayName("Get formats from dataset - no formats found")
    public void getFormatsFromDataset_noFormatsFound() {
        when(repository.findByIdAndTenantId(datasetWithoutDistributions.getId(), TENANT_ID))
                .thenReturn(Optional.of(datasetWithoutFormats));

        assertThrows(ResourceNotFoundAPIException.class, () -> datasetService.getFormatsFromDataset(datasetWithoutDistributions.getId()));

        verify(repository).findByIdAndTenantId(datasetWithoutDistributions.getId(), TENANT_ID);
    }

    @Test
    @DisplayName("Get artifact id from dataset - success")
    public void getArtifactIdFromDataset_success() {
        when(repository.findByIdAndTenantId(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), TENANT_ID))
                .thenReturn(Optional.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT));

        Artifact result = datasetService.getArtifactFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());

        assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getArtifact(), result);
        verify(repository).findByIdAndTenantId(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), TENANT_ID);
    }

    @Test
    @DisplayName("Get artifact id from dataset - not found")
    public void getArtifactIdFromDataset_notFound() {
        Dataset mockDataset = mock(Dataset.class);
        when(repository.findByIdAndTenantId(CatalogMockObjectUtil.DATASET.getId(), TENANT_ID))
                .thenReturn(Optional.of(mockDataset));
        when(mockDataset.getArtifact()).thenReturn(null);

        assertThrows(ResourceNotFoundAPIException.class, () -> datasetService.getArtifactFromDataset(CatalogMockObjectUtil.DATASET.getId()));

        verify(repository).findByIdAndTenantId(CatalogMockObjectUtil.DATASET.getId(), TENANT_ID);
    }

    @Test
    @DisplayName("Get all datasets")
    public void getAllDatasets_success() {
        datasetService.getAllDatasets();
        verify(repository).findAllByTenantId(TENANT_ID);
    }

    @Test
    @DisplayName("Save dataset - success")
    public void saveDataset_success() {
        when(repository.save(any(Dataset.class))).thenReturn(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT);
        when(artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), null, CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null))
                .thenReturn(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);

        Dataset result = datasetService.saveDataset(CatalogMockObjectUtil.DATASET, null, CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null);

        assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), result.getId());
        verify(catalogService).updateCatalogDatasetAfterSave(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT);
        verify(repository).save(argCaptorDataset.capture());

        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getId(), argCaptorDataset.getValue().getArtifact().getId());
        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), argCaptorDataset.getValue().getArtifact().getValue());
        assertEquals(CatalogMockObjectUtil.DATASET.getId(), argCaptorDataset.getValue().getId());
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATASET_CREATED), anyString(), any());

    }

    @Test
    @DisplayName("Save dataset - fail - no artifact")
    public void saveDataset_fail() {
        when(artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET.getId(), null, null, null))
                .thenThrow(CatalogErrorAPIException.class);
        assertThrows(InternalServerErrorAPIException.class, () -> datasetService.saveDataset(CatalogMockObjectUtil.DATASET, null, null, null));
    }

    @Test
    @DisplayName("Update dataset - success")
    public void updateDataset_success() {
        when(repository.findByIdAndTenantId(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), TENANT_ID))
                .thenReturn(Optional.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT));
        when(repository.save(any(Dataset.class))).thenReturn(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT);
        when(artifactService.uploadArtifact(eq(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId()), isNull(), anyString(), isNull()))
                .thenReturn(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);

        Dataset result = datasetService.updateDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(),
                CatalogMockObjectUtil.DATASET_FOR_UPDATE,
                null,
                CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(),
                null);

        assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), result.getId());
        verify(repository).findByIdAndTenantId(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), TENANT_ID);
        verify(repository).save(argCaptorDataset.capture());

        assertTrue(argCaptorDataset.getValue().getCreator().contains("update"));
        assertTrue(argCaptorDataset.getValue().getTitle().contains("update"));
        assertTrue(argCaptorDataset.getValue().getDescription().stream().filter(d -> d.getValue().contains("update")).findFirst().isPresent());
        assertTrue(argCaptorDataset.getValue().getHasPolicy().stream().findFirst().get().getId().contains("update"));
        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getId(), argCaptorDataset.getValue().getArtifact().getId());
        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), argCaptorDataset.getValue().getArtifact().getValue());
        assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), argCaptorDataset.getValue().getId());
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATASET_UPDATED), anyString(), any());
    }

    @Test
    @DisplayName("Delete dataset - success")
    public void deleteDataset_success() {
        when(repository.findByIdAndTenantId(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), TENANT_ID))
                .thenReturn(Optional.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT));

        datasetService.deleteDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());

        verify(repository).findByIdAndTenantId(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), TENANT_ID);
        verify(artifactService).deleteArtifact(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getArtifact());
        verify(repository).deleteById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());
        verify(catalogService).updateCatalogDatasetAfterDelete(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT);
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATASET_DELETED), anyString(), any());
    }

    @Test
    @DisplayName("Delete dataset - not found")
    public void deleteDataset_notFound() {
        when(repository.findByIdAndTenantId("1", TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundAPIException.class, () -> datasetService.deleteDataset("1"));

        verify(repository).findByIdAndTenantId("1", TENANT_ID);
        verify(repository, never()).deleteById("1");
        verify(catalogService, never()).updateCatalogDatasetAfterDelete(any(Dataset.class));
    }
}
