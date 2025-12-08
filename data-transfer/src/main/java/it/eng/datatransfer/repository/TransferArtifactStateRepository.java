package it.eng.datatransfer.repository;

import it.eng.datatransfer.model.TransferArtifactState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferArtifactStateRepository extends MongoRepository<TransferArtifactState, String> {
}
