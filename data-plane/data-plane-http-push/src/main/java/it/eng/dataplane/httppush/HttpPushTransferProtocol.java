package it.eng.dataplane.httppush;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareMetadata;
import it.eng.dataplane.api.message.DataFlowPrepareMetadataSection;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.s3.io.S3SourceReader;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.s3.util.S3Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * HTTP-PUSH transfer protocol implementation.
 * Acting as the provider side: opens the source artifact using CP-provided {@code source.*}
 * credentials via {@link S3SourceReader}, then pushes the data to the consumer's S3 bucket
 * using CP-provided {@code sink.*} credentials from the DataFlow's dataAddress.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HttpPushTransferProtocol implements DataTransferProtocol {

    private final S3ClientService s3ClientService;
    private final TemporaryBucketUserService temporaryBucketUserService;
    private final S3SourceReader s3SourceReader;
    @Qualifier("transferExecutor")
    private final Executor transferExecutor;
    private final ControlPlaneClient controlPlaneClient;

    /**
     * Returns the unique identifier for this transfer protocol.
     *
     * @return protocol identifier string "HttpData-PUSH"
     */
    @Override
    public String getProtocolId() {
        return "HttpData-PUSH";
    }

    /** Mode value for consumer viewData requests — returns a pre-signed URL for the pushed file. */
    private static final String MODE_VIEW = "VIEW";

    /**
     * Prepares the consumer-side bucket for an HTTP-PUSH transfer by creating a temporary
     * IAM user with write-only access to the consumer's bucket, using CP-provided management
     * credentials from {@code metadata.sink.s3}. Generates a presigned GET URL when called
     * with {@code metadata.sink.mode = VIEW} (consumer viewData request).
     *
     * <p>Normal flow: the consumer Control Plane calls this before sending the DSP
     * {@code TransferRequestMessage} to the provider, so the temporary credentials can be
     * embedded in the message's {@code dataAddress}. The provider then uses these credentials
     * to push the artifact directly into the consumer's bucket.</p>
     *
     * <p>VIEW mode: the consumer Control Plane calls this after a completed transfer to obtain
     * a presigned GET URL for the file that was pushed to the consumer's bucket. All S3
     * credentials must be present in {@code metadata.sink.s3}.</p>
     *
     * <p>All S3 coordinates (bucket, region, endpoint, credentials) are read exclusively from
     * {@code metadata.sink.s3}. No DP-local {@code s3.endpoint} or {@code s3.bucketName}
     * fallback is performed.</p>
     *
     * @param message the prepare message; {@code processId} is used as the S3 object key
     * @return response with {@code dataAddress} containing temporary upload credentials or presigned URL
     */
    @Override
    public DataFlowPrepareResponse prepare(DataFlowPrepareMessage message) {
        String processId = message.getProcessId();
        DataFlowPrepareMetadata metadata = DataFlowPrepareMetadata.from(message);

        String mode = metadata.getSinkSection()
                .getString(DataPlaneConstants.METADATA_FIELD_MODE);
        if (MODE_VIEW.equals(mode)) {
            log.info("Preparing viewData presigned URL for processId={}", processId);
            DataFlowPrepareMetadataSection sinkS3Section = metadata.getSinkSection()
                    .getSection(DataPlaneConstants.METADATA_SECTION_S3);
            String presignedUrl = s3ClientService.generateGetPresignedUrl(sinkS3Section.toScalarMap(), Duration.ofDays(7L));
            log.debug("Generated presigned URL for pushed file, objectKey='{}'", processId);
            return DataFlowPrepareResponse.Builder.newInstance()
                    .processId(processId)
                    .dataAddress(Map.of(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY, presignedUrl))
                    .build();
        }

        DataFlowPrepareMetadataSection sinkS3Section = metadata.getSinkSection()
                .getSection(DataPlaneConstants.METADATA_SECTION_S3);
        String bucketName = sinkS3Section.getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME);
        String endpointOverride = sinkS3Section.getString(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE);
        BucketCredentialsEntity managementCredentials = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(bucketName)
                .accessKey(sinkS3Section.getString(DataPlaneConstants.METADATA_S3_ACCESS_KEY))
                .secretKey(sinkS3Section.getString(DataPlaneConstants.METADATA_S3_SECRET_KEY))
                .endpointOverride(endpointOverride)
                .build();

        log.info("Preparing HTTP-PUSH temp user for processId={} in bucket={}", processId, bucketName);
        var tempUser = temporaryBucketUserService.createTemporaryUser(
                processId,
                managementCredentials,
                bucketName,
                processId);
        log.info("Created temporary IAM user '{}' for processId={}", tempUser.getAccessKey(), processId);

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(S3Utils.BUCKET_NAME, bucketName);
        dataAddress.put(S3Utils.OBJECT_KEY, processId);
        dataAddress.put(S3Utils.ACCESS_KEY, tempUser.getAccessKey());
        dataAddress.put(S3Utils.SECRET_KEY, tempUser.getSecretKey());
        String region = sinkS3Section.getString(DataPlaneConstants.METADATA_S3_REGION);
        if (StringUtils.isNotBlank(region)) {
            dataAddress.put(S3Utils.REGION, region);
        }
        // endpointOverride from CP metadata — internal/container-reachable endpoint for server-side uploads.
        // The external presigned URL endpoint is a CP concern and must not be embedded here.
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
     * Opens the provider artifact using CP-provided {@code metadata.source.s3} credentials via
     * {@link S3SourceReader} and pushes it to the consumer's S3 bucket using the
     * CP-provided {@code metadata.sink.s3} credentials.
     * Notifies the Control Plane via explicit {@code sendStarted}, {@code sendCompleted},
     * or {@code sendErrored} callbacks so the CP can drive DSP state transitions directly.
     *
     * @param dataFlow the data flow to initiate; its metadata must contain both source and sink S3 sections
     * @return future with the result of the transfer
     */
    @Override
    public CompletableFuture<DataFlowResult> initiateTransfer(DataFlow dataFlow) {
        log.info("Initiating HTTP-PUSH transfer for data flow {}", dataFlow.getDataFlowId());

        DataFlowPrepareMetadata metadata = DataFlowPrepareMetadata.fromMap(dataFlow.getMetadata());
        DataFlowPrepareMetadataSection sinkS3 = metadata.getSinkSection().getSection(DataPlaneConstants.METADATA_SECTION_S3);
        if (StringUtils.isBlank(sinkS3.getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME))) {
            return CompletableFuture.completedFuture(
                DataFlowResult.failure("metadata.sink.s3.bucketName (consumer bucketName) is required for HttpData-PUSH")
            );
        }
        DataFlowPrepareMetadataSection sourceS3 = metadata.getSourceSection().getSection(DataPlaneConstants.METADATA_SECTION_S3);
        if (StringUtils.isBlank(sourceS3.getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME))) {
            return CompletableFuture.completedFuture(
                DataFlowResult.failure("metadata.source.s3.bucketName (provider bucketName) is required for HttpData-PUSH")
            );
        }

        Map<String, String> dataAddress = dataFlow.getDataAddress() != null ? dataFlow.getDataAddress() : Map.of();
        // Notify CP that the DP has started processing — provider CP can now update DSP state
        controlPlaneClient.sendStarted(dataFlow.getCallbackAddress(), dataFlow.getProcessId(), dataAddress);

        // Run the transfer asynchronously, then notify CP of outcome
        return pushArtifactToConsumer(dataFlow)
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
     * Completes the HTTP-PUSH transfer by deleting the temporary IAM credentials that were
     * created during {@link #prepare}. This cleanup runs on the success path, before the
     * dataplane runtime persists {@code COMPLETED}.
     *
     * <p>Cleanup is best-effort: if the temporary user has already been deleted or was never
     * created, the failure is logged as a warning and the method still returns success.</p>
     *
     * @param processId the DPS transfer process ID
     * @return future with success result
     */
    @Override
    public CompletableFuture<DataFlowResult> completeTransfer(String processId) {
        log.info("Completing HttpData-PUSH transfer for processId={}, cleaning up temporary IAM user", processId);
        try {
            temporaryBucketUserService.deleteTemporaryUser(processId);
            log.info("Deleted temporary IAM user for processId={}", processId);
        } catch (Exception e) {
            log.warn("Best-effort cleanup on complete: failed to delete temporary IAM user for processId={}: {}",
                    processId, e.getMessage());
        }
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }

    /**
     * Terminates a data transfer and cleans up the temporary IAM credentials created by
     * {@link #prepare} for the consumer's upload bucket.
     *
     * <p>This handles the error/cancel path. On the success path, cleanup is performed by
     * {@link #completeTransfer} before the dataplane marks the flow {@code COMPLETED}.</p>
     *
     * <p>Cleanup is best-effort: if the temporary user has already been deleted or was never
     * created (e.g., prepare failed before credential creation), the failure is logged as a
     * warning and the method still returns success so that the Control Plane can continue
     * its own lifecycle transitions.</p>
     *
     * @param processId the DPS transfer process ID
     * @return future with success result
     */
    @Override
    public CompletableFuture<DataFlowResult> terminateTransfer(String processId) {
        log.info("Terminating HttpData-PUSH transfer for processId={}", processId);
        try {
            temporaryBucketUserService.deleteTemporaryUser(processId);
            log.info("Deleted temporary IAM user for processId={}", processId);
        } catch (Exception e) {
            log.warn("Best-effort cleanup: failed to delete temporary IAM user for processId={}: {}", processId, e.getMessage());
        }
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }

    /**
     * Performs the actual push: opens the provider source artifact using CP-provided
     * {@code metadata.source.s3} credentials via {@link S3SourceReader}, then streams the artifact
     * to the consumer bucket using CP-provided {@code metadata.sink.s3} properties.
     *
     * @param dataFlow the data flow with provider and consumer metadata
     * @return future with transfer result
     */
    private CompletableFuture<DataFlowResult> pushArtifactToConsumer(DataFlow dataFlow) {
        // Open provider source using CP-provided metadata.source.s3 credentials directly
        SourceContext sourceContext = buildSourceContext(dataFlow);
        SourceOpenResult sourceResult;
        try {
            sourceResult = s3SourceReader.open(sourceContext);
        } catch (RuntimeException e) {
            log.error("Failed to open provider source for data flow {} due to synchronous open failure: {}",
                    dataFlow.getDataFlowId(), e.getMessage());
            return CompletableFuture.completedFuture(
                    DataFlowResult.failure("Failed to open provider artifact: " + e.getMessage()));
        }
        if (!sourceResult.isSuccess()) {
            log.error("Failed to open provider source for data flow {}: {}",
                    dataFlow.getDataFlowId(), sourceResult.getErrorMessage());
            return CompletableFuture.completedFuture(
                    DataFlowResult.failure("Failed to open provider artifact: " + sourceResult.getErrorMessage()));
        }

        // Build consumer S3 properties from CP-provided metadata.sink.s3 section
        Map<String, String> consumerS3Properties = buildConsumerS3Properties(dataFlow);
        InputStream artifactStream = sourceResult.getStream();

        return CompletableFuture.supplyAsync(() ->
            {
                try {
                    return s3ClientService.uploadFile(
                            artifactStream,
                            consumerS3Properties,
                            sourceResult.getContentType(),
                            null
                    ).whenComplete((etag, ex) -> closeQuietly(artifactStream));
                } catch (RuntimeException exception) {
                    closeQuietly(artifactStream);
                    throw exception;
                }
            },
        transferExecutor)
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
     * Builds a {@link SourceContext} from CP-provided {@code metadata.source.s3} section.
     * Falls back to {@code datasetId} when {@code source.s3.objectKey} is absent.
     *
     * @param dataFlow the data flow containing transfer metadata
     * @return source context ready for {@link S3SourceReader#open(SourceContext)}
     */
    private SourceContext buildSourceContext(DataFlow dataFlow) {
        DataFlowPrepareMetadataSection s3Section = DataFlowPrepareMetadata.fromMap(dataFlow.getMetadata())
                .getSourceSection()
                .getSection(DataPlaneConstants.METADATA_SECTION_S3);
        Map<String, String> props = new HashMap<>();
        props.put(S3Utils.BUCKET_NAME, s3Section.getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        String objectKey = s3Section.getString(DataPlaneConstants.METADATA_S3_OBJECT_KEY);
        props.put(S3Utils.OBJECT_KEY, StringUtils.isNotBlank(objectKey) ? objectKey : dataFlow.getDatasetId());
        putIfNotBlank(props, S3Utils.ACCESS_KEY, s3Section.getString(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        putIfNotBlank(props, S3Utils.SECRET_KEY, s3Section.getString(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        putIfNotBlank(props, S3Utils.REGION, s3Section.getString(DataPlaneConstants.METADATA_S3_REGION));
        putIfNotBlank(props, S3Utils.ENDPOINT_OVERRIDE, s3Section.getString(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        return SourceContext.Builder.newInstance().properties(props).build();
    }

    /**
     * Builds the consumer S3 properties map from CP-provided {@code metadata.sink.s3} section.
     * Falls back to {@code processId} when {@code sink.s3.objectKey} is absent.
     * The secretKey arrives as plain text from the consumer CP via the DataFlowStartMessage.
     *
     * @param dataFlow the data flow containing transfer metadata
     * @return map of S3 properties ready for use by S3ClientService
     */
    private Map<String, String> buildConsumerS3Properties(DataFlow dataFlow) {
        DataFlowPrepareMetadataSection s3Section = DataFlowPrepareMetadata.fromMap(dataFlow.getMetadata())
                .getSinkSection()
                .getSection(DataPlaneConstants.METADATA_SECTION_S3);
        Map<String, String> props = new HashMap<>();
        props.put(S3Utils.BUCKET_NAME, s3Section.getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        String objectKey = s3Section.getString(DataPlaneConstants.METADATA_S3_OBJECT_KEY);
        props.put(S3Utils.OBJECT_KEY, StringUtils.isNotBlank(objectKey) ? objectKey : dataFlow.getProcessId());
        putIfNotBlank(props, S3Utils.ACCESS_KEY, s3Section.getString(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        // The secretKey arrives as plain text — set directly without decryption.
        putIfNotBlank(props, S3Utils.SECRET_KEY, s3Section.getString(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        putIfNotBlank(props, S3Utils.ENDPOINT_OVERRIDE, s3Section.getString(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        putIfNotBlank(props, S3Utils.REGION, s3Section.getString(DataPlaneConstants.METADATA_S3_REGION));
        return props;
    }

    private void putIfNotBlank(Map<String, String> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
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
