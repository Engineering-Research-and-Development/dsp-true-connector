package it.eng.catalog.repository;

import it.eng.catalog.model.Dataset;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DatasetRepository extends MongoRepository<Dataset, String> {

	Optional<Dataset> findByArtifact(String id);

	/**
	 * Returns all datasets belonging to the given tenant.
	 *
	 * @param tenantId the tenant identifier
	 * @return list of datasets for the tenant
	 */
	List<Dataset> findAllByTenantId(String tenantId);

	/**
	 * Returns the dataset with the given ID that belongs to the given tenant.
	 *
	 * @param id       the dataset identifier
	 * @param tenantId the tenant identifier
	 * @return the matching dataset, or empty if not found or not owned by tenant
	 */
	Optional<Dataset> findByIdAndTenantId(String id, String tenantId);
}
