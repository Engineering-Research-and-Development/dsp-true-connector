package it.eng.dcp.holder.repository;

import it.eng.dcp.holder.model.VerifiableCredential;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface VerifiableCredentialRepository extends MongoRepository<VerifiableCredential, String> {

    List<VerifiableCredential> findByHolderDid(String holderDid);

    /**
     * Finds the credentials of one holder with any of the given types.
     *
     * @param holderDid the DID of the holder that owns the credentials
     * @param types the credential types
     * @return the matching credentials of the holder
     */
    List<VerifiableCredential> findByHolderDidAndCredentialTypeIn(String holderDid, List<String> types);

    /**
     * Finds the credentials of one holder with any of the given IDs.
     *
     * @param holderDid the DID of the holder that owns the credentials
     * @param ids the credential IDs
     * @return the matching credentials of the holder
     */
    List<VerifiableCredential> findByHolderDidAndIdIn(String holderDid, List<String> ids);

}
