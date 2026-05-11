package it.eng.dataplane.httppull;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.service.TenantBucketResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP-PULL transfer protocol implementation.
 * Downloads data from a presigned URL and uploads to the consumer's S3 bucket.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HttpPullTransferProtocol implements DataTransferProtocol {

    private final S3ClientService s3ClientService;
    private final S3Properties s3Properties;
    private final TenantBucketResolver tenantBucketResolver;
    @Qualifier("transferExecutor")
    private final Executor transferExecutor;

    private static final int DEFAULT_CONNECT_TIMEOUT = 10_000; // 10 seconds
    /**
     * Fallback read timeout (30 minutes) used when the server does not advertise
     * Content-Length. For known sizes the timeout is computed dynamically.
     */
    private static final int FALLBACK_READ_TIMEOUT = 1_800_000; // 30 minutes
    /** Assumed minimum transfer speed in bytes/sec used for dynamic timeout (1 MB/s). */
    private static final long MIN_TRANSFER_SPEED_BYTES_PER_SEC = 1024L * 1024L;

    /**
     * Returns the unique identifier for this transfer protocol.
     *
     * @return protocol identifier string
     */
    @Override
    public String getProtocolId() {
        return "HttpData-PULL";
    }

    /**
     * Initiates a data transfer for the given data flow.
     * Downloads data from the presigned URL in dataAddress.endpoint and uploads to S3.
     *
     * @param dataFlow the data flow to initiate
     * @return future with the result of the transfer initiation
     */
    @Override
    public CompletableFuture<DataFlowResult> initiateTransfer(DataFlow dataFlow) {
        log.info("Initiating HTTP-PULL transfer for data flow {}", dataFlow.getDataFlowId());
        
        // Extract presigned URL from data address
        Map<String, String> dataAddress = dataFlow.getDataAddress();
        if (dataAddress == null || !dataAddress.containsKey("endpoint")) {
            return CompletableFuture.completedFuture(
                DataFlowResult.failure("dataAddress.endpoint (presigned URL) is required for HttpData-PULL")
            );
        }
        
        String presignedUrl = dataAddress.get("endpoint");
        
        // Run the transfer asynchronously
        return downloadAndUploadToS3(dataFlow, presignedUrl);
    }

    /**
     * Suspends an active data transfer.
     * Suspend is not supported for HTTP-PULL transfers.
     *
     * @param dataFlowId the ID of the data flow to suspend
     * @return future with failure result
     */
    @Override
    public CompletableFuture<DataFlowResult> suspendTransfer(String dataFlowId) {
        log.warn("Suspend not supported for HttpData-PULL transfer {}", dataFlowId);
        return CompletableFuture.completedFuture(
            DataFlowResult.failure("suspend not supported for HttpData-PULL")
        );
    }

    /**
     * Resumes a suspended data transfer.
     * Resume is not supported for HTTP-PULL transfers.
     *
     * @param dataFlowId the ID of the data flow to resume
     * @return future with failure result
     */
    @Override
    public CompletableFuture<DataFlowResult> resumeTransfer(String dataFlowId) {
        log.warn("Resume not supported for HttpData-PULL transfer {}", dataFlowId);
        return CompletableFuture.completedFuture(
            DataFlowResult.failure("resume not supported for HttpData-PULL")
        );
    }

    /**
     * Terminates a data transfer.
     * For HTTP-PULL, this is a no-op since transfers complete immediately.
     *
     * @param dataFlowId the ID of the data flow to terminate
     * @return future with success result
     */
    @Override
    public CompletableFuture<DataFlowResult> terminateTransfer(String dataFlowId) {
        log.info("Terminating HttpData-PULL transfer {}", dataFlowId);
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }

    /**
     * Downloads data from presigned URL and uploads to S3.
     * Uses dynamic read timeout based on content length.
     *
     * @param dataFlow the data flow containing transfer metadata
     * @param presignedUrl the presigned GET URL to download from
     * @return future with transfer result
     */
    private CompletableFuture<DataFlowResult> downloadAndUploadToS3(DataFlow dataFlow, String presignedUrl) {
        // AtomicReference allows the connection to be shared across two separate lambda stages
        // (supplyAsync and whenComplete) without violating Java's effectively-final capture rule.
        // The supplyAsync lambda opens the connection and stores it here; whenComplete reads it
        // to guarantee disconnect() is called on all paths — success, failure, or cancellation.
        AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();
        
        // Resolve bucket in async context — must pass tenantId explicitly
        String bucketName = tenantBucketResolver.resolveBucketName(dataFlow.getTenantId());
        String objectKey = dataFlow.getProcessId();

        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(presignedUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                // Store immediately so whenComplete can close it even if a later step throws
                connectionRef.set(connection);

                // Configure connection
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
                connection.setReadTimeout(FALLBACK_READ_TIMEOUT);

                if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                    log.debug("Using HTTPS connection to: {}", presignedUrl);
                } else {
                    log.debug("Using HTTP connection to: {}", presignedUrl);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    // Disconnect eagerly on error response and clear the ref so whenComplete skips it
                    connection.disconnect();
                    connectionRef.set(null);
                    throw new RuntimeException("Failed to get stream. HTTP response code: " + responseCode);
                }

                log.info("Presigned URL: {}", presignedUrl);
                log.info("HTTP response code: {}", responseCode);

                long contentLength = connection.getContentLengthLong();
                if (contentLength > 0) {
                    int dynamicTimeout = computeReadTimeout(contentLength);
                    connection.setReadTimeout(dynamicTimeout);
                    log.debug("Content-Length: {} bytes — dynamic read timeout set to {} ms", contentLength, dynamicTimeout);
                }

                String contentType = connection.getContentType();
                String contentDisposition = connection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION);
                
                Map<String, String> destinationS3Properties = buildS3Properties(bucketName, objectKey);
                
                // uploadFile is non-blocking and returns a CompletableFuture<String>.
                // Returning it here produces a CompletableFuture<CompletableFuture<String>>,
                // which we'll flatten with thenCompose.
                // The connection must remain open until the upload future completes.
                return s3ClientService.uploadFile(
                    connection.getInputStream(), 
                    destinationS3Properties, 
                    contentType, 
                    contentDisposition
                );
            } catch (IOException e) {
                // Disconnect on IOException before the upload started (connection may or may not be open)
                HttpURLConnection c = connectionRef.get();
                if (c != null) c.disconnect();
                log.error("Failed to download stream from URL: {}", presignedUrl, e);
                throw new RuntimeException(e.getMessage());
            }
        }, transferExecutor)
        // Flatten the nested future and attach a cleanup handler that runs on all completion paths
        .thenCompose(uploadFuture ->
            uploadFuture.whenComplete((result, throwable) -> {
                // Disconnect after the upload completes (success or failure) to release the socket
                HttpURLConnection c = connectionRef.get();
                if (c != null) c.disconnect();
            })
        )
        .thenApply(etag -> {
            log.info("Successfully uploaded to S3 bucket {} with key {}", bucketName, objectKey);
            return DataFlowResult.success();
        })
        .exceptionally(throwable -> {
            log.error("HTTP-PULL transfer failed for data flow {}", dataFlow.getDataFlowId(), throwable);
            return DataFlowResult.failure(throwable.getMessage());
        });
    }

    /**
     * Builds S3 properties map for upload.
     *
     * @param bucketName the target S3 bucket
     * @param objectKey the target object key
     * @return S3 properties map
     */
    private Map<String, String> buildS3Properties(String bucketName, String objectKey) {
        return Map.of(
            S3Utils.OBJECT_KEY, objectKey,
            S3Utils.BUCKET_NAME, bucketName,
            S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint(),
            S3Utils.REGION, s3Properties.getRegion(),
            S3Utils.ACCESS_KEY, s3Properties.getAccessKey(),
            S3Utils.SECRET_KEY, s3Properties.getSecretKey()
        );
    }

    /**
     * Computes a dynamic read timeout based on file size and a conservative minimum
     * transfer speed of {@value MIN_TRANSFER_SPEED_BYTES_PER_SEC} bytes/sec (1 MB/s).
     * A 10 % safety margin is added on top.
     *
     * <p>Example: 100 MB file → ceil(100 × 1.1 / 1) = 110 seconds timeout.
     *
     * @param contentLengthBytes the total file size in bytes
     * @return the read timeout in milliseconds, capped at {@link Integer#MAX_VALUE}
     */
    private int computeReadTimeout(long contentLengthBytes) {
        long seconds = (long) Math.ceil(contentLengthBytes * 1.1 / MIN_TRANSFER_SPEED_BYTES_PER_SEC);
        long millis = seconds * 1000L;
        return (int) Math.min(millis, Integer.MAX_VALUE);
    }
}
