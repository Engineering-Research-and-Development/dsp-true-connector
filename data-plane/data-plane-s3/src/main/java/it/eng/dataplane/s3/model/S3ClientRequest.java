package it.eng.dataplane.s3.model;

/**
 * Value object encapsulating the S3 client request parameters.
 *
 * @param region             the AWS/MinIO region
 * @param endpointOverride   optional endpoint override URL for MinIO
 * @param bucketCredentials  bucket-scoped credentials; may be {@code null} for admin operations
 */
public record S3ClientRequest(String region, String endpointOverride, BucketCredentialsEntity bucketCredentials) {

    /**
     * Creates a request without bucket credentials (admin operations).
     *
     * @param region           the AWS/MinIO region
     * @param endpointOverride optional endpoint override URL
     * @return a new S3ClientRequest with no bucket credentials
     */
    public static S3ClientRequest from(String region, String endpointOverride) {
        return new S3ClientRequest(region, endpointOverride, null);
    }

    /**
     * Creates a request with explicit bucket credentials.
     *
     * @param region             the AWS/MinIO region
     * @param endpointOverride   optional endpoint override URL
     * @param bucketCredentials  the bucket-scoped credentials
     * @return a new S3ClientRequest
     */
    public static S3ClientRequest from(String region, String endpointOverride, BucketCredentialsEntity bucketCredentials) {
        return new S3ClientRequest(region, endpointOverride, bucketCredentials);
    }
}
