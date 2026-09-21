package it.eng.tools.s3.repository;

import it.eng.tools.s3.model.TemporaryBucketUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemporaryBucketUserRepository extends MongoRepository<TemporaryBucketUser, String> {
}

