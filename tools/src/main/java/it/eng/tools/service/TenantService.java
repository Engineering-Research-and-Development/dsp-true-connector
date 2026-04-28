package it.eng.tools.service;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.exception.TenantNotFoundException;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for managing tenants.
 */
@Service
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AuditEventPublisher auditEventPublisher;

    /**
     * Constructs the service with its repository and audit publisher dependencies.
     *
     * @param tenantRepository     the tenant repository
     * @param auditEventPublisher  the audit event publisher
     */
    public TenantService(TenantRepository tenantRepository, AuditEventPublisher auditEventPublisher) {
        this.tenantRepository = tenantRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * Finds a tenant by ID and requires it to be enabled.
     *
     * @param tenantId the tenant identifier
     * @return the enabled tenant
     * @throws TenantNotFoundException if the tenant does not exist or is disabled
     */
    public Tenant findEnabledTenantById(String tenantId) {
        return tenantRepository.findById(tenantId)
                .filter(Tenant::isEnabled)
                .orElseThrow(() -> {
                    auditEventPublisher.publishEvent(AuditEvent.Builder.newInstance()
                            .eventType(AuditEventType.TENANT_NOT_FOUND)
                            .description("Tenant not found or disabled: " + tenantId)
                            .details(Map.of("tenantId", tenantId))
                            .build());
                    return new TenantNotFoundException("Tenant not found or disabled: " + tenantId);
                });
    }

    /**
     * Finds a tenant by ID regardless of its enabled state.
     *
     * @param tenantId the tenant identifier
     * @return the tenant
     * @throws TenantNotFoundException if the tenant does not exist
     */
    public Tenant findById(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));
    }

    /**
     * Returns all tenants.
     *
     * @return list of all tenants
     */
    public List<Tenant> findAll() {
        return tenantRepository.findAll();
    }

    /**
     * Persists and returns the given tenant.
     *
     * @param tenant the tenant to save
     * @return the saved tenant
     */
    public Tenant saveTenant(Tenant tenant) {
        Tenant saved = tenantRepository.save(tenant);
        auditEventPublisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.TENANT_CREATED)
                .description("Tenant created: " + saved.getId())
                .details(Map.of("tenantId", saved.getId(), "tenantName", saved.getName()))
                .build());
        log.info("Created tenant: {}", saved.getId());
        return saved;
    }

    /**
     * Deletes the tenant with the given ID.
     *
     * @param tenantId the tenant identifier
     * @throws TenantNotFoundException if the tenant does not exist
     */
    public void deleteTenant(String tenantId) {
        Tenant tenant = findById(tenantId);
        tenantRepository.delete(tenant);
        auditEventPublisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.TENANT_DELETED)
                .description("Tenant deleted: " + tenantId)
                .details(Map.of("tenantId", tenantId, "tenantName", tenant.getName()))
                .build());
        log.info("Deleted tenant: {}", tenantId);
    }

    /**
     * Enables the tenant with the given ID.
     *
     * @param tenantId the tenant identifier
     * @return the updated, enabled tenant
     * @throws TenantNotFoundException if the tenant does not exist
     */
    public Tenant enableTenant(String tenantId) {
        Tenant existing = findById(tenantId);
        Tenant updated = rebuildWithEnabled(existing, true);
        Tenant saved = tenantRepository.save(updated);
        auditEventPublisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.TENANT_ENABLED)
                .description("Tenant enabled: " + tenantId)
                .details(Map.of("tenantId", tenantId, "tenantName", saved.getName()))
                .build());
        log.info("Enabled tenant: {}", tenantId);
        return saved;
    }

    /**
     * Disables the tenant with the given ID.
     *
     * @param tenantId the tenant identifier
     * @return the updated, disabled tenant
     * @throws TenantNotFoundException if the tenant does not exist
     */
    public Tenant disableTenant(String tenantId) {
        Tenant existing = findById(tenantId);
        Tenant updated = rebuildWithEnabled(existing, false);
        Tenant saved = tenantRepository.save(updated);
        auditEventPublisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.TENANT_DISABLED)
                .description("Tenant disabled: " + tenantId)
                .details(Map.of("tenantId", tenantId, "tenantName", saved.getName()))
                .build());
        log.info("Disabled tenant: {}", tenantId);
        return saved;
    }

    /**
     * Updates the mutable settings of an existing tenant (name, description, connectorId,
     * callbackAddress, automaticNegotiation, automaticTransfer).
     * The {@code enabled} state is preserved from the existing tenant.
     *
     * @param tenantId the tenant identifier
     * @param updates  the tenant containing the new values to apply
     * @return the saved, updated tenant
     * @throws TenantNotFoundException if the tenant does not exist
     */
    public Tenant updateTenant(String tenantId, Tenant updates) {
        Tenant existing = findById(tenantId);
        Tenant updated = Tenant.Builder.newInstance()
                .id(existing.getId())
                .version(existing.getVersion())
                .name(updates.getName() != null ? updates.getName() : existing.getName())
                .description(updates.getDescription() != null ? updates.getDescription() : existing.getDescription())
                .connectorId(updates.getConnectorId() != null ? updates.getConnectorId() : existing.getConnectorId())
                .callbackAddress(updates.getCallbackAddress() != null ? updates.getCallbackAddress() : existing.getCallbackAddress())
                .automaticNegotiation(updates.isAutomaticNegotiation())
                .automaticTransfer(updates.isAutomaticTransfer())
                .enabled(existing.isEnabled())
                .build();
        Tenant saved = tenantRepository.save(updated);
        auditEventPublisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.TENANT_UPDATED)
                .description("Tenant updated: " + tenantId)
                .details(Map.of("tenantId", tenantId, "tenantName", saved.getName()))
                .build());
        log.info("Updated tenant: {}", tenantId);
        return saved;
    }

    /**
     * Rebuilds a tenant preserving all fields but with the given enabled value.
     *
     * @param source  the source tenant whose values are copied
     * @param enabled the new enabled value
     * @return a new tenant instance with the updated enabled flag
     */
    private Tenant rebuildWithEnabled(Tenant source, boolean enabled) {
        return Tenant.Builder.newInstance()
                .id(source.getId())
                .version(source.getVersion())
                .name(source.getName())
                .description(source.getDescription())
                .connectorId(source.getConnectorId())
                .callbackAddress(source.getCallbackAddress())
                .automaticNegotiation(source.isAutomaticNegotiation())
                .automaticTransfer(source.isAutomaticTransfer())
                .enabled(enabled)
                .build();
    }
}
