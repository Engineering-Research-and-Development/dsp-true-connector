package it.eng.catalog.repository;

import it.eng.catalog.model.Dataset;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DatasetRepository extends MongoRepository<Dataset, String> {

	Optional<Dataset> findByArtifact(String id);
}
