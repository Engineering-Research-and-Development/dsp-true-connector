package it.eng.tools.auth.keycloak.realm;

import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import it.eng.tools.service.FieldEncryptionService;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages per-tenant Keycloak realm client credentials. The secret is encrypted before it is
 * persisted and decrypted only by {@link #getDecrypted(String)}.
 */
@Slf4j
@Service
public class RealmCredentialsService {

    private final RealmCredentialsRepository repository;
    private final FieldEncryptionService fieldEncryptionService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Constructs the service.
     *
     * @param repository             the credentials repository
     * @param fieldEncryptionService the encryption service
     * @param eventPublisher         the event publisher used for change notifications
     */
    public RealmCredentialsService(RealmCredentialsRepository repository,
            FieldEncryptionService fieldEncryptionService, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.fieldEncryptionService = fieldEncryptionService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Encrypts and saves the client secret for a tenant and publishes a change event.
     * The returned entity carries the encrypted secret and must not be used for outbound calls.
     *
     * @param tenantId     the tenant identifier
     * @param clientSecret the plain client secret
     * @return the saved entity (secret encrypted)
     */
    public RealmCredentials save(String tenantId, String clientSecret) {
        Long existingVersion = repository.findById(tenantId).map(RealmCredentials::getVersion).orElse(null);
        RealmCredentials toSave = RealmCredentials.Builder.newInstance()
                .tenantId(tenantId)
                .clientSecret(fieldEncryptionService.encrypt(clientSecret))
                .version(existingVersion)
                .build();
        RealmCredentials saved = repository.save(toSave);
        log.info("Saved realm credentials for tenant: {}", tenantId);
        eventPublisher.publishEvent(new RealmCredentialsChangedEvent(tenantId));
        return saved;
    }

    /**
     * Returns the decrypted client secret of a tenant.
     *
     * @param tenantId the tenant identifier
     * @return the plain client secret, or empty when none is configured
     */
    public Optional<String> getDecrypted(String tenantId) {
        return repository.findById(tenantId)
                .map(c -> fieldEncryptionService.decrypt(c.getClientSecret()));
    }

    /**
     * Checks whether credentials exist for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return {@code true} if credentials are configured
     */
    public boolean exists(String tenantId) {
        return repository.existsById(tenantId);
    }

    /**
     * Deletes the credentials of a tenant, if present, and publishes a change event.
     *
     * @param tenantId the tenant identifier
     */
    public void delete(String tenantId) {
        repository.deleteById(tenantId);
        log.info("Deleted realm credentials for tenant: {}", tenantId);
        eventPublisher.publishEvent(new RealmCredentialsChangedEvent(tenantId));
    }
}
