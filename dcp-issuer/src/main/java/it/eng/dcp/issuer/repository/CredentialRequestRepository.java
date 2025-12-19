package it.eng.dcp.issuer.repository;

import it.eng.dcp.model.CredentialRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing CredentialRequest entities in MongoDB.
 */
@Repository
public interface CredentialRequestRepository extends MongoRepository<CredentialRequest, String> {

    /**
     * Find a credential request by issuer PID.
     *
     * @param issuerPid The issuer PID to search for
     * @return Optional containing the credential request if found
     */
    Optional<CredentialRequest> findByIssuerPid(String issuerPid);

    /**
     * Find a credential request by holder PID.
     *
     * @param holderPid The holder PID to search for
     * @return Optional containing the credential request if found
     */
    Optional<CredentialRequest> findByHolderPid(String holderPid);
}

