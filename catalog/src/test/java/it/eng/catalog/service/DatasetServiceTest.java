package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.util.MockObjectUtil;

@ExtendWith(MockitoExtension.class)
public class DatasetServiceTest {

    @Mock
    private DatasetRepository repository;

    @Mock
    private CatalogService catalogService;
    
    @Captor
  	private ArgumentCaptor<Dataset> argCaptorDataset;

    @InjectMocks
    private DatasetService datasetService;

    private Dataset datasetWithoutDistributions = Dataset.Builder.newInstance()
    		.hasPolicy(Arrays.asList(MockObjectUtil.OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
    		.build();
    
    private Dataset datasetWithoutFormats = Dataset.Builder.newInstance()
    		.hasPolicy(Arrays.asList(MockObjectUtil.OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
    		.distribution(Arrays.asList(MockObjectUtil.DISTRIBUTION_FOR_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
    		.build();

    @Test
    @DisplayName("Get dataset by id - success")
    public void getDatasetById_success() {
        when(repository.findById(MockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenReturn(Optional.of(MockObjectUtil.DATASET_WITH_FILE_ID));

        Dataset result = datasetService.getDatasetById(MockObjectUtil.DATASET_WITH_FILE_ID.getId());

        assertEquals(MockObjectUtil.DATASET_WITH_FILE_ID.getId(), result.getId());
        verify(repository).findById(MockObjectUtil.DATASET_WITH_FILE_ID.getId());
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
        when(repository.findById(MockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenReturn(Optional.of(MockObjectUtil.DATASET_WITH_FILE_ID));

        List<String> formats = datasetService.getFormatsFromDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId());

        assertEquals(MockObjectUtil.DATASET_WITH_FILE_ID.getDistribution().stream().findFirst().get().getFormat().getId(), formats.get(0));
        verify(repository).findById(MockObjectUtil.DATASET_WITH_FILE_ID.getId());
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
        when(repository.findById(MockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenReturn(Optional.of(MockObjectUtil.DATASET_WITH_FILE_ID));

        String result = datasetService.getArtifactIdFromDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId());

        assertEquals(MockObjectUtil.DATASET_WITH_FILE_ID.getArtifactId(), result);
        verify(repository).findById(MockObjectUtil.DATASET_WITH_FILE_ID.getId());
    }

    @Test
    @DisplayName("Get artifact id from dataset - not found")
    public void getArtifactIdFromDataset_notFound() {
        when(repository.findById(MockObjectUtil.DATASET.getId())).thenReturn(Optional.of(MockObjectUtil.DATASET));

        assertThrows(ResourceNotFoundAPIException.class, () -> datasetService.getArtifactIdFromDataset(MockObjectUtil.DATASET.getId()));

        verify(repository).findById("1");
    }

    @Test
    @DisplayName("Get all datasets")
    public void getAllDatasets_success() {
        datasetService.getAllDatasets();
        verify(repository).findAll();
    }

    @Test
    @DisplayName("Save dataset")
    public void saveDataset_success() {
        when(repository.save(any(Dataset.class))).thenReturn(MockObjectUtil.DATASET_WITH_FILE_ID);

        Dataset result = datasetService.saveDataset(MockObjectUtil.DATASET_WITH_FILE_ID);

        assertEquals(MockObjectUtil.DATASET_WITH_FILE_ID.getId(), result.getId());
        verify(repository).save(MockObjectUtil.DATASET_WITH_FILE_ID);
        verify(catalogService).updateCatalogDatasetAfterSave(MockObjectUtil.DATASET_WITH_FILE_ID);
    }

    @Test
    @DisplayName("Delete dataset - success")
    public void deleteDataset_success() {
        when(repository.findById(MockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenReturn(Optional.of(MockObjectUtil.DATASET_WITH_FILE_ID));

        datasetService.deleteDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId());

        verify(repository).findById(MockObjectUtil.DATASET_WITH_FILE_ID.getId());
        verify(repository).deleteById(MockObjectUtil.DATASET_WITH_FILE_ID.getId());
        verify(catalogService).updateCatalogDatasetAfterDelete(MockObjectUtil.DATASET_WITH_FILE_ID);
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

    @Test
    @DisplayName("Update dataset - success")
    public void updateDataset_success() {
        when(repository.findById(MockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenReturn(Optional.of(MockObjectUtil.DATASET_WITH_FILE_ID));
        when(repository.save(any(Dataset.class))).thenReturn(MockObjectUtil.DATASET_WITH_FILE_ID);

        Dataset result = datasetService.updateDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId(), MockObjectUtil.DATASET_FOR_UPDATE);

        assertEquals(MockObjectUtil.DATASET_WITH_FILE_ID.getId(), result.getId());
        verify(repository).findById(MockObjectUtil.DATASET_WITH_FILE_ID.getId());
        verify(repository).save(argCaptorDataset.capture());
        
        assertTrue(argCaptorDataset.getValue().getCreator().contains("update"));
        assertTrue(argCaptorDataset.getValue().getTitle().contains("update"));
        assertTrue(argCaptorDataset.getValue().getDescription().stream().filter(d -> d.getValue().contains("update")).findFirst().isPresent());
        assertTrue(argCaptorDataset.getValue().getHasPolicy().stream().findFirst().get().getId().contains("update"));
    }
}
