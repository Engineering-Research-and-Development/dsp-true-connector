
package it.eng.catalog.service;

import java.util.Collection;

import org.springframework.stereotype.Service;

import it.eng.catalog.exceptions.InternalServerErrorAPIException;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.DistributionRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The DistributionService class provides methods to interact with Distribution data, including saving, retrieving, and deleting distributions.
 */
@Service
@Slf4j
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
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundAPIException("Distribution with id: " + id + " not found"));
    }

    /**
     * Retrieves all distributions in the catalog.
     *
     * @return a list of all distributions
     */
    public Collection<Distribution> getAllDistributions() {
        return repository.findAll();
    }

    /**
     * Saves a distribution to the repository and updates the catalog.
     *
     * @param distribution the distribution to be saved
     * @return saved distribution
     * @throws InternalServerErrorAPIException if saving fails
     */
    public Distribution saveDistribution(Distribution distribution) {
        Distribution savedDistribution = null;
		try {
			savedDistribution = repository.save(distribution);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Distribution could not be saved");
		}
        catalogService.updateCatalogDistributionAfterSave(savedDistribution);
        return distribution;
    }

    /**
     * Deletes a distribution by its ID and updates the catalog.
     *
     * @param id the unique ID of the distribution to be deleted
     * @throws ResourceNotFoundAPIException if no distribution is found with the provided ID
     * @throws InternalServerErrorAPIException if deleting fails
     */
    public void deleteDistribution(String id) {
        Distribution distribution = getDistributionById(id);
        try {
			repository.deleteById(id);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Distribution could not be deleted");
		}
        catalogService.updateCatalogDistributionAfterDelete(distribution);
    }

    /**
     * Updates a distribution in the repository.
     *
     * @param id          the unique ID of the distribution to be updated
     * @param distribution the distribution to be updated
     * @return the updated distribution
     * @throws ResourceNotFoundAPIException if no distribution is found with the provided ID
     * @throws InternalServerErrorAPIException if updating fails
     */
    public Distribution updateDistribution(String id, Distribution distribution) {
        Distribution existingDistribution = getDistributionById(id);
        Distribution storedDistribution;
		try {
			Distribution updatedDistribution = existingDistribution.updateInstance(distribution);
			storedDistribution = repository.save(updatedDistribution);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new InternalServerErrorAPIException("Dataset could not be updated");
		}

        return storedDistribution;
    }
}
