package it.eng.tools.repository;

import it.eng.tools.model.Tenant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link Tenant} documents.
 */
@Repository
public interface TenantRepository extends MongoRepository<Tenant, String> {

    /**
     * Finds all tenants matching the given enabled state.
     *
     * @param enabled {@code true} to find enabled tenants, {@code false} for disabled
     * @return list of tenants with the specified enabled state
     */
    List<Tenant> findByEnabled(boolean enabled);

    /**
     * Finds a tenant by its S3 bucket name.
     * Used to enforce uniqueness of bucket names across tenants.
     *
     * @param bucketName the S3 bucket name to search for
     * @return an optional containing the tenant that owns this bucket, if any
     */
    Optional<Tenant> findByBucketName(String bucketName);

    /**
     * Finds a tenant by its DSP connector identity.
     * Used to enforce uniqueness of connector IDs across tenants.
     *
     * @param connectorId the connector ID to search for
     * @return an optional containing the tenant with this connector ID, if any
     */
    Optional<Tenant> findByConnectorId(String connectorId);
}
