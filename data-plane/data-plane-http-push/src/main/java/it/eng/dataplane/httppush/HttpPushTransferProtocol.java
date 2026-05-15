package it.eng.dataplane.httppush;

import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.dataplane.s3.service.TenantBucketResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
    private final S3Properties s3Properties;
    private final TemporaryBucketUserService temporaryBucketUserService;
    private final TenantBucketResolver tenantBucketResolver;
    @Qualifier("transferExecutor")
    private final Executor transferExecutor;

    private static final int DEFAULT_CONNECT_TIMEOUT = 10_000; // 10 seconds
    /**
     * Request timeout (30 minutes) used for all artifact downloads.
     * java.net.http.HttpClient requires the timeout to be set before sending,
     * so a generous fallback is applied to all requests regardless of file size.
     */
    private static final int REQUEST_TIMEOUT_MS = 1_800_000; // 30 minutes

    /**
     * Shared HTTP client configured to prefer HTTP/2 (with automatic fallback to HTTP/1.1).
     * HTTP/2 is negotiated via ALPN on TLS connections (AWS S3, production MinIO with TLS).
     * Plain HTTP connections (development MinIO) fall back to HTTP/1.1 transparently.
     * The client is thread-safe and safe to share across concurrent virtual-thread transfers.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofMillis(DEFAULT_CONNECT_TIMEOUT))
            .build();

    /**
     * Returns the unique identifier for this transfer protocol.
     *
     * @return protocol identifier string "HttpData-PUSH"
     */
    @Override
    public String getProtocolId() {
        return "HttpData-PUSH";
    }

    /** Data address key indicating the calling mode (VIEW for consumer viewData). */
    private static final String MODE_KEY = "mode";
    /** Mode value for consumer viewData requests — returns a pre-signed URL for the pushed file. */
    private static final String MODE_VIEW = "VIEW";
    /** Data address key carrying the pre-signed URL in the prepare response. */
    private static final String PRESIGNED_URL_KEY = "presignedUrl";

    /**
     * Prepares the consumer-side bucket for an HTTP-PUSH transfer by creating a temporary
     * IAM user with write-only access to the consumer's bucket, or generates a presigned
     * GET URL when called with {@code dataAddress.mode = VIEW} (consumer viewData request).
     *
     * <p>Normal flow: the consumer Control Plane calls this before sending the DSP
     * {@code TransferRequestMessage} to the provider, so the temporary credentials can be
     * embedded in the message's {@code dataAddress}. The provider then uses these credentials
     * to push the artifact directly into the consumer's bucket.</p>
     *
     * <p>VIEW mode: the consumer Control Plane calls this after a completed transfer to obtain
     * a presigned GET URL for the file that was pushed to the consumer's bucket
     * (stored under key {@code processId}).</p>
     *
     * @param message the prepare message; {@code processId} is used as the S3 object key
     * @return response with {@code dataAddress} containing S3 credentials/bucket info or presigned URL
     */
    @Override
    public DataFlowPrepareResponse prepare(DataFlowPrepareMessage message) {
        String processId = message.getProcessId();
        String bucketName = s3Properties.getBucketName();

        Map<String, String> incoming = message.getDataAddress();
        if (incoming != null && MODE_VIEW.equals(incoming.get(MODE_KEY))) {
            log.info("Preparing viewData presigned URL for processId={} in bucket={}", processId, bucketName);
            String presignedUrl = s3ClientService.generateGetPresignedUrl(bucketName, processId, Duration.ofDays(7L));
            log.debug("Generated presigned URL for pushed file, objectKey='{}'", processId);
            return DataFlowPrepareResponse.Builder.newInstance()
                    .processId(processId)
                    .dataAddress(Map.of(PRESIGNED_URL_KEY, presignedUrl))
                    .build();
        }

        log.info("Preparing HTTP-PUSH temp user for processId={} in bucket={}", processId, bucketName);

        var tempUser = temporaryBucketUserService.createTemporaryUser(processId, bucketName, processId);
        log.info("Created temporary IAM user '{}' for processId={}", tempUser.getAccessKey(), processId);

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(S3Utils.BUCKET_NAME, bucketName);
        dataAddress.put(S3Utils.REGION, s3Properties.getRegion());
        dataAddress.put(S3Utils.OBJECT_KEY, processId);
        dataAddress.put(S3Utils.ACCESS_KEY, tempUser.getAccessKey());
        dataAddress.put(S3Utils.SECRET_KEY, tempUser.getSecretKey());
        String endpointOverride = StringUtils.isNotBlank(s3Properties.getExternalPresignedEndpoint())
                ? s3Properties.getExternalPresignedEndpoint()
                : s3Properties.getEndpoint();
        if (StringUtils.isNotBlank(endpointOverride)) {
            dataAddress.put(S3Utils.ENDPOINT_OVERRIDE, endpointOverride);
        }

        return DataFlowPrepareResponse.Builder.newInstance()
                .processId(processId)
                .dataAddress(dataAddress)
                .build();
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
     * Temporary IAM credentials created by the consumer CP for HTTP-PUSH transfers are cleaned up
     * by the consumer CP when it receives the termination event, not by this provider-side DP.
     *
     * @param processId the DPS transfer process ID
     * @return future with success result
     */
    @Override
    public CompletableFuture<DataFlowResult> terminateTransfer(String processId) {
        log.info("Terminating HttpData-PUSH transfer for processId={}", processId);
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }

    /**
     * Performs the actual push: generates a presigned provider URL, streams the artifact,
     * and uploads to the consumer bucket. Uses {@link java.net.http.HttpClient} which
     * negotiates HTTP/2 on TLS connections and falls back to HTTP/1.1 for plain HTTP.
     *
     * @param dataFlow the data flow with provider and consumer metadata
     * @return future with transfer result
     */
    private CompletableFuture<DataFlowResult> pushArtifactToConsumer(DataFlow dataFlow) {
        // Resolve provider bucket and generate presigned URL before entering async lambda
        String providerBucket = tenantBucketResolver.resolveBucketName(dataFlow.getTenantId());
        String datasetId = dataFlow.getDatasetId();
        String presignedUrl = s3ClientService.generateGetPresignedUrl(providerBucket, datasetId, Duration.ofDays(1L));

        // Build consumer S3 properties, decrypting secretKey stored encrypted in MongoDB
        Map<String, String> dataAddress = dataFlow.getDataAddress();
        Map<String, String> consumerS3Properties = buildConsumerS3Properties(dataAddress, dataFlow.getProcessId());

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(presignedUrl))
                        .GET()
                        .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                        .build();

                log.debug("Sending GET request to provider artifact: {}", presignedUrl);
                HttpResponse<InputStream> response = HTTP_CLIENT.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());

                int statusCode = response.statusCode();
                if (statusCode != 200) {
                    closeQuietly(response.body());
                    throw new RuntimeException("Failed to get provider artifact. HTTP response code: " + statusCode);
                }

                log.info("HTTP response code: {}", statusCode);
                response.headers().firstValueAsLong("content-length").ifPresent(len ->
                        log.debug("Content-Length: {} bytes", len));

                String contentType = response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null);
                String contentDisposition = response.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION).orElse(null);

                // uploadFile is non-blocking. The response body InputStream is closed after
                // the upload future completes on all paths.
                return s3ClientService.uploadFile(
                        response.body(),
                        consumerS3Properties,
                        contentType,
                        contentDisposition
                ).whenComplete((etag, ex) -> closeQuietly(response.body()));
            } catch (IOException e) {
                log.error("Failed to download provider artifact from presigned URL: {}", presignedUrl, e);
                throw new RuntimeException(e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Download interrupted from presigned URL: {}", presignedUrl, e);
                throw new RuntimeException("Transfer interrupted: " + e.getMessage());
            }
        }, transferExecutor)
        .thenCompose(uploadFuture -> uploadFuture)
        .handle((etag, throwable) -> {
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
     * The secretKey is passed as plain text — the consumer CP creates the temporary IAM user
     * and places the unencrypted secretKey into the {@code DataFlowStartMessage.dataAddress}
     * that it sends to the provider CP, which forwards it to this DP unchanged.
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

        // The secretKey arrives as plain text — set directly without decryption.
        // The consumer CP creates the temp user, places the plain secretKey in the DataFlowStartMessage
        // dataAddress, and the provider CP forwards it here without encrypting.
        props.put(S3Utils.SECRET_KEY, dataAddress.get(S3Utils.SECRET_KEY));

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
     * Closes an {@link InputStream} silently, suppressing any {@link IOException}.
     * Used to release the HTTP response body socket after upload completes or fails.
     *
     * @param is the stream to close; no-op if {@code null}
     */
    private static void closeQuietly(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException ignored) {
                // intentionally suppressed
            }
        }
    }
}
