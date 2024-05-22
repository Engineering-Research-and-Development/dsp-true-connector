
package it.eng.catalog.service;

import it.eng.catalog.exceptions.DistributionNotFoundAPIException;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.DistributionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The DistributionService class provides methods to interact with Distribution data, including saving, retrieving, and deleting distributions.
 */
@Service
public class DistributionService {

    private final DistributionRepository repository;
    private final CatalogService catalogService;

    public DistributionService(DistributionRepository repository, CatalogService catalogService) {
        this.repository = repository;
        this.catalogService = catalogService;
    }

    /**
     * Retrieves a distribution by its unique ID.
     *
     * @param id the unique ID of the distribution
     * @return the distribution corresponding to the provided ID
     * @throws DistributionNotFoundAPIException if no distribution is found with the provided ID
     */
    public Distribution getDistributionById(String id) {
        return repository.findById(id).orElseThrow(() -> new DistributionNotFoundAPIException("Distribution with id: " + id + " not found"));
    }

    /**
     * Retrieves all distributions in the catalog.
     *
     * @return a list of all distributions
     */
    public List<Distribution> getAllDistributions() {
        return repository.findAll();
    }

    /**
     * Saves a distribution to the repository and updates the catalog.
     *
     * @param distribution the distribution to be saved
     */
    public Distribution saveDistribution(Distribution distribution) {
        Distribution savedDistribution = repository.save(distribution);
        catalogService.updateCatalogDistributionAfterSave(savedDistribution);
        return distribution;
    }

    /**
     * Deletes a distribution by its ID and updates the catalog.
     *
     * @param id the unique ID of the distribution to be deleted
     * @throws DistributionNotFoundAPIException if no distribution is found with the provided ID
     */
    public void deleteDistribution(String id) {
        Distribution distribution = getDistributionById(id);
        repository.delete(distribution);
        catalogService.updateCatalogDistributionAfterDelete(distribution);
    }

    /**
     * Updates a distribution in the repository and updates the catalog.
     *
     * @param distribution the distribution to be updated
     */
    public Distribution updateDistribution(String id, Distribution distribution) {

        Distribution existingDistribution = repository.findById(id).orElseThrow(() -> new DistributionNotFoundAPIException("Distribution with id: " + id + " not found"));
        Distribution updatedDistribution = Distribution.Builder.updateInstance(existingDistribution, distribution).build();
        Distribution storedDistribution = repository.save(updatedDistribution);
        catalogService.updateCatalogDistributionAfterUpdate(storedDistribution);

        return updatedDistribution;
    }
}
