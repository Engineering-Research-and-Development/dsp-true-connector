package it.eng.tools.model;

/**
 * Represents the resolved bucket-provisioning strategy for a tenant create/update request,
 * derived from the optional {@code bucketName}/{@code accessKey}/{@code secretKey} fields
 * carried by {@link TenantBucketCredentialsRequest}.
 */
public enum BucketProvisioningMode {

    /**
     * No bucket-related fields were supplied; the bucket and credentials are fully
     * auto-provisioned by the existing automatic provisioning flow.
     */
    AUTOMATIC,

    /**
     * Only {@code bucketName} was supplied; the existing bucket is reused and credentials
     * are auto-generated for it.
     */
    EXISTING_BUCKET,

    /**
     * {@code bucketName}, {@code accessKey}, and {@code secretKey} were all supplied; the
     * externally provided credentials are persisted as-is for the given bucket.
     */
    EXTERNAL_CREDENTIALS
}
