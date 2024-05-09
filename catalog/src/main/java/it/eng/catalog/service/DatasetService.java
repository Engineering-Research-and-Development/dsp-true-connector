package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.exceptions.DatasetNotFoundAPIException;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DatasetRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The DataSetService class provides methods to interact with Dataset data, including saving, retrieving, and deleting datasets.
 */
@Service
public class DatasetService {


    private final DatasetRepository repository;
    private final CatalogService catalogService;


    public DatasetService(DatasetRepository repository, CatalogService catalogService) {
        this.repository = repository;
        this.catalogService = catalogService;
    }

    /**
     * Retrieves a dataset by its unique ID.
     *
     * @param id the unique ID of the dataset
     * @return the dataset corresponding to the provided ID
     * @throws CatalogErrorException if no dataset is found with the provided ID
     */
    public Dataset getDatasetById(String id) {
        return repository.findById(id).orElseThrow(() -> new DatasetNotFoundAPIException("Data Set with id: " + id + " not found"));

    }

    /**
     * Retrieves all datasets in the catalog.
     *
     * @return a list of all datasets
     */
    public List<Dataset> getAllDatasets() {
        return repository.findAll();
    }

    /**
     * Saves a dataset to the repository and updates the catalog.
     *
     * @param dataset the dataset to be saved
     */
    public Dataset saveDataset(Dataset dataset) {
        Dataset savedDataSet = repository.save(dataset);
        catalogService.updateCatalogDatasetAfterSave(savedDataSet);
        return dataset;
    }

    /**
     * Deletes a dataset by its ID and updates the catalog.
     *
     * @param id the unique ID of the dataset to delete
     */
    public void deleteDataset(String id) {
        Dataset ds = repository.findById(id).orElseThrow(() -> new DatasetNotFoundAPIException("Data Set with id: " + id + " not found"));
        repository.deleteById(id);
        catalogService.updateCatalogDatasetAfterDelete(ds);
    }

    /**
     * Updates a dataset in the repository and the catalog.
     *
     * @param updatedDatasetData the dataset to update
     */
    public Dataset updateDataset(String id, Dataset updatedDatasetData) {
        Dataset.Builder builder = returnBaseDatasetForUpdate(id);

        builder.keyword(updatedDatasetData.getKeyword())
                .creator(updatedDatasetData.getCreator())
                .theme(updatedDatasetData.getTheme())
                .conformsTo(updatedDatasetData.getConformsTo())
                .description(updatedDatasetData.getDescription())
                .identifier(updatedDatasetData.getIdentifier())
                .title(updatedDatasetData.getTitle())
                .distribution(updatedDatasetData.getDistribution())
                .hasPolicy(updatedDatasetData.getHasPolicy());

        Dataset updatedDataset = builder.build();
        Dataset storedDataset = repository.save(updatedDataset);
        catalogService.updateCatalogDatasetAfterUpdate(updatedDataset);
        return storedDataset;
    }


    /**
     * Private method for creating base builder for dataset update by its ID.
     *
     * @param id The ID of the catalog for update.
     * @return The builder for the dataset with basic mandatory unchanged fields.
     * @throws CatalogErrorException Thrown if the catalog with the specified ID is not found.
     */
    private Dataset.Builder returnBaseDatasetForUpdate(String id) {
        return repository.findById(id)
                .map(c -> Dataset.Builder.newInstance()
                        .id(c.getId())
                        .version(c.getVersion())
                        .issued(c.getIssued())
                        .createdBy(c.getCreatedBy()))
                .orElseThrow(() -> new CatalogErrorException("Catalog with id: " + id + " not found"));
    }
}
