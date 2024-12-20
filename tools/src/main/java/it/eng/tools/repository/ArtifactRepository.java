package it.eng.tools.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.tools.model.Artifact;

@Repository
public interface ArtifactRepository extends MongoRepository<Artifact, String> {

}
