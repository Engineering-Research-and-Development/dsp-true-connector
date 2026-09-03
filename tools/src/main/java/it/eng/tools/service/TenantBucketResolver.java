package it.eng.tools.service;

import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resolves the S3 bucket name for the current tenant context.
 *
 * <p>The primary resolution path reads {@link TenantContextHolder#getTenantId()} and looks
 * up the corresponding {@link Tenant#getBucketName()} in MongoDB.  When the holder is
 * empty (super-admin or non-request contexts) the service falls back to the globally
 * configured {@code s3.bucketName} property and logs a warning.
 *
 * <p>Use {@link #resolveBucketName(String)} instead of {@link #resolveBucketName()} when
 * calling from an asynchronous context where the thread-local tenant ID may not be
 * propagated.
 */
@Service
@Slf4j
public class TenantBucketResolver {

    private final TenantRepository tenantRepository;
    private final S3Properties s3Properties;

    /**
     * Constructs the resolver with its required dependencies.
     *
     * @param tenantRepository the repository used to look up tenant records
     * @param s3Properties     the global S3 configuration used as a fallback
     */
    public TenantBucketResolver(TenantRepository tenantRepository, S3Properties s3Properties) {
        this.tenantRepository = tenantRepository;
        this.s3Properties = s3Properties;
    }

    /**
     * Resolves the S3 bucket name for the currently active tenant.
     *
     * <p>Reads the tenant ID from {@link TenantContextHolder}. If no tenant context is set
     * (e.g. a super-admin request), falls back to the global {@code s3.bucketName} property.
     *
     * @return the bucket name for the current tenant, never {@code null}
     * @throws IllegalStateException if no bucket can be resolved
     */
    public String resolveBucketName() {
        String tenantId = TenantContextHolder.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            log.warn("No tenant context set — falling back to global S3 bucket. "
                    + "This is expected for super-admin requests but unexpected in tenant-scoped flows.");
            return requireGlobalBucket();
        }
        return resolveBucketName(tenantId);
    }

    /**
     * Resolves the S3 bucket name for the given tenant identifier.
     *
     * <p>Prefer this overload in asynchronous methods where the thread-local tenant context
     * may not be propagated. The tenant ID is typically obtained from a persisted entity such
     * as {@code TransferProcess.getTenantId()}.
     *
     * @param tenantId the tenant identifier whose bucket should be resolved
     * @return the bucket name for the given tenant, never {@code null}
     * @throws IllegalStateException if the tenant is not found or has no bucket configured
     */
    public String resolveBucketName(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            log.warn("Null or blank tenantId passed to resolveBucketName — falling back to global S3 bucket.");
            return requireGlobalBucket();
        }
        return tenantRepository.findById(tenantId)
                .map(Tenant::getBucketName)
                .filter(StringUtils::hasText)
                .orElseGet(() -> {
                    log.warn("Tenant '{}' has no bucket configured — falling back to global S3 bucket.", tenantId);
                    return requireGlobalBucket();
                });
    }

    private String requireGlobalBucket() {
        String bucket = s3Properties.getBucketName();
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException(
                    "No S3 bucket is configured: tenant has no bucketName and s3.bucketName property is blank.");
        }
        return bucket;
    }
}
