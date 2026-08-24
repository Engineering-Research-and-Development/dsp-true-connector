package it.eng.tools.s3.configuration;

import it.eng.tools.s3.model.S3ClientRequest;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Abstraction over S3 client creation and caching.
 *
 * <p>Two implementations exist, selected by Spring conditions:
 * <ul>
 *   <li>{@link S3ClientProvider} — active in the Control Plane when {@code s3.access-key}
 *       is configured. Supports bootstrap credentials and admin-level operations.</li>
 *   <li>{@link DynamicS3ClientFactory} — active in Data Planes when {@code s3.access-key}
 *       is absent. Creates clients on demand from credentials carried in each request;
 *       requires {@code bucketCredentials} to be non-null in every {@link S3ClientRequest}.</li>
 * </ul>
 */
public interface S3ClientFactory {

    /**
     * Returns a cached synchronous S3 client for the given request.
     *
     * @param request the S3 client request containing credentials, region, and endpoint
     * @return a configured {@link S3Client} instance
     */
    S3Client getClient(S3ClientRequest request);

    /**
     * Returns a cached asynchronous S3 client for the given request.
     *
     * @param request the S3 client request containing credentials, region, and endpoint
     * @return a configured {@link S3AsyncClient} instance
     */
    S3AsyncClient getAsyncClient(S3ClientRequest request);

    /**
     * Clears cached S3 clients for a specific bucket so that new clients are built
     * with updated credentials on the next call.
     *
     * @param bucketName the bucket whose cached clients should be evicted
     */
    void clearBucketCache(String bucketName);

    /**
     * Returns a synchronous admin S3 client backed by bootstrap application-properties
     * credentials. Only available in Control Plane mode (when {@code s3.access-key} is set).
     *
     * @return a configured admin {@link S3Client}
     * @throws UnsupportedOperationException always, in Data Plane mode
     */
    default S3Client adminClient() {
        throw new UnsupportedOperationException(
                "adminClient() is not available in data-plane mode — s3.access-key is not configured");
    }
}
