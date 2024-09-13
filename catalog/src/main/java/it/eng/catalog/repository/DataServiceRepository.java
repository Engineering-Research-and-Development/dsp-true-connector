package it.eng.catalog.repository;

import it.eng.catalog.model.DataService;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataServiceRepository extends MongoRepository<DataService, String> {
}
