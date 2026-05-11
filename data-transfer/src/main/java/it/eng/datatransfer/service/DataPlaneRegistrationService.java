package it.eng.datatransfer.service;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.repository.DataPlaneRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing Data Plane registrations on the Control Plane side.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataPlaneRegistrationService {

    private final DataPlaneRegistrationRepository repository;

    /**
     * Registers a new Data Plane instance.
     *
     * @param registration the Data Plane registration to persist
     * @return the saved registration
     */
    public DataPlaneRegistration register(DataPlaneRegistration registration) {
        log.info("Registering Data Plane at endpoint {}", registration.getEndpoint());
        return repository.save(registration);
    }

    /**
     * Finds all Data Plane registrations that support the given transfer type.
     *
     * @param transferType the transfer type identifier to search for
     * @return list of matching registrations
     */
    public List<DataPlaneRegistration> findByTransferType(String transferType) {
        log.debug("Finding Data Planes supporting transfer type {}", transferType);
        return repository.findBySupportedTransferTypesContaining(transferType);
    }

    /**
     * Deregisters a Data Plane by its id.
     *
     * @param id the id of the registration to remove
     */
    public void deregister(String id) {
        log.info("Deregistering Data Plane with id {}", id);
        repository.deleteById(id);
    }

    /**
     * Returns all registered Data Plane instances.
     *
     * @return list of all registrations
     */
    public List<DataPlaneRegistration> findAll() {
        return repository.findAll();
    }
}
