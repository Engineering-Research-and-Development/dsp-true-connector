package it.eng.dataplane.httppush;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.service.FieldEncryptionService;
import it.eng.tools.service.TenantBucketResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP-PUSH transfer protocol implementation.
 * Acting as the provider side: downloads the artifact from its own presigned URL,
 * then pushes the data to the consumer's S3 bucket using the credentials provided
 * in the DataFlow's dataAddress. After a successful transfer the temporary consumer
 * IAM credentials are cleaned up.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HttpPushTransferProtocol implements DataTransferProtocol {

    private final S3ClientService s3ClientService;
    private final TemporaryBucketUserService temporaryBucketUserService;
    private final TenantBucketResolver tenantBucketResolver;
    private final FieldEncryptionService fieldEncryptionService;
    @Qualifier("transferExecutor")
    private final Executor transferExecutor;

    private static final int DEFAULT_CONNECT_TIMEOUT = 10_000; // 10 seconds
    /**
     * Fallback read timeout (30 minutes) used when the server does not advertise Content-Length.
     * Streaming and chunked responses omit Content-Length, so a short timeout causes silent failures
     * for large transfers. For known sizes the timeout is computed dynamically based on file size.
     */
    private static final int FALLBACK_READ_TIMEOUT = 1_800_000; // 30 minutes
    /** Assumed minimum transfer speed in bytes/sec used for dynamic timeout (1 MB/s). */
    private static final long MIN_TRANSFER_SPEED_BYTES_PER_SEC = 1024L * 1024L;

    /**
     * Returns the unique identifier for this transfer protocol.
     *
     * @return protocol identifier string "HttpData-PUSH"
     */
    @Override
    public String getProtocolId() {
        return "HttpData-PUSH";
    }

    /**
     * Initiates an HTTP-PUSH data transfer for the given data flow.
     * Generates a presigned GET URL for the provider artifact, downloads the artifact,
     * and pushes it to the consumer's S3 bucket using credentials from the data address.
     * On success, the temporary consumer credentials are deleted.
     *
     * @param dataFlow the data flow to initiate; its dataAddress must contain consumer S3 credentials
     * @return future with the result of the transfer
     */
    @Override
    public CompletableFuture<DataFlowResult> initiateTransfer(DataFlow dataFlow) {
        log.info("Initiating HTTP-PUSH transfer for data flow {}", dataFlow.getDataFlowId());

        Map<String, String> dataAddress = dataFlow.getDataAddress();
        if (dataAddress == null || !dataAddress.containsKey(S3Utils.BUCKET_NAME)) {
            return CompletableFuture.completedFuture(
                DataFlowResult.failure("dataAddress.bucketName is required for HttpData-PUSH")
            );
        }

        return pushArtifactToConsumer(dataFlow);
    }

    /**
     * Suspends an active data transfer.
     * Suspend is not supported for HTTP-PUSH transfers.
     *
     * @param dataFlowId the ID of the data flow to suspend
     * @return future with failure result indicating suspend is not supported
     */
    @Override
    public CompletableFuture<DataFlowResult> suspendTransfer(String dataFlowId) {
        log.warn("Suspend not supported for HttpData-PUSH transfer {}", dataFlowId);
        return CompletableFuture.completedFuture(
            DataFlowResult.failure("suspend not supported for HttpData-PUSH")
        );
    }

    /**
     * Resumes a suspended data transfer.
     * Resume is not supported for HTTP-PUSH transfers.
     *
     * @param dataFlowId the ID of the data flow to resume
     * @return future with failure result indicating resume is not supported
     */
    @Override
    public CompletableFuture<DataFlowResult> resumeTransfer(String dataFlowId) {
        log.warn("Resume not supported for HttpData-PUSH transfer {}", dataFlowId);
        return CompletableFuture.completedFuture(
            DataFlowResult.failure("resume not supported for HttpData-PUSH")
        );
    }

    /**
     * Terminates a data transfer.
     * For HTTP-PUSH, cleanup of temporary credentials is performed during {@link #initiateTransfer}
     * on success. If the transfer is terminated externally the cleanup cannot be performed here
     * because the process ID is not available from the data flow ID alone.
     *
     * @param dataFlowId the ID of the data flow to terminate
     * @return future with success result
     */
    @Override
    public CompletableFuture<DataFlowResult> terminateTransfer(String dataFlowId) {
        log.info("Terminating HttpData-PUSH transfer {} — temporary credential cleanup skipped (no processId available)", dataFlowId);
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }

    /**
     * Performs the actual push: generates a presigned provider URL, streams the artifact,
     * and uploads to the consumer bucket. Cleans up temporary credentials on success.
     *
     * @param dataFlow the data flow with provider and consumer metadata
     * @return future with transfer result
     */
    private CompletableFuture<DataFlowResult> pushArtifactToConsumer(DataFlow dataFlow) {
        // AtomicReference allows the connection to be shared across two separate lambda stages
        // (supplyAsync and whenComplete) without violating Java's effectively-final capture rule.
        // The supplyAsync lambda opens the connection and stores it here; whenComplete reads it
        // to guarantee disconnect() is called on all paths — success, failure, or cancellation.
        AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();

        // Resolve provider bucket and generate presigned URL before entering async lambda
        String providerBucket = tenantBucketResolver.resolveBucketName(dataFlow.getTenantId());
        String datasetId = dataFlow.getDatasetId();
        String presignedUrl = s3ClientService.generateGetPresignedUrl(providerBucket, datasetId, Duration.ofDays(1L));

        // Build consumer S3 properties, decrypting secretKey stored encrypted in MongoDB
        Map<String, String> dataAddress = dataFlow.getDataAddress();
        Map<String, String> consumerS3Properties = buildConsumerS3Properties(dataAddress, dataFlow.getProcessId());

        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(presignedUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                // Store immediately so whenComplete can close it even if a later step throws
                connectionRef.set(connection);

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
                connection.setReadTimeout(FALLBACK_READ_TIMEOUT);

                if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                    log.debug("Using HTTPS connection to provider artifact: {}", presignedUrl);
                } else {
                    log.debug("Using HTTP connection to provider artifact: {}", presignedUrl);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    // Disconnect eagerly on error response and clear the ref so whenComplete skips it
                    connection.disconnect();
                    connectionRef.set(null);
                    throw new RuntimeException("Failed to get provider artifact. HTTP response code: " + responseCode);
                }

                log.debug("Provider presigned URL: {}", presignedUrl);
                log.info("HTTP response code: {}", responseCode);

                long contentLength = connection.getContentLengthLong();
                if (contentLength > 0) {
                    int dynamicTimeout = computeReadTimeout(contentLength);
                    connection.setReadTimeout(dynamicTimeout);
                    log.debug("Content-Length: {} bytes — dynamic read timeout set to {} ms", contentLength, dynamicTimeout);
                }

                String contentType = connection.getContentType();
                String contentDisposition = connection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION);

                // uploadFile is non-blocking and returns a CompletableFuture<String>.
                // Returning it here produces a CompletableFuture<CompletableFuture<String>>,
                // which we'll flatten with thenCompose.
                // The connection must remain open until the upload future completes.
                return s3ClientService.uploadFile(
                    connection.getInputStream(),
                    consumerS3Properties,
                    contentType,
                    contentDisposition
                );
            } catch (IOException e) {
                // Disconnect on IOException before the upload started
                HttpURLConnection c = connectionRef.get();
                if (c != null) c.disconnect();
                log.error("Failed to download provider artifact from presigned URL: {}", presignedUrl, e);
                throw new RuntimeException(e.getMessage());
            } catch (RuntimeException e) {
                // Disconnect on RuntimeException to prevent leak
                HttpURLConnection c = connectionRef.get();
                if (c != null) c.disconnect();
                throw e;
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
        .handle((etag, throwable) -> {
            // Delete temporary IAM credentials regardless of whether the upload succeeded or failed.
            // Without this, a failed upload leaves live PutObject credentials in the consumer bucket
            // permanently (no TTL index on TemporaryBucketUser, no scheduled cleanup).
            String processId = dataFlow.getProcessId();
            if (StringUtils.isNotBlank(processId)) {
                try {
                    temporaryBucketUserService.deleteTemporaryUser(processId);
                    log.info("Cleaned up temporary credentials for transfer process {}", processId);
                } catch (Exception e) {
                    log.warn("Failed to clean up temporary credentials for process {}: {}", processId, e.getMessage());
                }
            }
            if (throwable != null) {
                log.error("HTTP-PUSH transfer failed for data flow {}", dataFlow.getDataFlowId(), throwable);
                return DataFlowResult.failure(throwable.getMessage());
            }
            String consumerBucket = consumerS3Properties.get(S3Utils.BUCKET_NAME);
            String objectKey = consumerS3Properties.get(S3Utils.OBJECT_KEY);
            log.info("Successfully pushed to consumer S3 bucket {} with key {}", consumerBucket, objectKey);
            return DataFlowResult.success();
        });
    }

    /**
     * Builds the consumer S3 properties map from the data address.
     * The secretKey is decrypted because it is stored encrypted in the TransferProcess.
     *
     * @param dataAddress the data address map from the DataFlow
     * @param processId   the transfer process ID used as object key fallback
     * @return map of S3 properties ready for use by S3ClientService
     */
    private Map<String, String> buildConsumerS3Properties(Map<String, String> dataAddress, String processId) {
        Map<String, String> props = new HashMap<>();
        props.put(S3Utils.BUCKET_NAME, dataAddress.get(S3Utils.BUCKET_NAME));

        String objectKey = dataAddress.getOrDefault(S3Utils.OBJECT_KEY, processId);
        props.put(S3Utils.OBJECT_KEY, objectKey);

        props.put(S3Utils.ACCESS_KEY, dataAddress.get(S3Utils.ACCESS_KEY));

        // The secretKey is stored encrypted in the TransferProcess — decrypt it here
        String encryptedSecret = dataAddress.get(S3Utils.SECRET_KEY);
        String plainSecret = StringUtils.isNotBlank(encryptedSecret)
            ? fieldEncryptionService.decrypt(encryptedSecret)
            : encryptedSecret;
        props.put(S3Utils.SECRET_KEY, plainSecret);

        String endpointOverride = dataAddress.get(S3Utils.ENDPOINT_OVERRIDE);
        if (StringUtils.isNotBlank(endpointOverride)) {
            props.put(S3Utils.ENDPOINT_OVERRIDE, endpointOverride);
        }

        String region = dataAddress.get(S3Utils.REGION);
        if (StringUtils.isNotBlank(region)) {
            props.put(S3Utils.REGION, region);
        }

        return props;
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
