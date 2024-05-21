package it.eng.catalog.service;

import it.eng.catalog.exceptions.DataServiceNotFoundAPIException;
import it.eng.catalog.model.DataService;
import it.eng.catalog.repository.DataServiceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The DataServiceService class provides methods to interact with DataService data, including saving, retrieving, and deleting dataServices.
 */
@Service
public class DataServiceService {

    private final DataServiceRepository repository;
    private final CatalogService catalogService;

    public DataServiceService(DataServiceRepository repository, CatalogService catalogService) {
        this.repository = repository;
        this.catalogService = catalogService;
    }

    /**
     * Retrieves a data service by its unique ID.
     *
     * @param id the unique ID of the data service
     * @return the dataService corresponding to the provided ID
     * @throws DataServiceNotFoundAPIException if no dataService is found with the provided ID
     */
    public DataService getDataServiceById(String id) {
        return repository.findById(id).orElseThrow(() -> new DataServiceNotFoundAPIException("Data Service with id: " + id + " not found"));
    }

    /**
     * Retrieves all data services in the catalog.
     *
     * @return a list of all data services
     */
    public List<DataService> getAllDataServices() {
        return repository.findAll();
    }

    /**
     * Saves a dataService to the repository and updates the catalog.
     *
     * @param dataService the dataService to be saved
     */
    public DataService saveDataService(DataService dataService) {
        DataService savedDataService = repository.save(dataService);
        catalogService.updateCatalogDataServiceAfterSave(savedDataService);
        return dataService;
    }

    /**
     * Deletes a dataService by its ID and updates the catalog.
     *
     * @param id the unique ID of the dataService to be deleted
     * @throws DataServiceNotFoundAPIException if no data service is found with the provided ID
     */
    public void deleteDataService(String id) {
        DataService dataService = repository.findById(id).orElseThrow(() -> new DataServiceNotFoundAPIException("Data Service with id: " + id + " not found"));
        repository.deleteById(id);
        catalogService.updateCatalogDataServiceAfterDelete(dataService);
    }

    /**
     * Updates a dataService in the repository and updates the catalog.
     *
     * @param id          the unique ID of the dataService to be updated
     * @param dataService the data service to be updated
     * @return the updated dataService
     */
    public DataService updateDataService(String id, DataService dataService) {
        DataService existingDataService = repository.findById(id).orElseThrow(() -> new DataServiceNotFoundAPIException("Data Service with id: " + id + " not found"));
        DataService updatedDataService = DataService.Builder.updateDataServiceInstance(existingDataService, dataService).build();
        DataService storedDataService = repository.save(updatedDataService);
        catalogService.updateCatalogDataServiceAfterUpdate(storedDataService);

        return updatedDataService;
    }
}
