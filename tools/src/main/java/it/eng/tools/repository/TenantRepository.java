package it.eng.tools.repository;

import it.eng.tools.model.Tenant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
