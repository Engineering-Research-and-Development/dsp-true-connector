package it.eng.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.DataService;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;

@ExtendWith(MockitoExtension.class)
public class DataServiceServiceTest {

    @Mock
    private DataServiceRepository repository;

    @Mock
    private CatalogService catalogService;
    
    @Captor
	private ArgumentCaptor<DataService> argCaptorDataService;

    @InjectMocks
    private DataServiceService dataServiceService;

    private DataService dataService = CatalogMockObjectUtil.DATA_SERVICE;

    @Test
    @DisplayName("Get data service by id - success")
    public void getDataServiceById_success() {
        when(repository.findById(dataService.getId())).thenReturn(Optional.of(dataService));

        DataService result = dataServiceService.getDataServiceById(dataService.getId());

        assertEquals(dataService.getId(), result.getId());
        verify(repository).findById(dataService.getId());
    }

    @Test
    @DisplayName("Get data service by id - not found")
    public void getDataServiceById_notFound() {
        when(repository.findById("1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundAPIException.class, () -> dataServiceService.getDataServiceById("1"));

        verify(repository).findById("1");
    }

    @Test
    @DisplayName("Get all data services")
    public void getAllDataServices_success() {
        dataServiceService.getAllDataServices();
        verify(repository).findAll();
    }

    @Test
    @DisplayName("Save data service")
    public void saveDataService_success() {
        when(repository.save(any(DataService.class))).thenReturn(dataService);

        DataService result = dataServiceService.saveDataService(dataService);

        assertEquals(dataService.getId(), result.getId());
        verify(repository).save(dataService);
        verify(catalogService).updateCatalogDataServiceAfterSave(dataService);
    }

    @Test
    @DisplayName("Delete data service - success")
    public void deleteDataService_success() {
        when(repository.findById(dataService.getId())).thenReturn(Optional.of(dataService));

        dataServiceService.deleteDataService(dataService.getId());

        verify(repository).findById(dataService.getId());
        verify(repository).deleteById(dataService.getId());
        verify(catalogService).updateCatalogDataServiceAfterDelete(dataService);
    }

    @Test
    @DisplayName("Delete data service - not found")
    public void deleteDataService_notFound() {
        when(repository.findById("1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundAPIException.class, () -> dataServiceService.deleteDataService("1"));

        verify(repository).findById("1");
        verify(repository, never()).deleteById("1");
        verify(catalogService, never()).updateCatalogDataServiceAfterDelete(any(DataService.class));
    }

    @Test
    @DisplayName("Update data service - success")
    public void updateDataService_success() {
        when(repository.findById(dataService.getId())).thenReturn(Optional.of(dataService));
        when(repository.save(any(DataService.class))).thenReturn(dataService);

        DataService result = dataServiceService.updateDataService(dataService.getId(), CatalogMockObjectUtil.DATA_SERVICE_FOR_UPDATE);

        assertEquals(dataService.getId(), result.getId());
        verify(repository).findById(dataService.getId());
        verify(repository).save(argCaptorDataService.capture());
        // createor, description, title, serveDataSet
        assertTrue(argCaptorDataService.getValue().getCreator().contains("update"));
        assertTrue(argCaptorDataService.getValue().getTitle().contains("update"));
        assertTrue(argCaptorDataService.getValue().getDescription().stream().filter(d -> d.getValue().contains("update")).findFirst().isPresent());
    }
}
