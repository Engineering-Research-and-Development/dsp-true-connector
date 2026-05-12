package it.eng.dataplane.s3.service;

import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves the S3 bucket name for the current Data Plane context.
 *
 * <p>Data Planes do not have multi-tenant bucket isolation; this implementation
 * always returns the globally configured {@code s3.bucketName} property.
 */
@Service
@Slf4j
public class TenantBucketResolver {

    private final S3Properties s3Properties;

    /**
     * Constructs the resolver with its required dependencies.
     *
     * @param s3Properties the global S3 configuration
     */
    public TenantBucketResolver(S3Properties s3Properties) {
        this.s3Properties = s3Properties;
    }

    /**
     * Resolves the S3 bucket name from the global configuration.
     *
     * @return the configured bucket name, never {@code null}
     * @throws IllegalStateException if no bucket is configured
     */
    public String resolveBucketName() {
        return requireGlobalBucket();
    }

    /**
     * Resolves the S3 bucket name.
     * The {@code tenantId} parameter is accepted for API compatibility with the
     * Control Plane version but is not used in Data Plane context.
     *
     * @param tenantId the tenant identifier (ignored in Data Plane context)
     * @return the configured bucket name, never {@code null}
     * @throws IllegalStateException if no bucket is configured
     */
    public String resolveBucketName(String tenantId) {
        return requireGlobalBucket();
    }

    private String requireGlobalBucket() {
        String bucket = s3Properties.getBucketName();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException(
                    "No S3 bucket is configured: s3.bucketName property is blank.");
        }
        return bucket;
    }
}
