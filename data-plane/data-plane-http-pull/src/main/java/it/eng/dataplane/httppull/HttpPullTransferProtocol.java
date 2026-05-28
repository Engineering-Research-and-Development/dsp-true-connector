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
        AtomicBoolean wasPaused = new AtomicBoolean(false);
        return downloadAndUploadToS3(dataFlow, presignedUrl, 0L, resumable, wasPaused)
                .thenApply(result -> {
                    activeSuspendFlags.remove(dataFlowId);
                    if (wasPaused.get()) {
                        // Upload was stopped cooperatively — checkpoint was saved; no CP completion callback
                        log.info("HTTP-PULL transfer paused for processId={}", processId);
                        return DataFlowResult.success();
                    }
                    if (result.isSuccess()) {
                        controlPlaneClient.sendCompleted(dataFlow.getCallbackAddress(), dataFlow.getProcessId(), dataAddress);
                    } else {
                        controlPlaneClient.sendErrored(dataFlow.getCallbackAddress(), dataFlow.getProcessId(), result.getErrorMessage());
                    }
                    return result;
                });
    }

    /**
     * Signals a running transfer to stop cooperatively.
     *
     * <p>Sets the {@code suspendRequested} flag that the upload thread monitors; the upload
     * thread will detect the flag at the next part boundary, throw {@link UploadPausedException},
     * and persist a checkpoint with the upload state at that point. The Data Plane framework
     * calls this method <em>after</em> it has already cancelled the execution future, so the
     * checkpoint is written by the still-running upload thread independently of the future.</p>
     *
     * @param dataFlowId the MongoDB document ID of the data flow entity
     * @return future completing with success once the flag has been set
     */
    @Override
    public CompletableFuture<DataFlowResult> suspendTransfer(String dataFlowId) {
        log.info("Suspending HttpData-PULL transfer for dataFlowId={}", dataFlowId);
        AtomicBoolean flag = activeSuspendFlags.get(dataFlowId);
        if (flag != null) {
            flag.set(true);
            log.debug("Suspend flag set for dataFlowId={}", dataFlowId);
        } else {
            log.warn("No active suspend flag found for dataFlowId={}; transfer may have already completed", dataFlowId);
        }
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }

    /**
     * Resumes a suspended HTTP-PULL transfer from its saved checkpoint.
     *
     * <p>Loads the {@link DataFlowCheckpoint} for the process, reconstructs the
     * {@link ResumableUploadRequest} with previously uploaded parts and their ETags, and
     * re-opens the presigned GET URL with an HTTP {@code Range: bytes=N-} header to skip
     * already-processed bytes. The transfer then continues from where it left off.</p>
     *
     * <p>If the presigned URL returns a non-200/206 status (e.g. 403 Expired), a failure
     * result is returned immediately so the Control Plane can terminate the transfer and
     * re-issue credentials.</p>
     *
     * @param dataFlowId the MongoDB document ID of the suspended data flow entity
     * @return future with the result of the resumed transfer
     */
    @Override
    public CompletableFuture<DataFlowResult> resumeTransfer(String dataFlowId) {
        log.info("Resuming HttpData-PULL transfer for dataFlowId={}", dataFlowId);

        Optional<DataFlowEntity> entityOpt = dataFlowRepository.findById(dataFlowId);
        if (entityOpt.isEmpty()) {
            return CompletableFuture.completedFuture(
                    DataFlowResult.failure("DataFlowEntity not found for dataFlowId: " + dataFlowId));
        }
        DataFlowEntity entity = entityOpt.get();
        String processId = entity.getProcessId();

        Map<String, String> dataAddress = entity.getDataAddress();
        if (dataAddress == null || StringUtils.isBlank(dataAddress.get("endpoint"))) {
            return CompletableFuture.completedFuture(
                    DataFlowResult.failure("dataAddress.endpoint is missing; cannot resume dataFlowId: " + dataFlowId));
        }
        String presignedUrl = dataAddress.get("endpoint");

        Optional<DataFlowCheckpoint> checkpointOpt = checkpointService.findByProcessId(processId);
        long confirmedBytes;
        ResumableUploadRequest resumable;

        if (checkpointOpt.isPresent()) {
            DataFlowCheckpoint checkpoint = checkpointOpt.get();
            confirmedBytes = checkpoint.getConfirmedBytes();
            resumable = buildResumableFromCheckpoint(checkpoint, dataFlowId, processId);
            log.info("Resuming transfer processId={} from checkpoint: confirmedBytes={}, parts={}",
                    processId, confirmedBytes,
                    checkpoint.getCompletedParts() == null ? 0 : checkpoint.getCompletedParts().size());
        } else {
            confirmedBytes = 0L;
            AtomicBoolean freshFlag = new AtomicBoolean(false);
            activeSuspendFlags.put(dataFlowId, freshFlag);
            DataFlowCheckpoint newCheckpoint = DataFlowCheckpoint.Builder.newInstance()
                    .processId(processId)
                    .dataFlowId(dataFlowId)
                    .transferType(getProtocolId())
                    .tenantId(entity.getTenantId())
                    .destinationBucket(tenantBucketResolver.resolveBucketName(entity.getTenantId()))
                    .destinationObjectKey(processId)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            checkpointService.save(newCheckpoint);
            AtomicReference<DataFlowCheckpoint> cpRef = new AtomicReference<>(newCheckpoint);
            resumable = new ResumableUploadRequest(null, List.of(), List.of(), 0L, freshFlag,
                    buildCheckpointCallback(cpRef, processId, dataFlowId));
            log.info("No checkpoint found for processId={}; starting resumed transfer from byte 0", processId);
        }

        DataFlow resumeDataFlow = DataFlow.Builder.newInstance()
                .dataFlowId(dataFlowId)
                .processId(processId)
                .transferType(getProtocolId())
                .tenantId(entity.getTenantId())
                .dataAddress(dataAddress)
                .callbackAddress(entity.getCallbackAddress())
                .build();

        AtomicBoolean wasPaused = new AtomicBoolean(false);
        return downloadAndUploadToS3(resumeDataFlow, presignedUrl, confirmedBytes, resumable, wasPaused)
                .thenApply(result -> {
                    activeSuspendFlags.remove(dataFlowId);
                    if (wasPaused.get()) {
                        log.info("HTTP-PULL resumed transfer paused again for processId={}", processId);
                        return DataFlowResult.success();
                    }
                    if (result.isSuccess()) {
                        controlPlaneClient.sendCompleted(resumeDataFlow.getCallbackAddress(), processId, dataAddress);
                    } else {
                        controlPlaneClient.sendErrored(resumeDataFlow.getCallbackAddress(), processId, result.getErrorMessage());
                    }
                    return result;
                });
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
     * @param rangeStart   byte offset at which to start the download; 0 for a fresh download
     * @param resumable    the resumable upload context (suspend flag, checkpoint callback, prior parts)
     * @param wasPaused    set to {@code true} by this method when the upload is stopped cooperatively
     *                     by an {@link UploadPausedException}; callers use this to skip CP callbacks
     * @return future with transfer result
     */
    private CompletableFuture<DataFlowResult> downloadAndUploadToS3(DataFlow dataFlow,
                                                                     String presignedUrl,
                                                                     long rangeStart,
                                                                     ResumableUploadRequest resumable,
                                                                     AtomicBoolean wasPaused) {
        String bucketName = tenantBucketResolver.resolveBucketName(dataFlow.getTenantId());
        String objectKey = dataFlow.getProcessId();

        // Extract auth from data address before entering the async lambda
        Map<String, String> dataAddress = dataFlow.getDataAddress();
        String authType = dataAddress != null ? dataAddress.get(IConstants.AUTH_TYPE) : null;
        String token = dataAddress != null ? dataAddress.get(IConstants.AUTHORIZATION) : null;
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

                // Add Range header to skip already-uploaded bytes on resume
                if (rangeStart > 0) {
                    requestBuilder.header("Range", "bytes=" + rangeStart + "-");
                    log.info("Resuming download from byte {} for processId={}", rangeStart, dataFlow.getProcessId());
                }

                log.debug("Sending GET request to: {}", presignedUrl);
                HttpResponse<InputStream> response = httpClient.send(
                        requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

                int statusCode = response.statusCode();
                if (statusCode != 200 && statusCode != 206) {
                    closeQuietly(response.body());
                    throw new RuntimeException("Failed to get stream. HTTP response code: " + statusCode);
                }

                log.info("HTTP response code: {} for processId={}", statusCode, dataFlow.getProcessId());
                response.headers().firstValueAsLong("content-length").ifPresent(len ->
                        log.debug("Content-Length: {} bytes", len));

                String contentType = response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null);
                String contentDisposition = response.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION).orElse(null);

                Map<String, String> destinationS3Properties = buildS3Properties(bucketName, objectKey);

                // uploadFile returns a CompletableFuture<String>; flatten with thenCompose.
                // The response body InputStream is closed after the upload completes on all paths.
                return s3ClientService.uploadFile(
                        response.body(),
                        destinationS3Properties,
                        contentType,
                        contentDisposition,
                        resumable
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
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
            if (cause instanceof UploadPausedException) {
                log.info("HTTP-PULL transfer paused for processId={}", dataFlow.getProcessId());
                wasPaused.set(true);
                return DataFlowResult.success();
            }
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
