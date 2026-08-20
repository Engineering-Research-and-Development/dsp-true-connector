package it.eng.tools.service;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.exception.TenantNotFoundException;
import it.eng.tools.model.BucketProvisioningMode;
import it.eng.tools.model.Tenant;
import it.eng.tools.model.TenantBucketCredentialsRequest;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.service.BucketConnectionVerificationService;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.service.S3BucketProvisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Service for managing tenants.
 */
@Service
@Slf4j
public class TenantService {

    private static final Pattern TENANT_ID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9-]+$");
    private static final Pattern BUCKET_NAME_PATTERN =
            Pattern.compile("^[a-z0-9][a-z0-9\\-]{1,61}[a-z0-9]$");

    /** Prefix used when auto-deriving an S3 bucket name from the tenant identifier. */
    static final String BUCKET_NAME_PREFIX = "dsp-";
    private static final String CHANGE_TYPE_KEY = "changeType";
    private static final String CHANGE_TYPE_ORDINARY_UPDATE = "ORDINARY_UPDATE";
    private static final String CHANGE_TYPE_CREDENTIALS_ROTATED = "CREDENTIALS_ROTATED";
    private static final String CHANGE_TYPE_BUCKET_MIGRATED = "BUCKET_MIGRATED";

    private final TenantRepository tenantRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final S3BucketProvisionService s3BucketProvisionService;
    private final BucketCredentialsService bucketCredentialsService;
    private final BucketProvisioningModeResolver bucketProvisioningModeResolver;
    private final BucketConnectionVerificationService bucketConnectionVerificationService;
    private final String baseCallbackAddress;

    /**
     * Constructs the service with its repository, audit publisher, S3 provisioning
     * dependencies, and the configured application callback base URL.
     *
     * @param tenantRepository         the tenant repository
     * @param auditEventPublisher      the audit event publisher
     * @param s3BucketProvisionService the S3 bucket provisioning service
     * @param bucketCredentialsService the bucket credentials service
     * @param bucketProvisioningModeResolver the resolver for tenant bucket provisioning mode
     * @param bucketConnectionVerificationService the service verifying externally supplied bucket credentials
     * @param baseCallbackAddress      the base URL used to derive per-tenant callback addresses;
     *                                 injected from {@code application.callback.address}
     */
    public TenantService(TenantRepository tenantRepository,
                         AuditEventPublisher auditEventPublisher,
                         S3BucketProvisionService s3BucketProvisionService,
                         BucketCredentialsService bucketCredentialsService,
                         BucketProvisioningModeResolver bucketProvisioningModeResolver,
                         BucketConnectionVerificationService bucketConnectionVerificationService,
                         @Value("${application.callback.address}") String baseCallbackAddress) {
        this.tenantRepository = tenantRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.s3BucketProvisionService = s3BucketProvisionService;
        this.bucketCredentialsService = bucketCredentialsService;
        this.bucketProvisioningModeResolver = bucketProvisioningModeResolver;
        this.bucketConnectionVerificationService = bucketConnectionVerificationService;
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
     * Find tenants based on generic filter criteria.
     * Supports any field with automatic type detection and conversion.
     *
     * @param filters  Map of field names to filter values. All values are pre-validated and converted.
     * @param pageable Pageable
     * @return page of Tenant
     */
    public Page<Tenant> findAll(Map<String, Object> filters, Pageable pageable) {
        return tenantRepository.findWithDynamicFilters(filters, Tenant.class, pageable);
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
     * <p>Bucket handling depends on the resolved {@link BucketProvisioningMode}:
     * <ul>
     *     <li>{@link BucketProvisioningMode#AUTOMATIC}: bucket is auto-derived as
     *     {@code "dsp-" + tenantId.toLowerCase()} and provisioned via
     *     {@link S3BucketProvisionService#ensureBucketCredentials(String)}</li>
     *     <li>{@link BucketProvisioningMode#EXISTING_BUCKET}: request-supplied {@code bucketName}
     *     is used and provisioned/confirmed via
     *     {@link S3BucketProvisionService#ensureBucketCredentials(String)}</li>
     *     <li>{@link BucketProvisioningMode#EXTERNAL_CREDENTIALS}: request-supplied
     *     {@code bucketName}/{@code accessKey}/{@code secretKey} are persisted via
     *     {@link BucketCredentialsService#saveBucketCredentials(BucketCredentialsEntity)} and
     *     S3 auto-provisioning is skipped</li>
     * </ul>
     *
     * <p>When {@code verifyConnection=true} in external-credentials mode, the candidate credentials
     * are verified before any tenant or credentials are persisted. A failed verification throws
     * {@link IllegalArgumentException}.
     *
     * @param tenant the tenant to save; {@code id} must be provided and valid
     * @param credentialsRequest optional request-only bucket credential fields
     * @return the saved tenant
     * @throws IllegalArgumentException if the id format is invalid, the id already exists,
     *                                  another tenant already owns the resolved bucket name,
     *                                  or a tenant with the same participantId already exists
     */
    public Tenant saveTenant(Tenant tenant, TenantBucketCredentialsRequest credentialsRequest) {
        BucketProvisioningMode provisioningMode = bucketProvisioningModeResolver.resolve(credentialsRequest);

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

        String effectiveBucketName = resolveEffectiveBucketName(tenantId, credentialsRequest, provisioningMode);
        validateBucketNameFormat(effectiveBucketName);

        Tenant tenantToSave = Tenant.Builder.newInstance()
                .id(tenantId)
                .name(tenant.getName())
                .description(tenant.getDescription())
                .participantId(tenant.getParticipantId())
                .automaticNegotiation(tenant.isAutomaticNegotiation())
                .automaticTransfer(tenant.isAutomaticTransfer())
                .enabled(tenant.isEnabled())
                .bucketName(effectiveBucketName)
                .build();

        validateBucketOwnership(effectiveBucketName);
        applyBucketProvisioning(tenantId, credentialsRequest, effectiveBucketName, provisioningMode);

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
     * Persists a new tenant using fully automatic bucket provisioning.
     *
     * @param tenant the tenant to save; {@code id} must be provided and valid
     * @return the saved tenant
     * @throws IllegalArgumentException if tenant validation or uniqueness checks fail
     */
    public Tenant saveTenant(Tenant tenant) {
        TenantBucketCredentialsRequest automaticRequest = TenantBucketCredentialsRequest.Builder.newInstance()
                .verifyConnection(false)
                .build();
        return saveTenant(tenant, automaticRequest);
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
     * Updates the mutable settings of an existing tenant (name, description,
     * automaticNegotiation, automaticTransfer).
     *
     * <p>This overload preserves the historical update behavior and always applies
     * automatic mode for bucket handling, meaning bucket credentials are untouched and
     * the current bucket assignment is preserved.
     *
     * @param tenantId the tenant identifier
     * @param updates  the tenant containing the new values to apply
     * @return the saved, updated tenant
     * @throws TenantNotFoundException  if the tenant does not exist
     */
    public Tenant updateTenant(String tenantId, Tenant updates) {
        TenantBucketCredentialsRequest automaticRequest = TenantBucketCredentialsRequest.Builder.newInstance()
                .verifyConnection(false)
                .build();
        return updateTenant(tenantId, updates, automaticRequest);
    }

    /**
     * Updates the mutable settings of an existing tenant and applies tenant bucket
     * credential provisioning according to the resolved {@link BucketProvisioningMode}.
     *
     * <p>The {@code enabled} state and {@code participantId} are immutable in this endpoint
     * and are always preserved from the existing tenant.
     *
     * <p>Mode behavior:
     * <ul>
     *     <li>{@link BucketProvisioningMode#AUTOMATIC}: existing bucket and credentials are preserved</li>
     *     <li>{@link BucketProvisioningMode#EXISTING_BUCKET}: supplied bucket is ensured/generated via
     *     {@link S3BucketProvisionService#ensureBucketCredentials(String)} after ownership checks</li>
     *     <li>{@link BucketProvisioningMode#EXTERNAL_CREDENTIALS}: supplied credentials are optionally verified
     *     ({@code verifyConnection=true}) and then saved via
     *     {@link BucketCredentialsService#saveBucketCredentials(BucketCredentialsEntity)}</li>
     * </ul>
     *
     * @param tenantId the tenant identifier
     * @param updates tenant fields to apply
     * @param credentialsRequest request-only optional bucket credential fields
     * @return the saved, updated tenant
     * @throws TenantNotFoundException if tenant does not exist
     * @throws IllegalArgumentException if bucket ownership conflicts, bucket format is invalid,
     *                                  credential input is invalid, or verification fails
     */
    public Tenant updateTenant(String tenantId, Tenant updates, TenantBucketCredentialsRequest credentialsRequest) {
        Tenant existing = findById(tenantId);
        BucketProvisioningMode provisioningMode = bucketProvisioningModeResolver.resolve(credentialsRequest);

        String effectiveBucketName = resolveEffectiveUpdateBucketName(existing, credentialsRequest, provisioningMode);
        String changeType = resolveUpdateChangeType(existing.getBucketName(), effectiveBucketName, provisioningMode);

        if (provisioningMode != BucketProvisioningMode.AUTOMATIC) {
            validateBucketNameFormat(effectiveBucketName);
            validateBucketOwnershipForUpdate(effectiveBucketName, tenantId);
        }

        applyUpdateBucketProvisioning(tenantId, existing, credentialsRequest, effectiveBucketName, provisioningMode);

        Tenant updated = Tenant.Builder.newInstance()
                .id(existing.getId())
                .version(existing.getVersion())
                .name(updates.getName() != null ? updates.getName() : existing.getName())
                .description(updates.getDescription() != null ? updates.getDescription() : existing.getDescription())
                .participantId(existing.getParticipantId())  // immutable; any value in request body is silently ignored
                .automaticNegotiation(updates.isAutomaticNegotiation())
                .automaticTransfer(updates.isAutomaticTransfer())
                .enabled(existing.isEnabled())
                .bucketName(effectiveBucketName)
                .build();
        Tenant saved = tenantRepository.save(updated);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tenantId", tenantId);
        details.put("tenantName", saved.getName());
        details.put(CHANGE_TYPE_KEY, changeType);
        auditEventPublisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.TENANT_UPDATED)
                .description("Tenant updated: " + tenantId)
                .details(details)
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

    /**
     * Returns all tenants as a list needed for user creation.
     *
     * @return list of all tenants
     */
    public List<Tenant> findAllAsList() {
        return tenantRepository.findAll();
    }

    private String resolveEffectiveBucketName(
            String tenantId,
            TenantBucketCredentialsRequest credentialsRequest,
            BucketProvisioningMode provisioningMode) {
        if (provisioningMode == BucketProvisioningMode.AUTOMATIC) {
            return BUCKET_NAME_PREFIX + tenantId.toLowerCase();
        }
        return credentialsRequest.getBucketName();
    }

    private void validateBucketOwnership(String bucketName) {
        tenantRepository.findByBucketName(bucketName)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Bucket '" + bucketName + "' is already assigned to tenant: " + existing.getId());
                });
    }

    private void validateBucketOwnershipForUpdate(String bucketName, String tenantId) {
        tenantRepository.findByBucketNameAndIdNot(bucketName, tenantId)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Bucket '" + bucketName + "' is already assigned to tenant: " + existing.getId());
                });
    }

    private void validateBucketNameFormat(String bucketName) {
        if (!BUCKET_NAME_PATTERN.matcher(bucketName).matches()) {
            throw new IllegalArgumentException(
                    "Bucket name '" + bucketName + "' is invalid.");
        }
    }

    private void applyBucketProvisioning(
            String tenantId,
            TenantBucketCredentialsRequest credentialsRequest,
            String effectiveBucketName,
            BucketProvisioningMode provisioningMode) {
        if (provisioningMode == BucketProvisioningMode.EXTERNAL_CREDENTIALS) {
            if (credentialsRequest.isVerifyConnection()
                    && !bucketConnectionVerificationService.verify(
                    effectiveBucketName,
                    credentialsRequest.getAccessKey(),
                    credentialsRequest.getSecretKey())) {
                throw new IllegalArgumentException(
                        "Bucket credentials verification failed for bucket '" + effectiveBucketName + "'.");
            }
            bucketCredentialsService.saveBucketCredentials(BucketCredentialsEntity.Builder.newInstance()
                    .bucketName(effectiveBucketName)
                    .accessKey(credentialsRequest.getAccessKey())
                    .secretKey(credentialsRequest.getSecretKey())
                    .build());
            return;
        }

        log.info("Provisioning S3 bucket '{}' for new tenant '{}'", effectiveBucketName, tenantId);
        s3BucketProvisionService.ensureBucketCredentials(effectiveBucketName);
    }

    private String resolveEffectiveUpdateBucketName(
            Tenant existing,
            TenantBucketCredentialsRequest credentialsRequest,
            BucketProvisioningMode provisioningMode) {
        if (provisioningMode == BucketProvisioningMode.AUTOMATIC) {
            return existing.getBucketName();
        }
        return credentialsRequest.getBucketName();
    }

    private String resolveUpdateChangeType(
            String existingBucketName,
            String effectiveBucketName,
            BucketProvisioningMode provisioningMode) {
        if (!Objects.equals(existingBucketName, effectiveBucketName)) {
            return CHANGE_TYPE_BUCKET_MIGRATED;
        }
        if (provisioningMode == BucketProvisioningMode.EXTERNAL_CREDENTIALS) {
            return CHANGE_TYPE_CREDENTIALS_ROTATED;
        }
        return CHANGE_TYPE_ORDINARY_UPDATE;
    }

    private void applyUpdateBucketProvisioning(
            String tenantId,
            Tenant existing,
            TenantBucketCredentialsRequest credentialsRequest,
            String effectiveBucketName,
            BucketProvisioningMode provisioningMode) {
        if (provisioningMode == BucketProvisioningMode.AUTOMATIC) {
            return;
        }

        if (provisioningMode == BucketProvisioningMode.EXISTING_BUCKET) {
            if (Objects.equals(existing.getBucketName(), effectiveBucketName)) {
                log.info("Reconfirming existing S3 bucket '{}' for tenant '{}'", effectiveBucketName, tenantId);
            } else {
                log.info("Migrating tenant '{}' bucket from '{}' to '{}'",
                        tenantId, existing.getBucketName(), effectiveBucketName);
            }
            s3BucketProvisionService.ensureBucketCredentials(effectiveBucketName);
            return;
        }

        if (credentialsRequest.isVerifyConnection()
                && !bucketConnectionVerificationService.verify(
                effectiveBucketName,
                credentialsRequest.getAccessKey(),
                credentialsRequest.getSecretKey())) {
            throw new IllegalArgumentException(
                    "Bucket credentials verification failed for bucket '" + effectiveBucketName + "'.");
        }
        bucketCredentialsService.saveBucketCredentials(BucketCredentialsEntity.Builder.newInstance()
                .bucketName(effectiveBucketName)
                .accessKey(credentialsRequest.getAccessKey())
                .secretKey(credentialsRequest.getSecretKey())
                .build());
    }
}
