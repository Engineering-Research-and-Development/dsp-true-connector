package it.eng.dataplane.s3.repository;

import it.eng.dataplane.s3.model.TemporaryBucketUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB repository for {@link TemporaryBucketUser}.
 */
@Repository
public interface TemporaryBucketUserRepository extends MongoRepository<TemporaryBucketUser, String> {
}
