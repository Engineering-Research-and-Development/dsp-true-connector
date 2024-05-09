package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.exceptions.CatalogNotFoundAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * The CatalogService class provides methods to interact with catalog data, including saving, retrieving, and deleting catalogs.
 */
@Service
public class CatalogService {

    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    /**
     * Saves the given catalog.
     *
     * @param catalog The catalog to be saved.
     */
    public void saveCatalog(Catalog catalog) {
        // TODO handle the situation when we insert catalog for the first time, and the object like dataSets, distributions, etc. should be stored into separate documents
        repository.save(catalog);
    }

    /**
     * Retrieves the catalog.
     *
     * @return The retrieved catalog.
     * @throws CatalogErrorException Thrown if the catalog is not found.
     */
    public Catalog getCatalog() {
        List<Catalog> allCatalogs = repository.findAll();

        if (allCatalogs.isEmpty()) {
            throw new CatalogErrorException("Catalog not found");
        } else {
            return allCatalogs.get(0);
        }
    }

    /**
     * Retrieves the catalog by its ID.
     *
     * @param id The ID of the catalog to retrieve.
     * @return An optional containing the retrieved catalog, or empty if not found.
     */
    public Optional<Catalog> getCatalogById(String id) {
        return repository.findById(id);
    }

    //NOTE: Still present in service, because different types of errors are thrown for protocol and API
    // TODO: Find an elegant way to adapt same method in DataSetService, to throw different types of error based on Protocol/API flow

    /**
     * Retrieves a dataset by its ID.
     *
     * @param id The ID of the dataset to retrieve.
     * @return An optional containing the retrieved dataset, or empty if not found.
     */
    public Dataset getDataSetById(String id) {
        Optional<Catalog> catalog = repository.findCatalogByDatasetId(id);
        return catalog.flatMap(c -> c.getDataset()
                        .stream()
                        .filter(d -> d.getId().equals(id))
                        .findFirst())
                .orElseThrow(() -> new CatalogErrorException("Data Set with id: " + id + " not found"));
    }


    /**
     * Deletes the catalog with the specified ID.
     *
     * @param id The ID of the catalog to delete.
     */
    public void deleteCatalog(String id) {
        repository.deleteById(id);
    }


    /**
     * Updates the catalog with the specified ID using the updated data.
     *
     * @param id                 The ID of the catalog to update.
     * @param updatedCatalogData The updated catalog data.
     */
    public void updateCatalog(String id, Catalog updatedCatalogData) {

        Catalog.Builder builder = returnBaseCatalogForUpdate(id);
        builder
                .keyword(updatedCatalogData.getKeyword())
                .theme(updatedCatalogData.getTheme())
                .conformsTo(updatedCatalogData.getConformsTo())
                .description(updatedCatalogData.getDescription())
                .identifier(updatedCatalogData.getIdentifier())
                .title(updatedCatalogData.getTitle())
                .distribution(updatedCatalogData.getDistribution())
                .hasPolicy(updatedCatalogData.getHasPolicy())
                .dataset(updatedCatalogData.getDataset())
                .service(updatedCatalogData.getService())
                .participantId(updatedCatalogData.getParticipantId())
                .homepage(updatedCatalogData.getHomepage());

        Catalog updatedCatalog = builder.build();
        repository.save(updatedCatalog);
    }


    /**
     * Updates the catalog with a newly saved dataset reference.
     * This method adds the new dataset reference to the catalog's dataset list and saves the updated catalog.
     *
     * @param newDataset The new dataset reference to be added to the catalog.
     */
    public void updateCatalogDatasetAfterSave(Dataset newDataset) {
        // TODO handle the situation when new dataset have distribution which is not present in catalog
        Catalog c = getCatalog();
        c.getDataset().add(newDataset);
        repository.save(c);
    }

    /**
     * Updates the catalog with modified dataset information.
     * This method replaces an existing dataset in the catalog with its updated version and saves the updated catalog.
     *
     * @param updatedDataset The dataset with updated information to be integrated into the catalog.
     */
    public void updateCatalogDatasetAfterUpdate(Dataset updatedDataset) {
        // TODO handle the situation when updated dataset have distribution which is not present in catalog
        Catalog c = getCatalog();
        c.getDataset().add(updatedDataset);
        repository.save(c);
    }

    /**
     * Removes a dataset reference from the catalog and saves the updated catalog.
     * This method removes the specified dataset reference from the catalog's dataset collection and saves the updated catalog.
     *
     * @param dataset The dataset to be removed from the catalog.
     */
    public void updateCatalogDatasetAfterDelete(Dataset dataset) {
        Catalog c = getCatalog();
        c.getDataset().remove(dataset);
        repository.save(c);
    }

    /**
     * Private method for creating base builder for catalog update by its ID.
     *
     * @param id The ID of the catalog for update.
     * @return The builder for the catalog with basic mandatory unchanged fields.
     * @throws CatalogErrorException Thrown if the catalog with the specified ID is not found.
     */
    private Catalog.Builder returnBaseCatalogForUpdate(String id) {
        return repository.findById(id)
                .map(c -> Catalog.Builder.newInstance()
                        .id(c.getId())
                        .version(c.getVersion())
                        .issued(c.getIssued())
                        .createdBy(c.getCreatedBy()))
                .orElseThrow(() -> new CatalogNotFoundAPIException("Catalog with id: " + id + " not found"));
    }

}

