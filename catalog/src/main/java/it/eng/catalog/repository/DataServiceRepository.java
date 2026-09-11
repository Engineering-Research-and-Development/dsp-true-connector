package it.eng.catalog.repository;

import it.eng.catalog.model.DataService;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataServiceRepository extends MongoRepository<DataService, String> {

    /**
     * Returns all data services belonging to the given tenant.
     *
     * @param tenantId the tenant identifier
     * @return list of data services for the tenant
     */
    List<DataService> findAllByTenantId(String tenantId);

    /**
     * Returns the data service with the given ID that belongs to the given tenant.
     *
     * @param id       the data service identifier
     * @param tenantId the tenant identifier
     * @return the matching data service, or empty if not found or not owned by tenant
     */
    Optional<DataService> findByIdAndTenantId(String id, String tenantId);
}
