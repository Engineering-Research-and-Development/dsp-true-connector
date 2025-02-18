package it.eng.catalog.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.exceptions.InternalServerErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.tools.model.Artifact;
import lombok.extern.slf4j.Slf4j;

/**
 * The DataSetService class provides methods to interact with Dataset data, including saving, retrieving, and deleting datasets.
 */
@Service
@Slf4j
public class DatasetService {


    private final DatasetRepository repository;
    private final CatalogService catalogService;
    private final ArtifactService artifactService;


    public DatasetService(DatasetRepository repository, CatalogService catalogService, ArtifactService artifactService) {
        this.repository = repository;
        this.catalogService = catalogService;
		this.artifactService = artifactService;
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
     * @param externalURL URL of external data
     * @param file File that is going to be stored in database locally
     * @param authorization 
     * @return saved dataset
     * @throws InternalServerErrorAPIException if saving fails
     */
    public Dataset saveDataset(Dataset dataset, MultipartFile file, String externalURL, String authorization) {
        Dataset savedDataSet = null;
        try {
        	Artifact artifact = artifactService.uploadArtifact(dataset, file, externalURL, authorization);
        	Dataset datasetWithArtifact = addArtifactToDataset(dataset, artifact);
        	savedDataSet = repository.save(datasetWithArtifact);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Dataset could not be saved");
		}
        catalogService.updateCatalogDatasetAfterSave(savedDataSet);
        log.info("Inserted Dataset with id {}", savedDataSet.getId());
        return savedDataSet;
    }

	/**
     * Updates a dataset in the repository.
     *
     * @param id the unique ID of the dataset to be updated
     * @param newDataset the dataset with new data
     * @param externalURL URL of external data
     * @param file File that is going to be stored in database locally
	 * @param authorization 
     * @return the updated dataset
     * @throws ResourceNotFoundAPIException if no data service is found with the provided ID
     * @throws InternalServerErrorAPIException if updating fails
     */
    public Dataset updateDataset(String id, Dataset newDataset, MultipartFile file, String externalURL, String authorization) {
    	Dataset existingDataset = getDatasetByIdForApi(id);
    	Dataset updatedDataset = null;
    	Dataset storedDataset = null;
		try {
			// store old artifact; will be deleted if new artifact is successfully updated
			Artifact oldArtifact = existingDataset.getArtifact();
			if (newDataset != null) {
				// update old dataset if new one is present
				updatedDataset = existingDataset.updateInstance(newDataset);
			} else {
				// use the old dataset if we are updating only the artifact
				updatedDataset = existingDataset;
			}
			if (file != null || StringUtils.isNotBlank(externalURL)) {
				Artifact newArtifact = artifactService.uploadArtifact(updatedDataset, file, externalURL, authorization);
				updatedDataset = addArtifactToDataset(updatedDataset, newArtifact);
				// remove old artifact
				if (oldArtifact != null) {
					artifactService.deleteOldArtifact(oldArtifact);
				}
			}
			storedDataset = repository.save(updatedDataset);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Dataset could not be updated");
		}

        return storedDataset;
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
	    	artifactService.deleteOldArtifact(ds.getArtifact());
			repository.deleteById(id);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Dataset could not be deleted");
		}
	    catalogService.updateCatalogDatasetAfterDelete(ds);
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

	public Artifact getArtifactFromDataset(String id) {
		Artifact artifact = getDatasetByIdForApi(id).getArtifact();
		if (artifact == null) {
			throw new ResourceNotFoundAPIException("Dataset with id: " + id + " has no artifact");
		}
		return artifact;
	}
	
	private Dataset addArtifactToDataset(Dataset dataset, Artifact artifact) {
		Dataset datasetWithArtifact = Dataset.Builder.newInstance()
				.id(dataset.getId())
				.artifact(artifact)
				.conformsTo(dataset.getConformsTo())
				.createdBy(dataset.getCreatedBy())
				.creator(dataset.getCreator())
				.description(dataset.getDescription())
				.distribution(dataset.getDistribution())
				.hasPolicy(dataset.getHasPolicy())
				.issued(dataset.getIssued())
				.keyword(dataset.getKeyword())
				.lastModifiedBy(dataset.getLastModifiedBy())
				.modified(dataset.getModified())
				.theme(dataset.getTheme())
				.title(dataset.getTitle())
				.version(dataset.getVersion())
				.build();
		return datasetWithArtifact;
	}
}
