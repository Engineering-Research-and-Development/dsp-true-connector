package it.eng.connector.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.connector.model.Property;


@Repository
public interface PropertiesRepository extends MongoRepository<Property, String> {
	
    Optional<Property> findById(String id);
	
	

}
