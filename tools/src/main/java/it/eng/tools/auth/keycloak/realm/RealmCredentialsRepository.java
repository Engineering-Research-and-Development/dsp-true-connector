package it.eng.tools.auth.keycloak.realm;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB repository for {@link RealmCredentials}, keyed by tenant id.
 */
@Repository
public interface RealmCredentialsRepository extends MongoRepository<RealmCredentials, String> {
}
