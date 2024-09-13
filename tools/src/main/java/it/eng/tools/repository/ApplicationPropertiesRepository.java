package it.eng.tools.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.tools.model.ApplicationProperty;

/**
 * ApplicationPropertiesRepository
 */
@Repository
public interface ApplicationPropertiesRepository extends MongoRepository<ApplicationProperty, String> {
	
	/**
	 * Find application property by id
	 * @param id property identifier
	 * @return Optional of ApplicationProperty
	 */
    Optional<ApplicationProperty> findById(String id);
    
    /**
     * Find properties starting with key_prefix
     * @param key_prefix criteria to filter
     * @param sort Sort
     * @return List of application properties
     */
    List<ApplicationProperty> findByKeyStartingWith(String key_prefix, Sort sort);
    
}
