package it.eng.connector.repository;

import it.eng.connector.model.RefreshToken;
import it.eng.connector.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {
    
    Optional<RefreshToken> findByToken(String token);
    
    List<RefreshToken> findByUser(User user);
    
    void deleteByUser(User user);
    
    void deleteByToken(String token);
    
    void deleteByExpiryDateBefore(LocalDateTime dateTime);
    
    void deleteByRevoked(boolean revoked);
}
