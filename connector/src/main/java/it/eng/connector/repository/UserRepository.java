package it.eng.connector.repository;

import it.eng.connector.model.User;
import it.eng.tools.repository.GenericDynamicFilterRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface UserRepository extends MongoRepository<User, String>,
        GenericDynamicFilterRepository<User, String> {

	Optional<User> findByEmail(String email);

}
