package it.eng.connector.repository;

import it.eng.connector.model.User;
import it.eng.tools.repository.GenericDynamicFilterRepository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface UserRepository extends MongoRepository<User, String>, GenericDynamicFilterRepository<User, String> {

	Optional<User> findByEmail(String email);
	
	Page<User> findByEnabledFalse(Pageable pageable);
}
