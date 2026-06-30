package it.eng.tools.service;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.exception.TenantNotFoundException;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.s3.service.S3BucketProvisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Service for managing tenants.
 */
@Service
@Slf4j
public class TenantService {

    private static final java.util.regex.Pattern TENANT_ID_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9-]+$");

    private final TenantRepository tenantRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final S3BucketProvisionService s3BucketProvisionService;
    private final String baseCallbackAddress;

    /**
     * Constructs the service with its repository, audit publisher, S3 provisioning
     * dependencies, and the configured application callback base URL.
     *
     * @param tenantRepository         the tenant repository
     * @param auditEventPublisher      the audit event publisher
     * @param s3BucketProvisionService the S3 bucket provisioning service
     * @param baseCallbackAddress      the base URL used to derive per-tenant callback addresses;
     *                                 injected from {@code application.callback.address}
     */
    public TenantService(TenantRepository tenantRepository,
                         AuditEventPublisher auditEventPublisher,
                         S3BucketProvisionService s3BucketProvisionService,
                         @Value("${application.callback.address}") String baseCallbackAddress) {
        this.tenantRepository = tenantRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.s3BucketProvisionService = s3BucketProvisionService;
        this.baseCallbackAddress = baseCallbackAddress;
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
     * Persists a new tenant using the client-supplied identifier.
     *
     * <p>The {@code id} on the incoming {@code tenant} is used directly and must be
     * non-null and composed exclusively of alphanumeric characters and hyphens
     * ({@code ^[a-zA-Z0-9-]+$}).  An {@link IllegalArgumentException} is thrown if the
     * format is invalid or if the id is already taken.
     *
     * <p>Participant IDs must be unique across all tenants.  If another tenant with the same
     * {@code participantId} already exists, an {@link IllegalArgumentException} is thrown.
     *
     * <p>If the tenant has a {@code bucketName} configured, the S3 bucket is provisioned
     * (or confirmed to exist) before the tenant is saved.  Bucket provisioning failure
     * prevents the tenant from being persisted.
     *
     * @param tenant the tenant to save; {@code id} must be provided and valid
     * @return the saved tenant
     * @throws IllegalArgumentException if the id format is invalid, the id already exists,
     *                                  another tenant already owns the requested bucket,
     *                                  or a tenant with the same participantId already exists
     */
    public Tenant saveTenant(Tenant tenant) {
        String tenantId = tenant.getId();
        if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new IllegalArgumentException(
                    "Tenant id '" + tenantId + "' is invalid: only alphanumeric characters and hyphens are allowed.");
        }
        tenantRepository.findById(tenantId)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Tenant with id '" + tenantId + "' already exists.");
                });
        tenantRepository.findByParticipantId(tenant.getParticipantId())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Tenant with participantId '" + tenant.getParticipantId() + "' already exists: " + existing.getId());
                });

        Tenant tenantToSave = Tenant.Builder.newInstance()
                .id(tenantId)
                .name(tenant.getName())
                .description(tenant.getDescription())
                .participantId(tenant.getParticipantId())
                .automaticNegotiation(tenant.isAutomaticNegotiation())
                .automaticTransfer(tenant.isAutomaticTransfer())
                .enabled(tenant.isEnabled())
                .bucketName(tenant.getBucketName())
                .build();

        String bucketName = tenantToSave.getBucketName();
        if (StringUtils.hasText(bucketName)) {
            tenantRepository.findByBucketName(bucketName)
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "Bucket '" + bucketName + "' is already assigned to tenant: " + existing.getId());
                    });
            log.info("Provisioning S3 bucket '{}' for new tenant '{}'", bucketName, tenantId);
            s3BucketProvisionService.ensureBucketCredentials(bucketName);
        }
        Tenant saved = tenantRepository.save(tenantToSave);
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
     * <p>If the tenant has an S3 bucket configured, the bucket is <strong>not</strong>
     * automatically deleted to prevent accidental data loss from in-flight transfers or
     * persisted artifacts. A warning is logged reminding the operator to clean up the bucket
     * manually when all data has been migrated or removed.
     *
     * @param tenantId the tenant identifier
     * @throws TenantNotFoundException if the tenant does not exist
     */
    public void deleteTenant(String tenantId) {
        Tenant tenant = findById(tenantId);
        tenantRepository.delete(tenant);
        if (StringUtils.hasText(tenant.getBucketName())) {
            log.warn("Tenant '{}' was deleted but its S3 bucket '{}' was NOT removed. "
                    + "Clean up the bucket manually once all artifact data has been migrated or is no longer needed.",
                    tenantId, tenant.getBucketName());
        }
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
     * Updates the mutable settings of an existing tenant (name, description, participantId,
     * automaticNegotiation, automaticTransfer, bucketName).
     * The {@code enabled} state is preserved from the existing tenant.
     *
     * <p>If {@code bucketName} is changed, the new bucket is provisioned before the tenant
     * is updated.  The old bucket is <strong>not</strong> deleted automatically.
     *
     * @param tenantId the tenant identifier
     * @param updates  the tenant containing the new values to apply
     * @return the saved, updated tenant
     * @throws TenantNotFoundException  if the tenant does not exist
     * @throws IllegalArgumentException if the new bucket name is already owned by another tenant
     */
    public Tenant updateTenant(String tenantId, Tenant updates) {
        Tenant existing = findById(tenantId);
        String newBucketName = updates.getBucketName() != null ? updates.getBucketName() : existing.getBucketName();
        if (StringUtils.hasText(newBucketName) && !newBucketName.equals(existing.getBucketName())) {
            tenantRepository.findByBucketName(newBucketName)
                    .filter(owner -> !owner.getId().equals(tenantId))
                    .ifPresent(owner -> {
                        throw new IllegalArgumentException(
                                "Bucket '" + newBucketName + "' is already assigned to tenant: " + owner.getId());
                    });
            log.info("Provisioning new S3 bucket '{}' for tenant '{}'", newBucketName, tenantId);
            s3BucketProvisionService.ensureBucketCredentials(newBucketName);
            if (StringUtils.hasText(existing.getBucketName())) {
                log.warn("Tenant '{}' bucket changed from '{}' to '{}'. "
                        + "The old bucket was NOT deleted — clean it up manually if no longer needed.",
                        tenantId, existing.getBucketName(), newBucketName);
            }
        }
        Tenant updated = Tenant.Builder.newInstance()
                .id(existing.getId())
                .version(existing.getVersion())
                .name(updates.getName() != null ? updates.getName() : existing.getName())
                .description(updates.getDescription() != null ? updates.getDescription() : existing.getDescription())
                .participantId(updates.getParticipantId() != null ? updates.getParticipantId() : existing.getParticipantId())
                .automaticNegotiation(updates.isAutomaticNegotiation())
                .automaticTransfer(updates.isAutomaticTransfer())
                .enabled(existing.isEnabled())
                .bucketName(newBucketName)
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
                .participantId(source.getParticipantId())
                .automaticNegotiation(source.isAutomaticNegotiation())
                .automaticTransfer(source.isAutomaticTransfer())
                .enabled(enabled)
                .bucketName(source.getBucketName())
                .build();
    }
}
