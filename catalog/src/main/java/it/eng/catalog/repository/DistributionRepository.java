
package it.eng.catalog.repository;

import it.eng.catalog.model.Distribution;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DistributionRepository extends MongoRepository<Distribution, String> {
}
