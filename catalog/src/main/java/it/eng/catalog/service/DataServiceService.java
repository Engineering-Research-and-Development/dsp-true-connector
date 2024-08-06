package it.eng.catalog.service;

import java.util.Collection;

import org.springframework.stereotype.Service;

import it.eng.catalog.exceptions.InternalServerErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.DataService;
import it.eng.catalog.repository.DataServiceRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The DataServiceService class provides methods to interact with DataService data, including saving, retrieving, and deleting dataServices.
 */
@Service
@Slf4j
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
     * @throws ResourceNotFoundAPIException if no dataService is found with the provided ID
     */
    public DataService getDataServiceById(String id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundAPIException("Data Service with id: " + id + " not found"));
    }

    /**
     * Retrieves all data services in the catalog.
     *
     * @return a list of all data services
     */
    public Collection<DataService> getAllDataServices() {
        return repository.findAll();
    }

    /**
     * Saves a dataService to the repository and updates the catalog.
     *
     * @param dataService the dataService to be saved
     * @return saved dataService
     * @throws InternalServerErrorAPIException if saving fails
     */
    public DataService saveDataService(DataService dataService) {
    	DataService savedDataService = null;
        try {
        	savedDataService = repository.save(dataService);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Data service could not be saved");
		}
        catalogService.updateCatalogDataServiceAfterSave(savedDataService);
        return dataService;
    }

    /**
     * Deletes a dataService by its ID and updates the catalog.
     *
     * @param id the unique ID of the dataService to be deleted
     * @throws ResourceNotFoundAPIException if no data service is found with the provided ID
     * @throws InternalServerErrorAPIException if deleting fails
     */
    public void deleteDataService(String id) {
        DataService existingDataService = getDataServiceById(id);
        try {
			repository.deleteById(id);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Data service could not be deleted");
		}
        catalogService.updateCatalogDataServiceAfterDelete(existingDataService);
    }

    /**
     * Updates a dataService in the repository.
     *
     * @param id the unique ID of the dataService to be updated
     * @param dataService the data service to be updated
     * @return the updated dataService
     * @throws ResourceNotFoundAPIException if no data service is found with the provided ID
     * @throws InternalServerErrorAPIException if updating fails
     */
    public DataService updateDataService(String id, DataService dataService) {
        DataService existingDataService = getDataServiceById(id);
        DataService storedDataService = null;;
		try {
			DataService updatedDataService = existingDataService.updateInstance(dataService);
			storedDataService = repository.save(updatedDataService);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Data service could not be updated");
		}

        return storedDataService;
    }
}
