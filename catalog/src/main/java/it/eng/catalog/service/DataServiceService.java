package it.eng.catalog.service;

import it.eng.catalog.exceptions.InternalServerErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.DataService;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * The DataServiceService class provides methods to interact with DataService data, including saving, retrieving, and deleting dataServices.
 */
@Service
@Slf4j
public class DataServiceService {

    private final DataServiceRepository repository;
    private final CatalogService catalogService;
    private final AuditEventPublisher auditEventPublisher;
    private final String baseURL;

    public DataServiceService(DataServiceRepository repository, CatalogService catalogService,
                              AuditEventPublisher auditEventPublisher, @Value("${application.baseURL}") String baseURL) {
        this.repository = repository;
        this.catalogService = catalogService;
        this.auditEventPublisher = auditEventPublisher;
        this.baseURL = baseURL;
    }

    /**
     * Retrieves a data service by its unique ID, scoped to the current tenant context.
     *
     * @param id the unique ID of the data service
     * @return the dataService corresponding to the provided ID
     * @throws ResourceNotFoundAPIException if no dataService is found with the provided ID
     */
    public DataService getDataServiceById(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return repository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundAPIException("Data Service with id: " + id + " not found"));
        }
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundAPIException("Data Service with id: " + id + " not found"));
    }

    /**
     * Retrieves all data services, scoped to the current tenant context.
     *
     * @return a collection of all data services visible to the current principal
     */
    public Collection<DataService> getAllDataServices() {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return repository.findAllByTenantId(tenantId);
        }
        return repository.findAll();
    }

    /**
     * Saves a dataService to the repository, stamping it with the current tenant ID.
     * Also updates the catalog.
     *
     * @param dataService the dataService to be saved
     * @return saved dataService
     * @throws InternalServerErrorAPIException if saving fails
     */
    public DataService saveDataService(DataService dataService) {
    	DataService savedDataService = null;
        try {
        	String tenantId = TenantContextHolder.getTenantId();
        	if (tenantId != null) {
        	    dataService.injectTenantId(tenantId);
        	}
            dataService.injectEndpointURL(checkForTrailingSlash(baseURL) + dataService.getTenantId());
        	savedDataService = repository.save(dataService);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Data service could not be saved");
		}
        catalogService.updateCatalogDataServiceAfterSave(savedDataService);
        auditEventPublisher.publishEvent(
                AuditEventType.DATA_SERVICE_CREATED, "Data service created",
                Map.of("dataServiceId", savedDataService.getId()));
        return savedDataService;
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
        auditEventPublisher.publishEvent(
                AuditEventType.DATA_SERVICE_DELETED, "Data service deleted", Map.of("dataServiceId", id));
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
        DataService storedDataService;
		try {
			DataService updatedDataService = existingDataService.updateInstance(dataService);
			storedDataService = repository.save(updatedDataService);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Data service could not be updated");
		}

        auditEventPublisher.publishEvent(
                AuditEventType.DATA_SERVICE_UPDATED, "Data service updated", Map.of("dataServiceId", id));
        return storedDataService;
    }

    /**
     * Checks if the provided URL ends with a trailing slash. If it does not, appends a trailing slash to the URL.
     *
     * @param url the URL to check
     * @return the URL with a trailing slash
     */
    private String checkForTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url;
        }
        return url + "/";
    }
}
