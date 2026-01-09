package it.eng.dcp.issuer.repository;

import it.eng.dcp.issuer.model.StatusList;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing StatusList entities in MongoDB.
 */
@Repository
public interface StatusListRepository extends MongoRepository<StatusList, String> {
    Optional<StatusList> findByIssuerDidAndStatusPurpose(String issuerDid, String statusPurpose);
}

