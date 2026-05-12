package it.eng.dataplane.s3.repository;

import it.eng.dataplane.s3.model.BucketCredentialsEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MongoDB repository for {@link BucketCredentialsEntity}.
 */
@Repository
public interface BucketCredentialsRepository extends MongoRepository<BucketCredentialsEntity, String> {

    /**
     * Finds bucket credentials by bucket name.
     *
     * @param bucketName the bucket name
     * @return the credentials if found
     */
    Optional<BucketCredentialsEntity> findByBucketName(String bucketName);
}
