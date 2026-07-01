package it.eng.catalog.repository;

import it.eng.catalog.model.Catalog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogRepository extends MongoRepository<Catalog, String> {
    @Query(value = "{'service.id': ?0}", fields = "{'service.$': 1}")
    Optional<Catalog> findCatalogByDataServiceId(String dataServiceId);

    @Query(value = "{'dataset.id': ?0}", fields = "{'dataset.$': 1}")
    Optional<Catalog> findCatalogByDatasetId(String datasetId);

    /**
     * Returns the catalog containing the given data service, scoped to the given tenant.
     *
     * @param dataServiceId the data service identifier
     * @param tenantId      the tenant identifier
     * @return the matching catalog, or empty if not found or not owned by tenant
     */
    @Query(value = "{'service.id': ?0, 'tenantId': ?1}", fields = "{'service.$': 1}")
    Optional<Catalog> findCatalogByDataServiceIdAndTenantId(String dataServiceId, String tenantId);

    /**
     * Returns the catalog containing the given dataset, scoped to the given tenant.
     *
     * @param datasetId the dataset identifier
     * @param tenantId  the tenant identifier
     * @return the matching catalog, or empty if not found or not owned by tenant
     */
    @Query(value = "{'dataset.id': ?0, 'tenantId': ?1}", fields = "{'dataset.$': 1}")
    Optional<Catalog> findCatalogByDatasetIdAndTenantId(String datasetId, String tenantId);

    /**
     * Returns all catalogs belonging to the given tenant.
     *
     * @param tenantId the tenant identifier
     * @return list of catalogs for the tenant
     */
    List<Catalog> findAllByTenantId(String tenantId);

    /**
     * Returns the first catalog for the given tenant.
     *
     * @param tenantId the tenant identifier
     * @return the tenant's catalog, or empty if none exists
     */
    Optional<Catalog> findByTenantId(String tenantId);

    /**
     * Returns the catalog with the given ID that belongs to the given tenant.
     *
     * @param id       the catalog identifier
     * @param tenantId the tenant identifier
     * @return the matching catalog, or empty if not found or not owned by tenant
     */
    Optional<Catalog> findByIdAndTenantId(String id, String tenantId);
}
