package it.eng.tools.s3.repository;

import it.eng.tools.s3.model.BucketCredentialsEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BucketCredentialsRepository extends MongoRepository<BucketCredentialsEntity, String> {

    Optional<BucketCredentialsEntity> findByBucketName(String bucketName);
}
