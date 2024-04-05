package it.eng.catalog.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import it.eng.catalog.exceptions.CatalogErrorException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;

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

    //TODO implement this with aggregations and move to DataSet repository/service

    /**
     * Retrieves a dataset by its ID.
     *
     * @param id The ID of the dataset to retrieve.
     * @return An optional containing the retrieved dataset, or empty if not found.
     */
    public Optional<Dataset> getDataSetById(String id) {
        Optional<Catalog> catalog = repository.findCatalogByDatasetId(id);
        return catalog.flatMap(c -> c.getDataset().stream().filter(d -> d.getId().equals(id)).findFirst());
    }


    /**
     * Deletes the catalog with the specified ID.
     *
     * @param id The ID of the catalog to delete.
     */
    public void deleteCatalog(String id) {
        repository.deleteById(id);
    }

    //TODO implement proper filter logic
    /**
     * 
     * @param filter
     * @return
     */
	public Catalog findByFilter(List<String> filter) {
		//if not catalog found(no options from filter found) throw ResourceNotFoundException
		return getCatalog();
	}
}
