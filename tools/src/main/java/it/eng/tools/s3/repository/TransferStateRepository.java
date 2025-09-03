package it.eng.tools.s3.repository;

import it.eng.tools.s3.model.TransferArtifactState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferStateRepository extends MongoRepository<TransferArtifactState, String> {
}
