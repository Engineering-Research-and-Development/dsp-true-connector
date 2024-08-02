package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @InjectMocks
    private DatasetService datasetService;

    private Dataset dataset = MockObjectUtil.DATASET;

    private Dataset updatedDataset = MockObjectUtil.DATASET_FOR_UPDATE;
    
    @Test
    @DisplayName("Get dataset by ID successfully")
    void getDataSetById_success() {
        when(repository.findById(anyString())).thenReturn(Optional.of(dataset));
        Dataset retrievedDataset = datasetService.getDatasetById(dataset.getId());
        assertNotNull(retrievedDataset);
        assertEquals(dataset.getId(), retrievedDataset.getId());
    }
    
    @Test
    @DisplayName("Get dataset by ID throws exception when not found")
    void getDataSetById_notFound() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        assertThrows(CatalogErrorException.class, () -> datasetService.getDatasetById("datasetId"));
    }

    @Test
    @DisplayName("Get dataset by id - success")
    public void getDatasetById_success() {
        when(repository.findById(dataset.getId())).thenReturn(Optional.of(dataset));

        Dataset result = datasetService.getDatasetById(dataset.getId());

        assertEquals(dataset.getId(), result.getId());
        verify(repository).findById(dataset.getId());
    }

    @Test
    @DisplayName("Get dataset by id - not found")
    public void getDatasetById_notFound() {
        when(repository.findById("1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundAPIException.class, () -> datasetService.getDatasetById("1"));

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
        when(repository.save(any(Dataset.class))).thenReturn(dataset);

        Dataset result = datasetService.saveDataset(dataset);

        assertEquals(dataset.getId(), result.getId());
        verify(repository).save(dataset);
        verify(catalogService).updateCatalogDatasetAfterSave(dataset);
    }

    @Test
    @DisplayName("Delete dataset - success")
    public void deleteDataset_success() {
        when(repository.findById(dataset.getId())).thenReturn(Optional.of(dataset));

        datasetService.deleteDataset(dataset.getId());

        verify(repository).findById(dataset.getId());
        verify(repository).deleteById(dataset.getId());
        verify(catalogService).updateCatalogDatasetAfterDelete(dataset);
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
        when(repository.findById(dataset.getId())).thenReturn(Optional.of(dataset));
        when(repository.save(any(Dataset.class))).thenReturn(dataset);

        Dataset result = datasetService.updateDataset(dataset.getId(), updatedDataset);

        assertEquals(dataset.getId(), result.getId());
        verify(repository).findById(dataset.getId());
        verify(repository).save(any(Dataset.class));
    }
}
