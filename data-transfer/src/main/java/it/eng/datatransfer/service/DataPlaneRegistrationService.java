package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.DataPlaneNotFoundException;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.repository.DataPlaneRegistrationRepository;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing Data Plane registrations on the Control Plane side.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataPlaneRegistrationService {

    private final DataPlaneRegistrationRepository repository;
    private final AuditEventPublisher auditEventPublisher;

    /**
     * Registers a new Data Plane instance or updates an existing registration with the same endpoint.
     * This makes re-registration idempotent — a DP restart replaces its previous entry rather than
     * creating a duplicate.
     *
     * @param registration the Data Plane registration to persist
     * @return the saved registration
     */
    public DataPlaneRegistration register(DataPlaneRegistration registration) {
        log.info("Registering Data Plane at endpoint {}", registration.getEndpoint());
        return repository.findByEndpoint(registration.getEndpoint())
                .map(existing -> {
                    log.info("Updating existing registration {} for endpoint {}", existing.getId(), existing.getEndpoint());
                    DataPlaneRegistration updated = DataPlaneRegistration.Builder.newInstance()
                            .id(existing.getId())
                            .endpoint(registration.getEndpoint())
                            .supportedTransferTypes(registration.getSupportedTransferTypes())
                            .apiKey(registration.getApiKey())
                            .build();
                    DataPlaneRegistration saved = repository.save(updated);
                    auditEventPublisher.publishEvent(AuditEventType.DATAPLANE_REGISTRATION_UPDATED,
                            "Data Plane registration updated for endpoint " + registration.getEndpoint(),
                            Map.of("endpoint", registration.getEndpoint(),
                                    "transferTypes", registration.getSupportedTransferTypes().toString()));
                    return saved;
                })
                .orElseGet(() -> {
                    DataPlaneRegistration saved = repository.save(registration);
                    auditEventPublisher.publishEvent(AuditEventType.DATAPLANE_REGISTERED,
                            "Data Plane registered at endpoint " + registration.getEndpoint(),
                            Map.of("endpoint", registration.getEndpoint(),
                                    "transferTypes", registration.getSupportedTransferTypes().toString()));
                    return saved;
                });
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
        DataPlaneRegistration existing = repository.findById(id)
                .orElseThrow(() -> {
                    auditEventPublisher.publishEvent(AuditEventType.DATAPLANE_REGISTRATION_NOT_FOUND,
                            "Data Plane registration not found for id " + id,
                            Map.of("id", id));
                    return new DataPlaneNotFoundException("Data Plane registration not found: " + id);
                });
        repository.deleteById(id);
        auditEventPublisher.publishEvent(AuditEventType.DATAPLANE_DEREGISTERED,
                "Data Plane deregistered at endpoint " + existing.getEndpoint(),
                Map.of("id", id, "endpoint", existing.getEndpoint()));
    }

    /**
     * Returns all registered Data Plane instances.
     *
     * @return list of all registrations
     */
    public List<DataPlaneRegistration> findAll() {
        return repository.findAll();
    }

    /**
     * Finds a Data Plane registration by its API key.
     *
     * @param apiKey the API key to look up
     * @return an Optional containing the registration if found, or empty if no registration matches
     */
    public Optional<DataPlaneRegistration> findByApiKey(String apiKey) {
        log.debug("Looking up Data Plane by API key");
        return repository.findByApiKey(apiKey);
    }
}
