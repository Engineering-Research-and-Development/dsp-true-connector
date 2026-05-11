package it.eng.datatransfer.repository;

import it.eng.datatransfer.model.DataPlaneRegistration;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

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
}
