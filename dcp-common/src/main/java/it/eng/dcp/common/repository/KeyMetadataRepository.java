package it.eng.dcp.common.repository;

import it.eng.dcp.common.model.KeyMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface KeyMetadataRepository extends MongoRepository<KeyMetadata, String> {

    Optional<KeyMetadata> findByActiveTrue();

    Optional<KeyMetadata> findByAlias(String alias);
}

