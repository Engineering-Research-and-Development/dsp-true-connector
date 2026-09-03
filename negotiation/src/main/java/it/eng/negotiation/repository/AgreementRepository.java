package it.eng.negotiation.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.negotiation.model.Agreement;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgreementRepository extends MongoRepository<Agreement, String> {

    List<Agreement> findAllByTenantId(String tenantId);

    Optional<Agreement> findByIdAndTenantId(String id, String tenantId);

    /**
     * Finds an agreement by its DSP protocol {@code id}, independent of the MongoDB technical
     * primary key. Intended for single-tenant/super-admin lookups where no tenant scoping applies;
     * tenant-scoped lookups should use {@link #findByIdAndTenantId(String, String)} instead.
     *
     * @param id the DSP protocol agreement identifier
     * @return the matching agreement, if any
     */
    Optional<Agreement> findAgreementById(String id);

}
