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
     * Finds a Data Plane registration by its API key.
     *
     * @param apiKey the API key to search for
     * @return an Optional containing the registration if found, or empty if not found
     */
    Optional<DataPlaneRegistration> findByApiKey(String apiKey);

    /**
     * Finds a Data Plane registration by its endpoint URL.
     *
     * @param endpoint the endpoint URL to search for
     * @return an Optional containing the registration if found, or empty if not found
     */
    Optional<DataPlaneRegistration> findByEndpoint(String endpoint);
}
