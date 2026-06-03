package it.eng.dataplane.httppull;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareMetadata;
import it.eng.dataplane.api.message.DataFlowPrepareMetadataSection;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.s3.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
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

    /** Mode value for consumer viewData requests — returns a pre-signed URL for the stored file. */
    static final String MODE_VIEW = "VIEW";
    /** Data address key carrying the pre-signed URL in the prepare response. */
    static final String PRESIGNED_URL_KEY = DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY;

    @Override
    public String getProtocolId() {
        return "HttpData-PULL";
    }

    /**
     * Generates a pre-signed GET URL for the requested S3 object and returns it in
     * the response {@code dataAddress}.
     *
     * <ul>
     *   <li>If {@code metadata.sink.mode == VIEW}: the consumer Control Plane is requesting a URL
     *       so the API caller can download a previously stored file. The bucket is resolved from
     *       {@code metadata.sink.s3.bucketName} (CP-provided, supports multi-tenant isolation),
     *       falling back to local {@code s3Properties} for single-tenant deployments.
     *       The object key is {@code message.processId}
     *       (the transfer process ID used as key when storing). Returns {@code presignedUrl}.</li>
     *   <li>Otherwise (provider side): the Control Plane provides the bucket name and object key
     *       via {@code metadata.source.s3.bucketName} and {@code metadata.source.s3.objectKey}.
     *       Falls back to local {@code s3Properties.bucketName} / {@code message.datasetId} when
     *       the metadata fields are absent. Returns {@code endpoint} and {@code endpointType}
     *       so the CP's {@code prepareResponseToDataAddress()} helper can embed them in the
     *       {@code TransferStartMessage} sent to the consumer.</li>
     * </ul>
     *
     * @param message the prepare message from the Control Plane
     * @return response containing {@code dataAddress.presignedUrl} (VIEW) or
     *         {@code dataAddress.endpoint} (provider)
     */
    @Override
    public DataFlowPrepareResponse prepare(DataFlowPrepareMessage message) {
        DataFlowPrepareMetadata meta = DataFlowPrepareMetadata.from(message);
        String mode = meta.getSinkSection().getString(DataPlaneConstants.METADATA_FIELD_MODE);
        if (mode == null) {
            mode = "";
        }

        Map<String, String> dataAddress = new HashMap<>();

        if (MODE_VIEW.equals(mode)) {
            // Consumer viewData: the file was stored with processId as the object key.
            // Prefer the CP-provided bucket from sink.s3.bucketName so multi-tenant deployments
            // route to the correct per-tenant bucket; fall back to the DP-local s3Properties for
            // single-tenant / legacy configurations.
            DataFlowPrepareMetadataSection sinkS3Section = meta.getSinkSection()
                    .getSection(DataPlaneConstants.METADATA_SECTION_S3);
            String cpBucket = sinkS3Section.getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME);
            String bucketName = StringUtils.isNotBlank(cpBucket) ? cpBucket : s3Properties.getBucketName();
            String objectKey = message.getProcessId();
            log.info("Preparing viewData presigned URL for processId={} in bucket={}", objectKey, bucketName);
            String presignedUrl = s3ClientService.generateGetPresignedUrl(bucketName, objectKey, Duration.ofDays(7L));
            log.debug("Generated viewData presigned URL for objectKey='{}'", objectKey);
            dataAddress.put(PRESIGNED_URL_KEY, presignedUrl);
        } else {
            // Provider side: CP provides the bucket and object key via source.s3 metadata
            DataFlowPrepareMetadataSection s3Section = meta.getSourceSection()
                    .getSection(DataPlaneConstants.METADATA_SECTION_S3);
            String metaBucket = s3Section.getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME);
            String metaObjectKey = s3Section.getString(DataPlaneConstants.METADATA_S3_OBJECT_KEY);
            String bucketName = StringUtils.isNotBlank(metaBucket) ? metaBucket : s3Properties.getBucketName();
            String objectKey = StringUtils.isNotBlank(metaObjectKey) ? metaObjectKey : message.getDatasetId();
            log.info("Preparing provider presigned URL for objectKey={} in bucket={}", objectKey, bucketName);
            Map<String, String> sourceS3Properties = buildSourceS3Properties(s3Section, bucketName, objectKey);
            String presignedUrl = hasInlineSourceCredentials(sourceS3Properties)
                    ? s3ClientService.generateGetPresignedUrl(sourceS3Properties, Duration.ofDays(7L))
                    : s3ClientService.generateGetPresignedUrl(bucketName, objectKey, Duration.ofDays(7L));
            log.debug("Generated provider presigned URL for objectKey='{}'", objectKey);
            dataAddress.put(DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT, presignedUrl);
            dataAddress.put(DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT_TYPE, "https://w3id.org/idsa/v4.1/HTTP");
        }

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
        if (!dataAddress.containsKey(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME)) {
            return CompletableFuture.completedFuture(
                DataFlowResult.failure("dataAddress." + DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME
                        + " is required for HttpData-PULL")
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
     * Downloads data from presigned URL and uploads to S3 using sink properties from the data address.
     * Uses {@link java.net.http.HttpClient} which negotiates HTTP/2 on TLS connections
     * and falls back to HTTP/1.1 for plain HTTP (e.g. development MinIO without TLS).
     *
     * @param dataFlow the data flow containing transfer metadata
     * @param presignedUrl the presigned GET URL to download from
     * @return future with transfer result
     */
    private CompletableFuture<DataFlowResult> downloadAndUploadToS3(DataFlow dataFlow, String presignedUrl) {
        // Read sink properties from CP-provided data address — no local bucket resolution needed
        Map<String, String> dataAddress = dataFlow.getDataAddress();
        Map<String, String> destinationS3Properties = buildSinkS3Properties(dataAddress, dataFlow.getProcessId());

        // Extract auth from data address before entering the async lambda
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
            String bucketName = destinationS3Properties.get(it.eng.tools.s3.util.S3Utils.BUCKET_NAME);
            String objectKey = destinationS3Properties.get(it.eng.tools.s3.util.S3Utils.OBJECT_KEY);
            log.info("Successfully uploaded to S3 bucket {} with key {}", bucketName, objectKey);
            return DataFlowResult.success();
        })
        .exceptionally(throwable -> {
            log.error("HTTP-PULL transfer failed for data flow {}", dataFlow.getDataFlowId(), throwable);
            return DataFlowResult.failure(throwable.getMessage());
        });
    }

    /**
     * Builds S3 properties map for upload from CP-provided {@code sink.*} entries in the data address.
     * Falls back to {@code processId} when {@code sink.objectKey} is absent.
     *
     * @param dataAddress the flat data address map from the DataFlow
     * @param processId   transfer process ID used as object key fallback
     * @return S3 properties map ready for {@link S3ClientService#uploadFile}
     */
    private Map<String, String> buildSinkS3Properties(Map<String, String> dataAddress, String processId) {
        Map<String, String> props = new HashMap<>();
        props.put(S3Utils.BUCKET_NAME,
                dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME));
        String objectKey = dataAddress.getOrDefault(
                DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, processId);
        props.put(S3Utils.OBJECT_KEY, objectKey);
        props.put(S3Utils.ACCESS_KEY,
                dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY));
        props.put(S3Utils.SECRET_KEY,
                dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY));
        String region = dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION);
        if (StringUtils.isNotBlank(region)) {
            props.put(S3Utils.REGION, region);
        }
        String endpointOverride = dataAddress.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE);
        if (StringUtils.isNotBlank(endpointOverride)) {
            props.put(S3Utils.ENDPOINT_OVERRIDE, endpointOverride);
        }
        return props;
    }

    private Map<String, String> buildSourceS3Properties(DataFlowPrepareMetadataSection s3Section,
                                                         String bucketName,
                                                         String objectKey) {
        Map<String, String> sourceS3Properties = new HashMap<>();
        sourceS3Properties.put(S3Utils.BUCKET_NAME, bucketName);
        sourceS3Properties.put(S3Utils.OBJECT_KEY, objectKey);
        putIfNotBlank(sourceS3Properties, S3Utils.REGION,
                s3Section.getString(DataPlaneConstants.METADATA_S3_REGION));
        putIfNotBlank(sourceS3Properties, S3Utils.ACCESS_KEY,
                s3Section.getString(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        putIfNotBlank(sourceS3Properties, S3Utils.SECRET_KEY,
                s3Section.getString(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        putIfNotBlank(sourceS3Properties, S3Utils.ENDPOINT_OVERRIDE,
                s3Section.getString(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        return sourceS3Properties;
    }

    private boolean hasInlineSourceCredentials(Map<String, String> sourceS3Properties) {
        return StringUtils.isNotBlank(sourceS3Properties.get(S3Utils.ACCESS_KEY))
                && StringUtils.isNotBlank(sourceS3Properties.get(S3Utils.SECRET_KEY))
                && StringUtils.isNotBlank(sourceS3Properties.get(S3Utils.REGION));
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
