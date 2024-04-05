package it.eng.connector.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.eng.connector.model.User;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

	Optional<User> findByEmail(String email);
}
