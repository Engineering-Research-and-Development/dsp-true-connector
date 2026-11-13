package it.eng.tools.repository;

import it.eng.tools.model.Tenant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link Tenant} documents.
 */
@Repository
public interface TenantRepository extends MongoRepository<Tenant, String>,
        GenericDynamicFilterRepository<Tenant, String>{

    /**
     * Finds all tenants matching the given enabled state.
     *
     * @param enabled {@code true} to find enabled tenants, {@code false} for disabled
     * @return list of tenants with the specified enabled state
     */
    List<Tenant> findByEnabled(boolean enabled);

    /**
     * Finds a tenant by its S3 bucket name.
     * Used to enforce uniqueness of bucket names across tenants.
     *
     * @param bucketName the S3 bucket name to search for
     * @return an optional containing the tenant that owns this bucket, if any
     */
    Optional<Tenant> findByBucketName(String bucketName);

    /**
     * Finds a tenant by bucket name excluding the provided tenant id.
     * Used during tenant updates to detect ownership conflicts while allowing
     * a tenant to keep or reconfirm its own bucket.
     *
     * @param bucketName the S3 bucket name to search for
     * @param id the tenant id to exclude from the lookup
     * @return an optional containing the conflicting tenant, if any
     */
    Optional<Tenant> findByBucketNameAndIdNot(String bucketName, String id);

    /**
     * Finds a tenant by its DSP participant identity.
     * Used to enforce uniqueness of participant IDs across tenants.
     *
     * @param participantId the participant ID to search for
     * @return an optional containing the tenant with this participant ID, if any
     */
    Optional<Tenant> findByParticipantId(String participantId);

    /**
     * Checks whether a tenant other than the given one already holds the realm.
     * Matches case-sensitively and includes disabled tenants. On create, pass an
     * id that no tenant holds (such as the new tenant's own id) to check all tenants.
     *
     * @param realm the realm name to look for
     * @param id the tenant id to exclude from the lookup
     * @return {@code true} if another tenant is bound to the realm
     */
    boolean existsByRealmAndIdNot(String realm, String id);

    /**
     * Finds an enabled tenant bound to the given Keycloak realm.
     * Used by the trusted-issuer registry to resolve tenant token issuers on every request.
     *
     * @param realm the Keycloak realm name, compared exactly
     * @return an optional containing an enabled tenant with this realm, if any
     */
    Optional<Tenant> findFirstByRealmAndEnabledTrue(String realm);
}
