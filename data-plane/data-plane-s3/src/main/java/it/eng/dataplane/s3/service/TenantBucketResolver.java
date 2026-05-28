package it.eng.dataplane.s3.service;

import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves the S3 bucket name for the current Data Plane context.
 *
 * <p>This resolver is only a fallback helper for Data Plane code paths that still need
 * a locally configured bucket. It does not represent tenant-aware bucket authority and
 * always returns the globally configured {@code s3.bucketName} property when available.
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
     * Resolves the fallback S3 bucket name from the global configuration.
     *
     * @return the configured bucket name, never {@code null}
     * @throws IllegalStateException if no fallback bucket is configured
     */
    public String resolveBucketName() {
        return requireGlobalBucket();
    }

    /**
     * Resolves the fallback S3 bucket name.
     *
     * <p>The {@code tenantId} parameter is accepted for API compatibility but is not used to
     * select a bucket in the Data Plane.</p>
     *
     * @param tenantId the tenant identifier (ignored in Data Plane context)
     * @return the configured bucket name, never {@code null}
     * @throws IllegalStateException if no fallback bucket is configured
     */
    public String resolveBucketName(String tenantId) {
        return requireGlobalBucket();
    }

    private String requireGlobalBucket() {
        String bucket = s3Properties.getBucketName();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException(
                    "No Data Plane fallback bucket is configured: s3.bucketName property is blank.");
        }
        return bucket;
    }
}
