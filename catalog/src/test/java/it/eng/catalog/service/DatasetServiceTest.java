package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.exceptions.InternalServerErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.model.Artifact;

@ExtendWith(MockitoExtension.class)
public class DatasetServiceTest {

    @Mock
    private DatasetRepository repository;

    @Mock
    private CatalogService catalogService;
    
    @Mock
    private ArtifactService artifactService;
    
    @Captor
  	private ArgumentCaptor<Dataset> argCaptorDataset;

    @InjectMocks
    private DatasetService datasetService;

    private Dataset datasetWithoutDistributions = Dataset.Builder.newInstance()
    		.hasPolicy(Arrays.asList(CatalogMockObjectUtil.OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
    		.build();
    
    private Dataset datasetWithoutFormats = Dataset.Builder.newInstance()
    		.hasPolicy(Arrays.asList(CatalogMockObjectUtil.OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
    		.distribution(Arrays.asList(CatalogMockObjectUtil.DISTRIBUTION_FOR_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
    		.build();

    @Test
    @DisplayName("Get dataset by id - success")
    public void getDatasetById_success() {
        when(repository.findById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId())).thenReturn(Optional.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT));

        Dataset result = datasetService.getDatasetById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());

        assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), result.getId());
        verify(repository).findById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());
    }

    @Test
    @DisplayName("Get dataset by id - not found")
    public void getDatasetById_notFound() {
        when(repository.findById("1")).thenReturn(Optional.empty());

        assertThrows(CatalogErrorException.class, () -> datasetService.getDatasetById("1"));

        verify(repository).findById("1");
    }
    
    @Test
    @DisplayName("Get formats from dataset - success")
    public void getFormatsFromDataset_success() {
        when(repository.findById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId())).thenReturn(Optional.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT));

        List<String> formats = datasetService.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());

        assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getDistribution().stream().findFirst().get().getFormat().getId(), formats.get(0));
        verify(repository).findById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());
    }

    @Test
    @DisplayName("Get formats from dataset - no distributions found")
    public void getFormatsFromDataset_noDistributionsFound() {
        when(repository.findById(datasetWithoutDistributions.getId())).thenReturn(Optional.of(datasetWithoutDistributions));

        assertThrows(ResourceNotFoundAPIException.class, () -> datasetService.getFormatsFromDataset(datasetWithoutDistributions.getId()));

        verify(repository).findById(datasetWithoutDistributions.getId());
    }
    
    @Test
    @DisplayName("Get formats from dataset - no formats found")
    public void getFormatsFromDataset_noFormatsFound() {
        when(repository.findById(datasetWithoutDistributions.getId())).thenReturn(Optional.of(datasetWithoutFormats));

        assertThrows(ResourceNotFoundAPIException.class, () -> datasetService.getFormatsFromDataset(datasetWithoutDistributions.getId()));

        verify(repository).findById(datasetWithoutDistributions.getId());
    }
    
    @Test
    @DisplayName("Get artifact id from dataset - success")
    public void getArtifactIdFromDataset_success() {
        when(repository.findById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId())).thenReturn(Optional.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT));

        Artifact result = datasetService.getArtifactFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());

        assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getArtifact(), result);
        verify(repository).findById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());
    }

    @Test
    @DisplayName("Get artifact id from dataset - not found")
    public void getArtifactIdFromDataset_notFound() {
    	Dataset mockDataset = mock(Dataset.class);
        when(repository.findById(CatalogMockObjectUtil.DATASET.getId())).thenReturn(Optional.of(mockDataset));
        when(mockDataset.getArtifact()).thenReturn(null);

        assertThrows(ResourceNotFoundAPIException.class, () -> datasetService.getArtifactFromDataset(CatalogMockObjectUtil.DATASET.getId()));

        verify(repository).findById(CatalogMockObjectUtil.DATASET.getId());
    }

    @Test
    @DisplayName("Get all datasets")
    public void getAllDatasets_success() {
        datasetService.getAllDatasets();
        verify(repository).findAll();
    }

    @Test
    @DisplayName("Save dataset - success")
    public void saveDataset_success() {
        when(repository.save(any(Dataset.class))).thenReturn(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT);
        when(artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET, null, CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null))
        .thenReturn(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);

        Dataset result = datasetService.saveDataset(CatalogMockObjectUtil.DATASET, null, CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null);

        assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), result.getId());
        verify(catalogService).updateCatalogDatasetAfterSave(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT);
        verify(repository).save(argCaptorDataset.capture());
        
        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getId(), argCaptorDataset.getValue().getArtifact().getId());
        assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), argCaptorDataset.getValue().getArtifact().getValue());
        assertEquals(CatalogMockObjectUtil.DATASET.getId(), argCaptorDataset.getValue().getId());
        
    }
    
    @Test
    @DisplayName("Save dataset - fail - no artifact")
    public void saveDataset_fail() {
    	when(artifactService.uploadArtifact(CatalogMockObjectUtil.DATASET, null, null, null))
        .thenThrow(CatalogErrorAPIException.class);
    	assertThrows(InternalServerErrorAPIException.class, () -> datasetService.saveDataset(CatalogMockObjectUtil.DATASET, null, null, null));
    }

    @Test
	@DisplayName("Update dataset - success")
	public void updateDataset_success() {
	    when(repository.findById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId())).thenReturn(Optional.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT));
	    when(repository.save(any(Dataset.class))).thenReturn(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT);
	    when(artifactService.uploadArtifact(any(Dataset.class), isNull(), anyString(), isNull()))
	    .thenReturn(CatalogMockObjectUtil.ARTIFACT_EXTERNAL);
	
	    Dataset result = datasetService.updateDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(),
	    		CatalogMockObjectUtil.DATASET_FOR_UPDATE,
	    		null,
	    		CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(),
	    		null);
	
	    assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), result.getId());
	    verify(repository).findById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());
	    verify(repository).save(argCaptorDataset.capture());
	    
	    assertTrue(argCaptorDataset.getValue().getCreator().contains("update"));
	    assertTrue(argCaptorDataset.getValue().getTitle().contains("update"));
	    assertTrue(argCaptorDataset.getValue().getDescription().stream().filter(d -> d.getValue().contains("update")).findFirst().isPresent());
	    assertTrue(argCaptorDataset.getValue().getHasPolicy().stream().findFirst().get().getId().contains("update"));
	    assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getId(), argCaptorDataset.getValue().getArtifact().getId());
	    assertEquals(CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), argCaptorDataset.getValue().getArtifact().getValue());
	    assertEquals(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId(), argCaptorDataset.getValue().getId());
	}

	@Test
    @DisplayName("Delete dataset - success")
    public void deleteDataset_success() {
        when(repository.findById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId())).thenReturn(Optional.of(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT));

        datasetService.deleteDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());

        verify(repository).findById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());
        verify(artifactService).deleteOldArtifact(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getArtifact());
        verify(repository).deleteById(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());
        verify(catalogService).updateCatalogDatasetAfterDelete(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT);
    }

    @Test
    @DisplayName("Delete dataset - not found")
    public void deleteDataset_notFound() {
        when(repository.findById("1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundAPIException.class, () -> datasetService.deleteDataset("1"));

        verify(repository).findById("1");
        verify(repository, never()).deleteById("1");
        verify(catalogService, never()).updateCatalogDatasetAfterDelete(any(Dataset.class));
    }
}
