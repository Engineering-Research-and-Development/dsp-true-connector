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

}
