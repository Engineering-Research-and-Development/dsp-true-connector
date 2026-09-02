package it.eng.datatransfer.service;

import it.eng.datatransfer.event.DataPlaneRegistrationChangedEvent;
import it.eng.datatransfer.exceptions.DataPlaneNotFoundException;
import it.eng.datatransfer.exceptions.DataPlaneUnauthorizedException;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.repository.DataPlaneRegistrationRepository;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.security.ApiKeyHasher;
import it.eng.tools.service.AuditEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ApiKeyHasher apiKeyHasher;

    /**
     * Registers a new Data Plane instance or updates an existing registration with the same endpoint.
     * This makes re-registration idempotent — a DP restart replaces its previous entry rather than
     * creating a duplicate.
     * Evicts the Data Plane routing cache so the next dispatch picks up the new registration.
     *
     * @param registration the Data Plane registration to persist
     * @return the saved registration
     */
    @CacheEvict(cacheNames = "dataPlanesByType", allEntries = true)
    public DataPlaneRegistration register(DataPlaneRegistration registration) {
        log.info("Registering Data Plane at endpoint {}", registration.getEndpoint());
        String hashedApiKey = apiKeyHasher.hash(registration.getApiKey());
        String apiKeyHint = registration.getApiKey().substring(0, Math.min(8, registration.getApiKey().length()));
        return repository.findByEndpoint(registration.getEndpoint())
                .map(existing -> {
                    String idToUse = (registration.getId() != null && !registration.getId().isBlank())
                            ? registration.getId()
                            : existing.getId();
                    log.info("Updating existing registration {} → {} for endpoint {}", existing.getId(), idToUse, existing.getEndpoint());
                    if (!idToUse.equals(existing.getId())) {
                        // DP now sends a configured id different from the auto-generated UUID — replace the record
                        repository.deleteById(existing.getId());
                        log.info("Deleted old registration {} (replaced by {})", existing.getId(), idToUse);
                    }
                    DataPlaneRegistration updated = DataPlaneRegistration.Builder.newInstance()
                            .id(idToUse)
                            .endpoint(registration.getEndpoint())
                            .supportedTransferTypes(registration.getSupportedTransferTypes())
                            .transportProfiles(registration.getTransportProfiles())
                            .apiKey(hashedApiKey)
                            .apiKeyHint(apiKeyHint)
                            .build();
                    DataPlaneRegistration saved = repository.save(updated);
                    auditEventPublisher.publishEvent(AuditEventType.DATAPLANE_REGISTRATION_UPDATED,
                            "Data Plane registration updated for endpoint " + registration.getEndpoint(),
                            Map.of("endpoint", registration.getEndpoint(),
                                    "transferTypes", registration.getSupportedTransferTypes().toString()));
                    publishRegistrationChangedEvent(saved, DataPlaneRegistrationChangedEvent.ChangeType.REGISTERED);
                    return saved;
                })
                .orElseGet(() -> {
                    DataPlaneRegistration toSave = DataPlaneRegistration.Builder.newInstance()
                            .id(registration.getId())
                            .endpoint(registration.getEndpoint())
                            .supportedTransferTypes(registration.getSupportedTransferTypes())
                            .transportProfiles(registration.getTransportProfiles())
                            .authType(registration.getAuthType())
                            .lastHeartbeat(registration.getLastHeartbeat())
                            .registeredAt(registration.getRegisteredAt())
                            .apiKey(hashedApiKey)
                            .apiKeyHint(apiKeyHint)
                            .build();
                    DataPlaneRegistration saved = repository.save(toSave);
                    auditEventPublisher.publishEvent(AuditEventType.DATAPLANE_REGISTERED,
                            "Data Plane registered at endpoint " + registration.getEndpoint(),
                            Map.of("endpoint", registration.getEndpoint(),
                                    "transferTypes", registration.getSupportedTransferTypes().toString()));
                    publishRegistrationChangedEvent(saved, DataPlaneRegistrationChangedEvent.ChangeType.REGISTERED);
                    return saved;
                });
    }

    /**
     * Finds all Data Plane registrations that support the given transfer type.
     * Results are cached with a 10-second TTL to avoid a MongoDB round-trip on every
     * transfer dispatch. Cache is evicted on registration changes.
     *
     * @param transferType the transfer type identifier to search for
     * @return list of matching registrations
     */
    @Cacheable(cacheNames = "dataPlanesByType", key = "#transferType")
    public List<DataPlaneRegistration> findByTransferType(String transferType) {
        log.debug("Finding Data Planes supporting transfer type {}", transferType);
        return repository.findBySupportedTransferTypesContaining(transferType);
    }

    /**
     * Deregisters a Data Plane by its id.
     * Evicts the Data Plane routing cache so removed entries are no longer dispatched to.
     *
     * @param id the id of the registration to remove
     * @param rawApiKey the raw API key presented by the caller
     */
    @CacheEvict(cacheNames = "dataPlanesByType", allEntries = true)
    public void deregister(String id, String rawApiKey) {
        log.info("Deregistering Data Plane with id {}", id);
        DataPlaneRegistration existing = repository.findById(id)
                .orElseThrow(() -> {
                    auditEventPublisher.publishEvent(AuditEventType.DATAPLANE_REGISTRATION_NOT_FOUND,
                            "Data Plane registration not found for id " + id,
                            Map.of("id", id));
                    return new DataPlaneNotFoundException("Data Plane registration not found: " + id);
                });
        if (!apiKeyHasher.matches(rawApiKey, existing.getApiKey())) {
            log.warn("Rejected deregistration for id {} — API key mismatch", id);
            throw new DataPlaneUnauthorizedException("API key does not match registration " + id);
        }
        repository.deleteById(id);
        auditEventPublisher.publishEvent(AuditEventType.DATAPLANE_DEREGISTERED,
                "Data Plane deregistered at endpoint " + existing.getEndpoint(),
                Map.of("id", id, "endpoint", existing.getEndpoint()));
        publishRegistrationChangedEvent(existing, DataPlaneRegistrationChangedEvent.ChangeType.DEREGISTERED);
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
     * @param rawApiKey the raw API key to look up
     * @return an Optional containing the registration if found, or empty if no registration matches
     */
    public Optional<DataPlaneRegistration> findByApiKey(String rawApiKey) {
        log.debug("Looking up Data Plane by API key");
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByApiKey(apiKeyHasher.hash(rawApiKey));
    }

    private void publishRegistrationChangedEvent(DataPlaneRegistration registration,
                                                 DataPlaneRegistrationChangedEvent.ChangeType changeType) {
        applicationEventPublisher.publishEvent(
                new DataPlaneRegistrationChangedEvent(changeType, registration.getId(), registration.getEndpoint()));
    }
}
