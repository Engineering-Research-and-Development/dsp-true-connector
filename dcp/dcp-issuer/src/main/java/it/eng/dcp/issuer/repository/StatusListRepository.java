package it.eng.dcp.issuer.repository;

import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.model.StatusList;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing StatusList entities in MongoDB.
 */
@Repository
public interface StatusListRepository extends MongoRepository<StatusList, String> {

    /**
     * Finds the most recently created status list of an issuer for a purpose and profile; new entries are
     * allocated from it until it is full.
     *
     * @param issuerDid the issuer DID
     * @param statusPurpose the status purpose, for example {@code revocation}
     * @param profile the credential profile that selects the status list format
     * @return the newest matching status list, if any
     */
    Optional<StatusList> findFirstByIssuerDidAndStatusPurposeAndProfileOrderByCreatedAtDesc(
            String issuerDid, String statusPurpose, ProfileId profile);
}
