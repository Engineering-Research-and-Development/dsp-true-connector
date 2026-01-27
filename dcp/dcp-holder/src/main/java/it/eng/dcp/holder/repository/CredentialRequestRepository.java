package it.eng.dcp.holder.repository;

import it.eng.dcp.common.model.CredentialRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CredentialRequestRepository extends MongoRepository<CredentialRequest, String> {
    Optional<CredentialRequest> findByIssuerPid(String issuerPid);
}

