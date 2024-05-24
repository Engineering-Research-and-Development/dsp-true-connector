package it.eng.catalog.service;

import it.eng.catalog.exceptions.DatasetNotFoundAPIException;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.util.MockObjectUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

        assertThrows(DatasetNotFoundAPIException.class, () -> datasetService.getDatasetById("1"));

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

        assertThrows(DatasetNotFoundAPIException.class, () -> datasetService.deleteDataset("1"));

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
