package it.eng.tools.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.tools.model.ApplicationProperty;

@Repository
public interface ApplicationPropertiesRepository extends MongoRepository<ApplicationProperty, String> {
	
    Optional<ApplicationProperty> findById(String id);
    
    List<ApplicationProperty> findByKeyStartingWith(String key_prefix, Sort sort);
    
}
