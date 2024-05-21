package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.util.MockObjectUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CatalogServiceTest {

    @Mock
    private CatalogRepository repository;

    @InjectMocks
    private CatalogService service;

    private Catalog catalog = MockObjectUtil.CATALOG;

    @Test
    @DisplayName("Save catalog successfully")
    void saveCatalog_success() {
        when(repository.save(any(Catalog.class))).thenReturn(catalog);
        Catalog savedCatalog = service.saveCatalog(catalog);
        assertNotNull(savedCatalog);
        verify(repository).save(catalog);
    }

    @Test
    @DisplayName("Get catalog successfully")
    void getCatalog_success() {
        when(repository.findAll()).thenReturn(Collections.singletonList(catalog));
        Catalog retrievedCatalog = service.getCatalog();
        assertNotNull(retrievedCatalog);
        verify(repository).findAll();
    }

    @Test
    @DisplayName("Get catalog throws exception when not found")
    void getCatalog_notFound() {
        when(repository.findAll()).thenReturn(Collections.emptyList());
        assertThrows(CatalogErrorException.class, () -> service.getCatalog());
    }

    @Test
    @DisplayName("Get catalog by ID successfully")
    void getCatalogById_success() {
        when(repository.findById(anyString())).thenReturn(Optional.of(catalog));
        Optional<Catalog> retrievedCatalog = service.getCatalogById(catalog.getId());
        assertTrue(retrievedCatalog.isPresent());
        verify(repository).findById(catalog.getId());
    }

    @Test
    @DisplayName("Get dataset by ID successfully")
    void getDataSetById_success() {
        catalog.getDataset();
        when(repository.findCatalogByDatasetId(anyString())).thenReturn(Optional.of(catalog));
        Dataset retrievedDataset = service.getDataSetById(MockObjectUtil.DATASET.getId());
        assertNotNull(retrievedDataset);
        assertEquals(MockObjectUtil.DATASET.getId(), retrievedDataset.getId());
    }

    @Test
    @DisplayName("Get dataset by ID throws exception when not found")
    void getDataSetById_notFound() {
        when(repository.findCatalogByDatasetId(anyString())).thenReturn(Optional.empty());
        assertThrows(CatalogErrorException.class, () -> service.getDataSetById("datasetId"));
    }

    @Test
    @DisplayName("Delete catalog successfully")
    void deleteCatalog_success() {
        service.deleteCatalog(catalog.getId());
        verify(repository).deleteById(catalog.getId());
    }

    @Test
    @DisplayName("Update catalog successfully")
    void updateCatalog_success() {
        when(repository.findById(anyString())).thenReturn(Optional.of(catalog));
        when(repository.save(any(Catalog.class))).thenReturn(catalog);

        Catalog updatedCatalogData = MockObjectUtil.CATALOG_FOR_UPDATE;

        Catalog updatedCatalog = service.updateCatalog(catalog.getId(), updatedCatalogData);
        assertNotNull(updatedCatalog);
        verify(repository).findById(catalog.getId());
        verify(repository).save(any(Catalog.class));
    }


    @Test
    @DisplayName("Update catalog data service after delete successfully")
    void updateCatalogDataServiceAfterDelete_success() {
        DataService dataService = MockObjectUtil.DATA_SERVICE;
        when(repository.findAll()).thenReturn(Collections.singletonList(catalog));
        when(repository.save(any(Catalog.class))).thenReturn(catalog);

        service.updateCatalogDataServiceAfterDelete(dataService);

        verify(repository).save(any(Catalog.class));
    }
}