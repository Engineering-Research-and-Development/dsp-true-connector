package it.eng.dcp.repository;

import it.eng.dcp.model.VerifiableCredential;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface VerifiableCredentialRepository extends MongoRepository<VerifiableCredential, String> {

    List<VerifiableCredential> findByCredentialTypeIn(List<String> types);

    List<VerifiableCredential> findByHolderDid(String holderDid);

}
