package it.eng.dcp.repository;

import it.eng.dcp.model.CredentialStatusRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CredentialStatusRepository extends MongoRepository<CredentialStatusRecord, String> {

    Optional<CredentialStatusRecord> findByRequestId(String requestId);

}

