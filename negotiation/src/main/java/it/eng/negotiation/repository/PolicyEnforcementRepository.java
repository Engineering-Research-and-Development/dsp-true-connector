package it.eng.negotiation.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.negotiation.model.PolicyEnforcement;

@Repository
public interface PolicyEnforcementRepository extends MongoRepository<PolicyEnforcement, String> {

	Optional<PolicyEnforcement> findByAgreementId(String agreementId);
}
