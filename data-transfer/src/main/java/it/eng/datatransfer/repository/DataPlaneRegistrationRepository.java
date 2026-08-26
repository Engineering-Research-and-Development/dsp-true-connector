package it.eng.datatransfer.repository;

import it.eng.datatransfer.model.DataPlaneRegistration;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link DataPlaneRegistration} documents.
 */
public interface DataPlaneRegistrationRepository extends MongoRepository<DataPlaneRegistration, String> {

    /**
     * Finds all Data Plane registrations that support the given transfer type.
     *
     * @param transferType the transfer type identifier to search for (e.g. "HttpData-PULL")
     * @return list of matching registrations
     */
    List<DataPlaneRegistration> findBySupportedTransferTypesContaining(String transferType);

    /**
     * Finds a Data Plane registration by the HMAC-SHA256 hash of its API key.
     *
     * <p>The method name intentionally still reads {@code findByApiKey} because Spring Data's
     * query derivation resolves it against the {@code apiKey} property on
     * {@link DataPlaneRegistration} by name, not by the semantic meaning of the value passed in;
     * that field now stores a hash, not the raw key (see {@link DataPlaneRegistration#getApiKey()}).
     *
     * @param apiKeyHash the HMAC-SHA256 hash to search for (see {@code ApiKeyHasher#hash}) — the
     *                   caller must hash the raw key before calling this method; the raw key is
     *                   never passed to or stored by this repository
     * @return an Optional containing the registration if found, or empty if not found
     */
    Optional<DataPlaneRegistration> findByApiKey(String apiKeyHash);

    /**
     * Finds a Data Plane registration by its endpoint URL.
     *
     * @param endpoint the endpoint URL to search for
     * @return an Optional containing the registration if found, or empty if not found
     */
    Optional<DataPlaneRegistration> findByEndpoint(String endpoint);
}
