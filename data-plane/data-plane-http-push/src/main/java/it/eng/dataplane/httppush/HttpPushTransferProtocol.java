package it.eng.dataplane.httppush;

import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.model.DataFlowCheckpoint;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.repository.DataFlowRepository;
import it.eng.dataplane.core.service.DataFlowCheckpointService;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.s3.service.upload.ResumableUploadRequest;
import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import it.eng.tools.s3.service.upload.UploadPausedException;
import it.eng.tools.s3.util.S3Utils;
import it.eng.dataplane.s3.service.TenantBucketResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP-PUSH transfer protocol implementation.
 * Acting as the provider side: downloads the artifact from its own presigned URL,
 * then pushes the data to the consumer's S3 bucket using the credentials provided
 * in the DataFlow's dataAddress. After a successful transfer the temporary consumer
 * IAM credentials are cleaned up.
 *
 * <p>Suspend/resume is supported via per-flow {@link AtomicBoolean} flags.  When
 * {@code DataFlowService.suspend()} requests suspension it cancels the outer future
 * and then calls {@link #suspendTransfer(String)}, which flips the flag.  The
 * ongoing S3 upload detects the flag and raises {@link UploadPausedException}.  The
 * inner upload future's {@code whenComplete} handler persists the checkpoint to
 * MongoDB so that a subsequent {@link #resumeTransfer(String)} call can continue
 * from the last confirmed byte.</p>
 *
 * <p>On resume the existing consumer-side S3 credentials stored in the suspended
 * entity's {@code dataAddress} are reused directly — no new temporary IAM user is
 * created.</p>
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
    @Qualifier("dataPlaneHttpClient")
    private final HttpClient httpClient;
    private final ControlPlaneClient controlPlaneClient;
    private final DataFlowCheckpointService checkpointService;
    private final DataFlowRepository dataFlowRepository;

    /**
     * Active suspend flags keyed by DSP transfer process ID.
     * Used to signal in-progress S3 uploads to pause cleanly.
     */
    private final ConcurrentHashMap<String, AtomicBoolean> activeSuspendFlags = new ConcurrentHashMap<>();

    /**
     * Request timeout (30 minutes) used for all artifact downloads.
     * java.net.http.HttpClient requires the timeout to be set before sending,
     * so a generous fallback is applied to all requests regardless of file size.
     */
    private static final int REQUEST_TIMEOUT_MS = 1_800_000; // 30 minutes

    /** Data address key indicating the calling mode (VIEW for consumer viewData). */
    private static final String MODE_KEY = "mode";
    /** Mode value for consumer viewData requests — returns a pre-signed URL for the pushed file. */
    private static final String MODE_VIEW = "VIEW";
    /** Data address key carrying the pre-signed URL in the prepare response. */
    private static final String PRESIGNED_URL_KEY = "presignedUrl";

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
     * Notifies the Control Plane via explicit {@code sendStarted}, {@code sendCompleted},
     * or {@code sendErrored} callbacks so the CP can drive DSP state transitions directly.
     *
     * <p>The upload is performed using the resumable multipart upload API so that
     * a subsequent {@link #suspendTransfer(String)} call can pause the upload and
     * save a checkpoint for later resumption.</p>
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

        // Notify CP that the DP has started processing — provider CP can now update DSP state
        controlPlaneClient.sendStarted(dataFlow.getCallbackAddress(), dataFlow.getProcessId(), dataAddress);

        AtomicBoolean suspendFlag = new AtomicBoolean(false);
        activeSuspendFlags.put(dataFlow.getProcessId(), suspendFlag);

        // Run the transfer asynchronously, then notify CP of outcome
        return pushArtifactAsync(dataFlow, suspendFlag, null, List.of(), List.of(), 0L)
                .thenApply(result -> {
                    if (result.isPaused()) {
                        // Upload paused cleanly — no CP callback. The outer future is cancelled by
                        // DataFlowService.suspend() before or around the same time this runs, so
                        // this guard ensures sendCompleted is never sent even in the rare race
                        // where the thenApply executes before the cancel propagates.
                        return result;
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
     * Suspends an active HTTP-PUSH data transfer by setting the per-flow suspend flag.
     *
     * <p>When {@code DataFlowService.suspend()} calls this method, it has already cancelled
     * the outer execution future to prevent stale-future completion races.  Setting the flag
     * causes the S3 upload strategy to stop uploading new parts and raise
     * {@link UploadPausedException}.  The inner upload future's {@code whenComplete} handler
     * then persists the checkpoint to MongoDB regardless of the outer future's state.</p>
     *
     * <p>This method returns immediately — checkpoint persistence is asynchronous and
     * completes slightly after this method returns.</p>
     *
     * @param dataFlowId the entity ID ({@code DataFlowEntity.getId()}) of the flow to suspend
     * @return completed future with success
     */
    @Override
    public CompletableFuture<DataFlowResult> suspendTransfer(String dataFlowId) {
        // dataFlowId is entity.getId(); we need processId to look up the flag
        dataFlowRepository.findById(dataFlowId).ifPresentOrElse(entity -> {
            AtomicBoolean flag = activeSuspendFlags.get(entity.getProcessId());
            if (flag != null) {
                log.info("Requesting upload pause for HttpData-PUSH processId={}", entity.getProcessId());
                flag.set(true);
            } else {
                log.info("No active upload to pause for processId={} (already completed or not started yet)",
                        entity.getProcessId());
            }
        }, () -> log.warn("suspendTransfer: no entity found for dataFlowId={}", dataFlowId));
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }

    /**
     * Resumes a suspended HTTP-PUSH data transfer using the stored checkpoint and
     * the consumer destination credentials already present in the entity's dataAddress.
     *
     * <p>No new temporary IAM user is created — the credentials persisted at transfer-start
     * time are reused directly.  If those credentials have expired or been revoked the
     * upload will fail and {@link DataFlowResult#failure(String)} is returned, allowing
     * the Control Plane to terminate and restart the transfer.</p>
     *
     * @param dataFlowId the entity ID ({@code DataFlowEntity.getId()}) of the flow to resume
     * @return future with the result of the resumed transfer
     */
    @Override
    public CompletableFuture<DataFlowResult> resumeTransfer(String dataFlowId) {
        DataFlowEntity entity = dataFlowRepository.findById(dataFlowId).orElse(null);
        if (entity == null) {
            log.warn("resumeTransfer: no entity found for dataFlowId={}", dataFlowId);
            return CompletableFuture.completedFuture(
                    DataFlowResult.failure("No DataFlowEntity found for dataFlowId: " + dataFlowId));
        }

        String processId = entity.getProcessId();
        DataFlowCheckpoint checkpoint = checkpointService.findByProcessId(processId).orElse(null);
        if (checkpoint == null) {
            log.warn("resumeTransfer: no checkpoint for processId={}", processId);
            return CompletableFuture.completedFuture(
                    DataFlowResult.failure("No resumable checkpoint available for processId: " + processId));
        }

        Map<String, String> dataAddress = entity.getDataAddress();
        if (dataAddress == null || !dataAddress.containsKey(S3Utils.BUCKET_NAME)) {
            return CompletableFuture.completedFuture(
                    DataFlowResult.failure("Consumer S3 credentials missing from dataAddress for processId: " + processId));
        }

        log.info("Resuming HTTP-PUSH transfer processId={} from confirmedBytes={}", processId, checkpoint.getConfirmedBytes());

        List<CompletedPart> previousParts = buildCompletedParts(checkpoint);
        List<Long> previousSizes = buildPartSizes(checkpoint);

        AtomicBoolean suspendFlag = new AtomicBoolean(false);
        activeSuspendFlags.put(processId, suspendFlag);

        DataFlow dataFlow = toDataFlow(entity);

        return pushArtifactAsync(dataFlow, suspendFlag,
                checkpoint.getUploadId(), previousParts, previousSizes, checkpoint.getConfirmedBytes())
                .thenApply(result -> {
                    if (result.isPaused()) {
                        // Upload paused again during the resumed transfer.
                        // Checkpoint is preserved in MongoDB for the next resume; no CP callback.
                        return result;
                    }
                    if (result.isSuccess()) {
                        checkpointService.deleteByProcessId(processId);
                        controlPlaneClient.sendCompleted(entity.getCallbackAddress(), processId, dataAddress);
                    } else {
                        controlPlaneClient.sendErrored(entity.getCallbackAddress(), processId, result.getErrorMessage());
                    }
                    return result;
                });
    }

    /**
     * Returns {@code true} if the given data flow has usable consumer-side S3 credentials
     * in its {@code dataAddress}.
     *
     * <p>HTTP-PUSH transfers store the consumer's S3 access key and secret in the
     * data address when the transfer is initiated.  If those credentials are present,
     * the transfer can be resumed without re-creating a temporary IAM user.  The
     * credentials may still have expired on the consumer side, in which case the
     * resumed upload will fail and the Control Plane should start a new transfer.</p>
     *
     * @param dataFlow the data flow to inspect
     * @return {@code true} if bucketName, accessKey, and secretKey are all present
     */
    @Override
    public boolean hasUsableAccessMaterial(DataFlow dataFlow) {
        Map<String, String> da = dataFlow.getDataAddress();
        return da != null
                && StringUtils.isNotBlank(da.get(S3Utils.BUCKET_NAME))
                && StringUtils.isNotBlank(da.get(S3Utils.ACCESS_KEY))
                && StringUtils.isNotBlank(da.get(S3Utils.SECRET_KEY));
    }

    /**
     * Terminates a data transfer.
     * Temporary IAM credentials created by the consumer CP for HTTP-PUSH transfers are cleaned up
     * by the consumer CP when it receives the termination event, not by this provider-side DP.
     *
     * @param dataFlowId the entity ID ({@code DataFlowEntity.getId()}) of the flow to terminate
     * @return future with success result
     */
    @Override
    public CompletableFuture<DataFlowResult> terminateTransfer(String dataFlowId) {
        log.info("Terminating HttpData-PUSH transfer for dataFlowId={}", dataFlowId);
        // dataFlowId is entity.getId(); resolve the processId to remove the correct flag entry
        dataFlowRepository.findById(dataFlowId).ifPresent(entity ->
                activeSuspendFlags.remove(entity.getProcessId()));
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }

    /**
     * Performs the asynchronous push: generates a presigned provider URL, streams the artifact
     * (optionally from a byte offset for resumed transfers), and uploads to the consumer bucket
     * using the resumable multipart upload API.
     *
     * <p>When the outer execution future is cancelled (as happens during
     * {@code DataFlowService.suspend()}), this method's inner upload future and its
     * {@code whenComplete} side-effect continue to run so that the checkpoint is persisted
     * before the upload thread exits.</p>
     *
     * @param dataFlow         the data flow with provider and consumer metadata
     * @param suspendFlag      flag monitored by the upload strategy; set to {@code true} to pause
     * @param existingUploadId existing multipart upload ID to resume, or {@code null} for fresh start
     * @param existingParts    completed {@link CompletedPart} objects from a previous session
     * @param existingSizes    byte sizes of each entry in {@code existingParts}, in the same order
     * @param confirmedBytes   total bytes already confirmed in the previous session
     * @return future with transfer result
     */
    private CompletableFuture<DataFlowResult> pushArtifactAsync(DataFlow dataFlow,
                                                                  AtomicBoolean suspendFlag,
                                                                  String existingUploadId,
                                                                  List<CompletedPart> existingParts,
                                                                  List<Long> existingSizes,
                                                                  long confirmedBytes) {
        String processId = dataFlow.getProcessId();
        String providerBucket = tenantBucketResolver.resolveBucketName(dataFlow.getTenantId());
        String presignedUrl = s3ClientService.generateGetPresignedUrl(providerBucket, dataFlow.getDatasetId(), Duration.ofDays(1L));
        Map<String, String> consumerS3Props = buildConsumerS3Properties(dataFlow.getDataAddress(), processId);
        String destinationBucket = consumerS3Props.get(S3Utils.BUCKET_NAME);
        String objectKey = consumerS3Props.get(S3Utils.OBJECT_KEY);

        AtomicReference<DataFlowCheckpoint> checkpointRef = new AtomicReference<>(null);
        UploadCheckpointCallback callback = buildCheckpointCallback(processId, dataFlow, destinationBucket, objectKey, checkpointRef);

        ResumableUploadRequest resumableRequest = new ResumableUploadRequest(
                existingUploadId, existingParts, existingSizes, confirmedBytes, suspendFlag, callback);

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(presignedUrl))
                        .GET()
                        .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS));
                if (confirmedBytes > 0) {
                    reqBuilder.header("Range", "bytes=" + confirmedBytes + "-");
                }

                log.debug("Sending GET request to provider artifact: {}", presignedUrl);
                HttpResponse<InputStream> response = httpClient.send(
                        reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

                int statusCode = response.statusCode();
                if (statusCode != 200 && statusCode != 206) {
                    closeQuietly(response.body());
                    throw new RuntimeException("Failed to get provider artifact. HTTP response code: " + statusCode);
                }

                log.info("HTTP response code: {}", statusCode);
                response.headers().firstValueAsLong("content-length").ifPresent(len ->
                        log.debug("Content-Length: {} bytes", len));

                String contentType = response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null);
                String contentDisposition = response.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION).orElse(null);

                CompletableFuture<String> uploadFuture = s3ClientService.uploadFile(
                        response.body(), consumerS3Props, contentType, contentDisposition, resumableRequest);

                // Persist checkpoint on pause or cleanup the flag on completion.
                // This whenComplete runs on the inner upload future, independently of the outer
                // future's state. Even when DataFlowService.suspend() cancels the outer future,
                // this handler still runs and saves the checkpoint to MongoDB.
                uploadFuture.whenComplete((etag, ex) -> {
                    closeQuietly(response.body());
                    activeSuspendFlags.remove(processId);
                    if (ex != null) {
                        Throwable cause = ex instanceof CompletionException ce ? ce.getCause() : ex;
                        if (cause instanceof UploadPausedException pause) {
                            savePauseCheckpoint(processId, dataFlow, pause, destinationBucket, objectKey);
                        }
                    }
                });

                return uploadFuture;
            } catch (IOException e) {
                activeSuspendFlags.remove(processId);
                log.error("Failed to download provider artifact from presigned URL: {}", presignedUrl, e);
                throw new RuntimeException(e.getMessage());
            } catch (InterruptedException e) {
                activeSuspendFlags.remove(processId);
                Thread.currentThread().interrupt();
                log.error("Download interrupted from presigned URL: {}", presignedUrl, e);
                throw new RuntimeException("Transfer interrupted: " + e.getMessage());
            }
        }, transferExecutor)
        .thenCompose(uploadFuture -> uploadFuture)
        .handle((etag, throwable) -> {
            if (throwable != null) {
                Throwable cause = throwable instanceof CompletionException ce ? ce.getCause() : throwable;
                if (cause instanceof UploadPausedException) {
                    // Upload paused cleanly. Checkpoint saved by whenComplete above.
                    // Return paused so that initiateTransfer/resumeTransfer thenApply
                    // handlers skip the CP callback entirely.
                    log.info("HTTP-PUSH upload paused for processId={}", processId);
                    return DataFlowResult.paused();
                }
                log.error("HTTP-PUSH transfer failed for processId={}", processId, throwable);
                return DataFlowResult.failure(cause.getMessage() != null ? cause.getMessage() : throwable.getMessage());
            }
            String consumerBucket = consumerS3Props.get(S3Utils.BUCKET_NAME);
            log.info("Successfully pushed to consumer S3 bucket {} with key {}", consumerBucket, objectKey);
            return DataFlowResult.success();
        });
    }

    /**
     * Builds an {@link UploadCheckpointCallback} that persists a {@link DataFlowCheckpoint}
     * to MongoDB when a new multipart upload is created and on each completed part.
     *
     * @param processId         the DSP transfer process ID
     * @param dataFlow          the data flow providing tenantId and transferType
     * @param destinationBucket consumer S3 bucket name
     * @param objectKey         consumer S3 object key
     * @param checkpointRef     reference holding the latest saved checkpoint instance
     * @return the constructed callback
     */
    private UploadCheckpointCallback buildCheckpointCallback(String processId,
                                                              DataFlow dataFlow,
                                                              String destinationBucket,
                                                              String objectKey,
                                                              AtomicReference<DataFlowCheckpoint> checkpointRef) {
        return new UploadCheckpointCallback() {
            @Override
            public void onMultipartCreated(String uploadId) {
                DataFlowCheckpoint initial = DataFlowCheckpoint.Builder.newInstance()
                        .processId(processId)
                        .dataFlowId(dataFlow.getDataFlowId())
                        .transferType(dataFlow.getTransferType())
                        .tenantId(dataFlow.getTenantId())
                        .uploadId(uploadId)
                        .destinationBucket(destinationBucket)
                        .destinationObjectKey(objectKey)
                        .confirmedBytes(0L)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                DataFlowCheckpoint saved = checkpointService.save(initial);
                checkpointRef.set(saved);
                log.debug("Checkpoint created for processId={}, uploadId={}", processId, uploadId);
            }

            @Override
            public void onPartCompleted(int partNumber, String eTag, long partSize, long contiguousConfirmedBytes) {
                DataFlowCheckpoint current = checkpointRef.get();
                if (current == null) {
                    log.warn("onPartCompleted called before onMultipartCreated for processId={}", processId);
                    return;
                }
                DataFlowCheckpoint updated = current.withCompletedPart(partNumber, partSize, eTag);
                DataFlowCheckpoint saved = checkpointService.save(updated);
                checkpointRef.set(saved);
                log.debug("Checkpoint updated for processId={}, part={}, confirmedBytes={}",
                        processId, partNumber, contiguousConfirmedBytes);
            }
        };
    }

    /**
     * Saves a checkpoint to MongoDB after the upload was paused via {@link UploadPausedException}.
     * Uses the exception's snapshot rather than the incremental callback reference, ensuring
     * that all parts recorded in the exception are persisted even if the callback ref is stale.
     *
     * @param processId         the DSP transfer process ID
     * @param dataFlow          the data flow providing metadata
     * @param pause             the exception carrying the paused upload state
     * @param destinationBucket consumer S3 bucket name
     * @param objectKey         consumer S3 object key
     */
    private void savePauseCheckpoint(String processId,
                                      DataFlow dataFlow,
                                      UploadPausedException pause,
                                      String destinationBucket,
                                      String objectKey) {
        try {
            Map<Integer, Long> partSizesMap = new HashMap<>();
            Map<Integer, String> partETagsMap = new HashMap<>();
            List<Integer> partNumbers = new ArrayList<>();
            List<CompletedPart> parts = pause.getCompletedParts();
            List<Long> sizes = pause.getPartSizes();
            for (int i = 0; i < parts.size(); i++) {
                int num = parts.get(i).partNumber();
                String etag = parts.get(i).eTag();
                long size = sizes.get(i);
                partNumbers.add(num);
                partSizesMap.put(num, size);
                partETagsMap.put(num, etag);
            }
            DataFlowCheckpoint paused = DataFlowCheckpoint.Builder.newInstance()
                    .processId(processId)
                    .dataFlowId(dataFlow.getDataFlowId())
                    .transferType(dataFlow.getTransferType())
                    .tenantId(dataFlow.getTenantId())
                    .uploadId(pause.getUploadId())
                    .destinationBucket(destinationBucket)
                    .destinationObjectKey(objectKey)
                    .completedParts(partNumbers)
                    .partSizes(partSizesMap)
                    .partETags(partETagsMap)
                    .confirmedBytes(pause.getConfirmedBytes())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            checkpointService.save(paused);
            log.info("Saved pause checkpoint for processId={}, uploadId={}, confirmedBytes={}",
                    processId, pause.getUploadId(), pause.getConfirmedBytes());
        } catch (Exception e) {
            log.error("Failed to save pause checkpoint for processId={}", processId, e);
        }
    }

    /**
     * Reconstructs the ordered list of {@link CompletedPart} objects from a stored checkpoint.
     * The list is sorted by part number and includes both the part number and its ETag,
     * which are required by the S3 API to complete the multipart upload.
     *
     * @param checkpoint the checkpoint to reconstruct parts from
     * @return ordered list of completed parts, or empty list if the checkpoint has no parts
     */
    private List<CompletedPart> buildCompletedParts(DataFlowCheckpoint checkpoint) {
        if (checkpoint.getCompletedParts() == null || checkpoint.getCompletedParts().isEmpty()) {
            return List.of();
        }
        Map<Integer, String> eTags = checkpoint.getPartETags() != null ? checkpoint.getPartETags() : Map.of();
        List<CompletedPart> parts = new ArrayList<>();
        for (Integer num : checkpoint.getCompletedParts()) {
            String eTag = eTags.getOrDefault(num, "");
            parts.add(CompletedPart.builder().partNumber(num).eTag(eTag).build());
        }
        parts.sort((a, b) -> Integer.compare(a.partNumber(), b.partNumber()));
        return List.copyOf(parts);
    }

    /**
     * Extracts the ordered list of part sizes from a stored checkpoint.
     * Each entry corresponds to the part at the same index in the completed-parts list.
     *
     * @param checkpoint the checkpoint to extract sizes from
     * @return ordered list of part sizes in bytes, or empty list if no parts are recorded
     */
    private List<Long> buildPartSizes(DataFlowCheckpoint checkpoint) {
        if (checkpoint.getCompletedParts() == null || checkpoint.getCompletedParts().isEmpty()) {
            return List.of();
        }
        Map<Integer, Long> sizeMap = checkpoint.getPartSizes() != null ? checkpoint.getPartSizes() : Map.of();
        List<Integer> sortedNums = new ArrayList<>(checkpoint.getCompletedParts());
        sortedNums.sort(Integer::compareTo);
        List<Long> sizes = new ArrayList<>();
        for (Integer num : sortedNums) {
            sizes.add(sizeMap.getOrDefault(num, 0L));
        }
        return List.copyOf(sizes);
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
     * Converts a {@link DataFlowEntity} to a {@link DataFlow} domain object.
     *
     * @param entity the entity to convert
     * @return the populated DataFlow
     */
    private DataFlow toDataFlow(DataFlowEntity entity) {
        return DataFlow.Builder.newInstance()
                .dataFlowId(entity.getId())
                .processId(entity.getProcessId())
                .agreementId(entity.getAgreementId())
                .datasetId(entity.getDatasetId())
                .transferType(entity.getTransferType())
                .callbackAddress(entity.getCallbackAddress())
                .state(entity.getState())
                .dataAddress(entity.getDataAddress())
                .tenantId(entity.getTenantId())
                .participantId(entity.getParticipantId())
                .counterPartyId(entity.getCounterPartyId())
                .build();
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


