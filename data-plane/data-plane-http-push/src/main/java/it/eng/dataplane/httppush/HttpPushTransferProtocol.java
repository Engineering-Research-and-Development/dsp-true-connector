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
import it.eng.tools.s3.properties.S3Properties;
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
    private final S3Properties s3Properties;
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
        DataFlowPrepareMetadata metadata = DataFlowPrepareMetadata.from(message);
        String bucketName = resolvePrepareBucketName(metadata);

        String mode = metadata.getSinkSection()
                .getString(DataPlaneConstants.METADATA_FIELD_MODE);
        if (MODE_VIEW.equals(mode)) {


            log.info("Preparing viewData presigned URL for processId={} in bucket={}", processId, bucketName);
            DataFlowPrepareMetadata meta = DataFlowPrepareMetadata.from(message);
            DataFlowPrepareMetadataSection sinkS3Section = meta.getSinkSection()
                    .getSection(DataPlaneConstants.METADATA_SECTION_S3);
            String presignedUrl = s3ClientService.generateGetPresignedUrl(sinkS3Section.toScalarMap(), Duration.ofDays(7L));
//            String presignedUrl = s3ClientService.generateGetPresignedUrl(bucketName, processId, Duration.ofDays(7L));
            log.debug("Generated presigned URL for pushed file, objectKey='{}'", processId);
            return DataFlowPrepareResponse.Builder.newInstance()
                    .processId(processId)
                    .dataAddress(Map.of(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY, presignedUrl))
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
        // Server-to-server uploads must reach the internal/container-reachable endpoint.
        // externalPresignedEndpoint is only for presigned URLs embedded in DSP messages
        // consumed by external clients — never use it for sink upload credentials.
        String endpointOverride = s3Properties.getEndpoint();
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
     * Opens the provider artifact using CP-provided {@code source.*} credentials via
     * {@link S3SourceReader} and pushes it to the consumer's S3 bucket using the
     * CP-provided {@code sink.*} credentials from the data address.
     * Notifies the Control Plane via explicit {@code sendStarted}, {@code sendCompleted},
     * or {@code sendErrored} callbacks so the CP can drive DSP state transitions directly.
     *
     * @param dataFlow the data flow to initiate; its dataAddress must contain both source and consumer S3 credentials
     * @return future with the result of the transfer
     */
    @Override
    public CompletableFuture<DataFlowResult> initiateTransfer(DataFlow dataFlow) {
        log.info("Initiating HTTP-PUSH transfer for data flow {}", dataFlow.getDataFlowId());

        Map<String, String> dataAddress = dataFlow.getDataAddress();
        if (dataAddress == null || !dataAddress.containsKey(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME)) {
            return CompletableFuture.completedFuture(
                DataFlowResult.failure("dataAddress." + DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME
                        + " (consumer bucketName) is required for HttpData-PUSH")
            );
        }
        if (!dataAddress.containsKey(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME)) {
            return CompletableFuture.completedFuture(
                DataFlowResult.failure("dataAddress." + DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME
                        + " (provider bucketName) is required for HttpData-PUSH")
            );
        }

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
     * Terminates a data transfer and cleans up the temporary IAM credentials created by
     * {@link #prepare} for the consumer's upload bucket.
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

    private String resolvePrepareBucketName(DataFlowPrepareMetadata metadata) {
        String bucketName = metadata.getSinkSection()
                .getSection(DataPlaneConstants.METADATA_SECTION_S3)
                .getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME);
        if (StringUtils.isBlank(bucketName)) {
            return s3Properties.getBucketName();
        }
        return bucketName;
    }

    /**
     * Performs the actual push: opens the provider source artifact using CP-provided
     * {@code source.*} credentials via {@link S3SourceReader}, then streams the artifact
     * to the consumer bucket using CP-provided {@code sink.*} properties.
     *
     * <p>Using {@link S3SourceReader} ensures the DP uses only the credentials supplied by the
     * CP in the {@link DataFlow#getDataAddress()} map — no DP-local MongoDB bucket credentials
     * are consulted for the source read.</p>
     *
     * @param dataFlow the data flow with provider and consumer metadata
     * @return future with transfer result
     */
    private CompletableFuture<DataFlowResult> pushArtifactToConsumer(DataFlow dataFlow) {
        Map<String, String> dataAddress = dataFlow.getDataAddress();

        // Open provider source using CP-provided source.* credentials directly
        SourceContext sourceContext = buildSourceContext(dataAddress, dataFlow.getDatasetId());
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

        // Build consumer S3 properties from CP-provided sink.* properties
        Map<String, String> consumerS3Properties = buildConsumerS3Properties(dataAddress, dataFlow.getProcessId());
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
     * Builds a {@link SourceContext} from CP-provided {@code source.*} entries in the data address.
     * Falls back to {@code datasetId} when {@code source.objectKey} is absent.
     *
     * @param dataAddress the flat data address map from the DataFlow
     * @param datasetId   dataset ID used as object key fallback
     * @return source context ready for {@link S3SourceReader#open(SourceContext)}
     */
    private SourceContext buildSourceContext(Map<String, String> dataAddress, String datasetId) {
        Map<String, String> props = new HashMap<>();
        props.put(S3Utils.BUCKET_NAME, dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME));
        String objectKey = dataAddress.getOrDefault(
                DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, datasetId);
        props.put(S3Utils.OBJECT_KEY, objectKey);
        props.put(S3Utils.ACCESS_KEY, dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ACCESS_KEY));
        props.put(S3Utils.SECRET_KEY, dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_SECRET_KEY));
        String region = dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_REGION);
        if (StringUtils.isNotBlank(region)) {
            props.put(S3Utils.REGION, region);
        }
        String endpointOverride = dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ENDPOINT_OVERRIDE);
        if (StringUtils.isNotBlank(endpointOverride)) {
            props.put(S3Utils.ENDPOINT_OVERRIDE, endpointOverride);
        }
        return SourceContext.Builder.newInstance().properties(props).build();
    }

    /**
     * Builds the consumer S3 properties map from CP-provided {@code sink.*} entries in the data address.
     * Falls back to {@code processId} when {@code sink.objectKey} is absent.
     * The secretKey arrives as plain text from the consumer CP via the DataFlowStartMessage.
     *
     * @param dataAddress the flat data address map from the DataFlow
     * @param processId   the transfer process ID used as object key fallback
     * @return map of S3 properties ready for use by S3ClientService
     */
    private Map<String, String> buildConsumerS3Properties(Map<String, String> dataAddress, String processId) {
        Map<String, String> props = new HashMap<>();
        props.put(S3Utils.BUCKET_NAME, dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME));

        String objectKey = dataAddress.getOrDefault(
                DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, processId);
        props.put(S3Utils.OBJECT_KEY, objectKey);

        props.put(S3Utils.ACCESS_KEY, dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY));

        // The secretKey arrives as plain text — set directly without decryption.
        // The consumer CP creates the temp user, places the plain secretKey in the DataFlowStartMessage
        // dataAddress under sink.secretKey, and the provider CP forwards it here without encrypting.
        props.put(S3Utils.SECRET_KEY, dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY));

        String endpointOverride = dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE);
        if (StringUtils.isNotBlank(endpointOverride)) {
            props.put(S3Utils.ENDPOINT_OVERRIDE, endpointOverride);
        }

        String region = dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION);
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
