
package it.eng.catalog.repository;

import it.eng.catalog.model.Distribution;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DistributionRepository extends MongoRepository<Distribution, String> {

    /**
     * Returns all distributions belonging to the given tenant.
     *
     * @param tenantId the tenant identifier
     * @return list of distributions for the tenant
     */
    List<Distribution> findAllByTenantId(String tenantId);

    /**
     * Returns the distribution with the given ID that belongs to the given tenant.
     *
     * @param id       the distribution identifier
     * @param tenantId the tenant identifier
     * @return the matching distribution, or empty if not found or not owned by tenant
     */
    Optional<Distribution> findByIdAndTenantId(String id, String tenantId);
}
