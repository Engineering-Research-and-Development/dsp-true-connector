package it.eng.dataplane.httppull;

import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.s3.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
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
    @Qualifier("dataPlaneHttpClient")
    private final HttpClient httpClient;
    private final ControlPlaneClient controlPlaneClient;

    /**
     * Request timeout (30 minutes) used for all artifact downloads.
     * Streaming and chunked responses may omit Content-Length; a short timeout causes silent
     * failures for large transfers. java.net.http.HttpClient requires the timeout to be set
     * before sending, so a generous fallback is used for all requests.
     */
    private static final int REQUEST_TIMEOUT_MS = 1_800_000; // 30 minutes

    /** Data address key indicating the calling mode (VIEW for consumer viewData). */
    static final String MODE_KEY = "mode";
    /** Mode value for consumer viewData requests — returns a pre-signed URL for the stored file. */
    static final String MODE_VIEW = "VIEW";
    /** Data address key carrying the pre-signed URL in the prepare response. */
    static final String PRESIGNED_URL_KEY = "presignedUrl";

    @Override
    public String getProtocolId() {
        return "HttpData-PULL";
    }

    /**
     * Generates a pre-signed GET URL for the requested S3 object and returns it in
     * the response {@code dataAddress}.
     *
     * <ul>
     *   <li>If {@code dataAddress.mode == VIEW}: the consumer Control Plane is requesting a URL
     *       so the API caller can download a previously stored file. The object key is
     *       {@code message.processId} (the transfer process ID used as key when storing).</li>
     *   <li>Otherwise (provider side): generates a URL for the artifact identified by
     *       {@code message.datasetId}. This URL is embedded in the DSP {@code TransferStartMessage}
     *       that the provider sends to the consumer.</li>
     * </ul>
     *
     * @param message the prepare message from the Control Plane
     * @return response containing {@code dataAddress.presignedUrl}
     */
    @Override
    public DataFlowPrepareResponse prepare(DataFlowPrepareMessage message) {
        String bucketName = s3Properties.getBucketName();
        String mode = message.getDataAddress() != null
                ? message.getDataAddress().getOrDefault(MODE_KEY, "")
                : "";

        String objectKey;
        if (MODE_VIEW.equals(mode)) {
            // Consumer viewData: the file was stored with processId as the object key
            objectKey = message.getProcessId();
            log.info("Preparing viewData presigned URL for processId={} in bucket={}", objectKey, bucketName);
        } else {
            // Provider side: object key is the dataset ID
            objectKey = message.getDatasetId();
            log.info("Preparing provider presigned URL for datasetId={} in bucket={}", objectKey, bucketName);
        }

        String presignedUrl = s3ClientService.generateGetPresignedUrl(bucketName, objectKey, Duration.ofDays(7L));
        log.debug("Generated presigned URL for mode='{}', objectKey='{}'", mode, objectKey);

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(PRESIGNED_URL_KEY, presignedUrl);

        return DataFlowPrepareResponse.Builder.newInstance()
                .processId(message.getProcessId())
                .dataAddress(dataAddress)
                .build();
    }

    /**
     * Initiates a data transfer for the given data flow.
     * Downloads data from the presigned URL in dataAddress.endpoint and uploads to S3.
     * Notifies the Control Plane via explicit {@code sendStarted}, {@code sendCompleted},
     * or {@code sendErrored} callbacks so the CP can drive DSP state transitions directly.
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

        // Notify CP that the DP has started processing — consumer CP can now update DSP state
        controlPlaneClient.sendStarted(dataFlow.getCallbackAddress(), dataFlow.getProcessId(), dataAddress);

        // Run the transfer asynchronously, then notify CP of outcome
        return downloadAndUploadToS3(dataFlow, presignedUrl)
                .thenApply(result -> {
                    if (result.isSuccess()) {
                        controlPlaneClient.sendCompleted(dataFlow.getCallbackAddress(), dataFlow.getProcessId(), dataAddress);
                    } else {
                        controlPlaneClient.sendErrored(dataFlow.getCallbackAddress(), dataFlow.getProcessId(), result.getErrorMessage());
                    }
                    return result;
                });
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
     * Uses {@link java.net.http.HttpClient} which negotiates HTTP/2 on TLS connections
     * and falls back to HTTP/1.1 for plain HTTP (e.g. development MinIO without TLS).
     *
     * @param dataFlow the data flow containing transfer metadata
     * @param presignedUrl the presigned GET URL to download from
     * @return future with transfer result
     */
    private CompletableFuture<DataFlowResult> downloadAndUploadToS3(DataFlow dataFlow, String presignedUrl) {
        // Resolve bucket in async context — must pass tenantId explicitly
        String bucketName = tenantBucketResolver.resolveBucketName(dataFlow.getTenantId());
        String objectKey = dataFlow.getProcessId();

        // Extract auth from data address before entering the async lambda
        Map<String, String> dataAddress = dataFlow.getDataAddress();
        String authType = dataAddress.get(IConstants.AUTH_TYPE);
        String token = dataAddress.get(IConstants.AUTHORIZATION);
        String authorization = (StringUtils.isNotBlank(authType) && StringUtils.isNotBlank(token))
                ? authType + " " + token : null;

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(presignedUrl))
                        .GET()
                        .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS));
                if (StringUtils.isNotBlank(authorization)) {
                    requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
                }

                log.debug("Sending GET request to: {}", presignedUrl);
                HttpResponse<InputStream> response = httpClient.send(
                        requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

                int statusCode = response.statusCode();
                if (statusCode != 200) {
                    closeQuietly(response.body());
                    throw new RuntimeException("Failed to get stream. HTTP response code: " + statusCode);
                }

                log.info("HTTP response code: {}", statusCode);
                response.headers().firstValueAsLong("content-length").ifPresent(len ->
                        log.debug("Content-Length: {} bytes", len));

                String contentType = response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null);
                String contentDisposition = response.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION).orElse(null);

                Map<String, String> destinationS3Properties = buildS3Properties(bucketName, objectKey);

                // uploadFile is non-blocking and returns a CompletableFuture<String>.
                // Returning it here produces a CompletableFuture<CompletableFuture<String>>,
                // which we'll flatten with thenCompose.
                // The response body InputStream is closed after the upload completes on all paths.
                return s3ClientService.uploadFile(
                        response.body(),
                        destinationS3Properties,
                        contentType,
                        contentDisposition
                ).whenComplete((etag, ex) -> closeQuietly(response.body()));
            } catch (IOException e) {
                log.error("Failed to download stream from URL: {}", presignedUrl, e);
                throw new RuntimeException(e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Download interrupted from URL: {}", presignedUrl, e);
                throw new RuntimeException("Transfer interrupted: " + e.getMessage());
            }
        }, transferExecutor)
        .thenCompose(uploadFuture -> uploadFuture)
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
        Map<String, String> props = new HashMap<>();
        props.put(it.eng.tools.s3.util.S3Utils.OBJECT_KEY, objectKey);
        props.put(it.eng.tools.s3.util.S3Utils.BUCKET_NAME, bucketName);
        props.put(it.eng.tools.s3.util.S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint());
        props.put(it.eng.tools.s3.util.S3Utils.REGION, s3Properties.getRegion());
        props.put(it.eng.tools.s3.util.S3Utils.ACCESS_KEY, s3Properties.getAccessKey());
        props.put(it.eng.tools.s3.util.S3Utils.SECRET_KEY, s3Properties.getSecretKey());
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
