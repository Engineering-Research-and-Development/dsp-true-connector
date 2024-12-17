package it.eng.catalog.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.exceptions.InternalServerErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.DatasetRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The DataSetService class provides methods to interact with Dataset data, including saving, retrieving, and deleting datasets.
 */
@Service
@Slf4j
public class DatasetService {


    private final DatasetRepository repository;
    private final CatalogService catalogService;


    public DatasetService(DatasetRepository repository, CatalogService catalogService) {
        this.repository = repository;
        this.catalogService = catalogService;
    }

    /********* PROTOCOL ***********/
    /**
     * Retrieves a dataset by its unique ID, intended for protocol use.
     *
     * @param id the unique ID of the dataset
     * @return the dataset corresponding to the provided ID
     * @throws CatalogErrorException if no dataset is found with the provided ID
     */
    public Dataset getDatasetById(String id) {
        return repository.findById(id).orElseThrow(() -> new CatalogErrorException("Dataset with id: " + id + " not found"));

    }
    
    /********* API ***********/
    /**
     * Retrieves a dataset by its unique ID, intended for API use.
     *
     * @param id the unique ID of the dataset
     * @return the dataset corresponding to the provided ID
     * @throws ResourceNotFoundAPIException if no dataset is found with the provided ID
     */
    public Dataset getDatasetByIdForApi(String id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundAPIException("Dataset with id: " + id + " not found"));
    }

    /**
     * Retrieves all datasets in the catalog.
     *
     * @return a list of all datasets
     */
    public Collection<Dataset> getAllDatasets() {
        return repository.findAll();
    }

    /**
     * Saves a dataset to the repository and updates the catalog.
     *
     * @param dataset the dataset to be saved
     * @return saved dataset
     * @throws InternalServerErrorAPIException if saving fails
     */
    public Dataset saveDataset(Dataset dataset) {
        Dataset savedDataSet = null;
        try {
        	savedDataSet = repository.save(dataset);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Dataset could not be saved");
		}
        catalogService.updateCatalogDatasetAfterSave(savedDataSet);
        return dataset;
    }

    /**
     * Deletes a dataset by its ID and updates the catalog.
     *
     * @param id the unique ID of the dataset to delete
     * @throws ResourceNotFoundAPIException if no dataset is found with the provided ID
     * @throws InternalServerErrorAPIException if deleting fails
     */
    public void deleteDataset(String id) {
        Dataset ds = getDatasetByIdForApi(id);
        try {
			repository.deleteById(id);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Dataset could not be deleted");
		}
        catalogService.updateCatalogDatasetAfterDelete(ds);
    }

    /**
     * Updates a dataset in the repository.
     *
     * @param id the unique ID of the dataset to be updated
     * @param dataset the dataset to update
     * @return the updated dataset
     * @throws ResourceNotFoundAPIException if no data service is found with the provided ID
     * @throws InternalServerErrorAPIException if updating fails
     */
    public Dataset updateDataset(String id, Dataset dataset) {
    	Dataset existingDataset = getDatasetByIdForApi(id);
    	Dataset storedDataset = null;
		try {
			Dataset updatedDataset= existingDataset.updateInstance(dataset);
			storedDataset = repository.save(updatedDataset);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Dataset could not be updated");
		}

        return storedDataset;
    }

	public List<String> getFormatsFromDataset(String id) {
		Set<Distribution> distributions = getDatasetByIdForApi(id).getDistribution();
		
		if (distributions == null || distributions.isEmpty()) {
			throw new ResourceNotFoundAPIException("Dataset with id: " + id + " has no distributions");
		}
		
		List<String> formats = distributions.stream()
				.filter(dist -> dist.getFormat() != null)
				.map(dist -> dist.getFormat().getId())
				.collect(Collectors.toList());
		
		if (formats == null || formats.isEmpty()) {
			throw new ResourceNotFoundAPIException("Dataset with id: " + id + " has no distributions with format");
		}
		
		return formats;
	}

	public String getArtifactIdFromDataset(String id) {
		String artifactId = getDatasetByIdForApi(id).getSomeField();
		if (StringUtils.isBlank(artifactId)) {
			throw new ResourceNotFoundAPIException("Dataset with id: " + id + " has no artifact id");
		}
		return artifactId;
	}
}
