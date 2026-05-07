package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.PresignedUrlExpiredException;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferArtifactState;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.service.CancellationRegistry;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.exceptions.TransferCancelledException;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import it.eng.tools.s3.util.S3Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class HttpPullTransferStrategy implements DataTransferStrategy {

    private final S3ClientService s3ClientService;
    private final S3Properties s3Properties;
    private final Executor transferExecutor;
    private final TransferArtifactStateRepository transferArtifactStateRepository;
    private final CancellationRegistry cancellationRegistry;
    private static final int DEFAULT_CONNECT_TIMEOUT = 10_000; // 10 seconds
    /**
     * Fallback read timeout (30 minutes) used when the server does not advertise
     * Content-Length. For known sizes the timeout is computed dynamically.
     */
    private static final int FALLBACK_READ_TIMEOUT = 1_800_000; // 30 minutes
    /** Assumed minimum transfer speed in bytes/sec used for dynamic timeout (1 MB/s). */
    private static final long MIN_TRANSFER_SPEED_BYTES_PER_SEC = 1024L * 1024L;
    /** HTTP 206 Partial Content — returned when a Range header was honoured. */
    private static final int HTTP_PARTIAL_CONTENT = 206;

    /**
     * Creates an instance using the Spring-managed {@code httpPullTransferExecutor} bean.
     *
     * @param s3ClientService service for uploading data to S3
     * @param s3Properties S3 configuration properties
     * @param transferExecutor Spring-managed executor for running async transfer tasks
     * @param transferArtifactStateRepository repository for managing transfer state
     * @param cancellationRegistry registry for transfer cancellation tokens
     */
    @Autowired
    public HttpPullTransferStrategy(S3ClientService s3ClientService,
                                    S3Properties s3Properties,
                                    @Qualifier("httpPullTransferExecutor") Executor transferExecutor,
                                    TransferArtifactStateRepository transferArtifactStateRepository,
                                    CancellationRegistry cancellationRegistry) {
        this.s3ClientService = s3ClientService;
        this.s3Properties = s3Properties;
        this.transferExecutor = transferExecutor;
        this.transferArtifactStateRepository = transferArtifactStateRepository;
        this.cancellationRegistry = cancellationRegistry;
    }

    @Override
    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PULL transfer for process {}", transferProcess.getId());

        String authorization = extractAuthorization(transferProcess);

        // Load existing checkpoint (0 bytes = first-time download)
        TransferArtifactState checkpoint = transferArtifactStateRepository
                .findById(transferProcess.getId())
                .orElseGet(() -> TransferArtifactState.Builder.newInstance()
                        .id(transferProcess.getId()).downloadedBytes(0).build());

        long rangeStart = checkpoint.getDownloadedBytes();
        if (rangeStart > 0) {
            log.info("Resuming HTTP PULL for process {} from byte offset {}", transferProcess.getId(), rangeStart);
            // Reset multipart tracking for the fresh upload that starts from scratch
            checkpoint.setUploadId(null);
        }
        try {
            transferArtifactStateRepository.save(checkpoint);
        } catch (DuplicateKeyException e) {
            // suspendDataTransfer() concurrently inserted the state document.
            // Re-read to get the version assigned by the concurrent insert.
            log.debug("Concurrent TransferArtifactState insert for {}; retrying after re-read", transferProcess.getId());
            checkpoint = transferArtifactStateRepository.findById(transferProcess.getId())
                    .orElseThrow(() -> new DataTransferAPIException(
                            "TransferArtifactState not found after concurrent insert for: " + transferProcess.getId()));
            rangeStart = checkpoint.getDownloadedBytes();
        }

        // Register cancellation token before starting — deregistered in DataTransferAPIService.downloadData().whenComplete()
        AtomicBoolean cancellationToken = cancellationRegistry.register(transferProcess.getId());

        String transferProcessId = transferProcess.getId();
        CheckpointCallbackImpl checkpointCallback = new CheckpointCallbackImpl(
                checkpoint, rangeStart, transferArtifactStateRepository);

        return downloadAndUploadToS3(
                transferProcess.getDataAddress().getEndpoint(),
                authorization,
                transferProcessId,
                rangeStart,
                cancellationToken,
                checkpointCallback
        ).thenAccept(key -> {
            checkpointCallback.flush();
            log.info("Stored transfer process id - {} data!", key);
        });
    }

    private CompletableFuture<String> downloadAndUploadToS3(String presignedUrl,
                                                            String authorization,
                                                            String key,
                                                            long rangeStart,
                                                            AtomicBoolean cancellationToken,
                                                            UploadCheckpointCallback checkpointCallback) {
        // AtomicReference allows the connection to be shared across two separate lambda stages
        // (supplyAsync and whenComplete) without violating Java's effectively-final capture rule.
        // The supplyAsync lambda opens the connection and stores it here; whenComplete reads it
        // to guarantee disconnect() is called on all paths — success, failure, or cancellation.
        AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();

        return CompletableFuture.supplyAsync(() -> {
            if (cancellationToken.get()) {
                throw new TransferCancelledException("Transfer " + key + " was cancelled before download started");
            }
            try {
                URL url = new URL(presignedUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                // Store immediately so whenComplete can close it even if a later step throws
                connectionRef.set(connection);

                // Configure connection
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
                connection.setReadTimeout(FALLBACK_READ_TIMEOUT);
                if (StringUtils.isNotBlank(authorization)) {
                    connection.setRequestProperty(HttpHeaders.AUTHORIZATION, authorization);
                }

                if (rangeStart > 0) {
                    connection.setRequestProperty(HttpHeaders.RANGE, "bytes=" + rangeStart + "-");
                    log.info("Added Range header bytes={}- for key: {}", rangeStart, key);
                }

                if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                    log.debug("Using HTTPS connection to: {}", presignedUrl);
                } else {
                    log.debug("Using HTTP connection to: {}", presignedUrl);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    connection.disconnect();
                    connectionRef.set(null);
                    throw new PresignedUrlExpiredException(key);
                }
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HTTP_PARTIAL_CONTENT) {
                    // Disconnect eagerly on error response and clear the ref so whenComplete skips it
                    connection.disconnect();
                    connectionRef.set(null);
                    throw new DataTransferAPIException("Failed to get stream. HTTP response code: " + responseCode);
                }

                // Check for cancellation before committing to the upload — the suspension signal
                // may have arrived while getResponseCode() was blocked waiting for response headers.
                if (cancellationToken.get()) {
                    connection.disconnect();
                    connectionRef.set(null);
                    throw new TransferCancelledException("Transfer " + key + " was cancelled after response headers");
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
                Map<String, String> destinationS3Properties = Map.of(
                        S3Utils.OBJECT_KEY, key,
                        S3Utils.BUCKET_NAME, s3Properties.getBucketName(),
                        S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint(),
                        S3Utils.REGION, s3Properties.getRegion(),
                        S3Utils.ACCESS_KEY, s3Properties.getAccessKey(),
                        S3Utils.SECRET_KEY, s3Properties.getSecretKey()
                );
                // uploadFile is non-blocking and returns a CompletableFuture<String>.
                // Returning it here produces a CompletableFuture<CompletableFuture<String>>,
                // which thenCompose below flattens into a single CompletableFuture<String>.
                // The connection must remain open until the upload future completes.
                return s3ClientService.uploadFile(connection.getInputStream(), destinationS3Properties,
                        contentType, contentDisposition, cancellationToken, checkpointCallback);
            } catch (PresignedUrlExpiredException | TransferCancelledException e) {
                HttpURLConnection c = connectionRef.get();
                if (c != null) c.disconnect();
                throw e;
            } catch (IOException e) {
                // Disconnect on IOException before the upload started (connection may or may not be open)
                HttpURLConnection c = connectionRef.get();
                if (c != null) c.disconnect();
                log.error("Failed to download stream from URL: {}", presignedUrl, e);
                throw new DataTransferAPIException(e.getMessage());
            }
        }, transferExecutor)
        // Flatten the nested future and attach a cleanup handler that runs on all completion paths
        .thenCompose(uploadFuture ->
            uploadFuture.whenComplete((result, throwable) -> {
                // Disconnect after the upload completes (success or failure) to release the socket
                HttpURLConnection c = connectionRef.get();
                if (c != null) c.disconnect();
            })
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

    private String extractAuthorization(TransferProcess transferProcess) {
        if (transferProcess.getDataAddress().getEndpointProperties() != null) {
            List<EndpointProperty> properties = transferProcess.getDataAddress().getEndpointProperties();
            String authType = properties.stream()
                    .filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTH_TYPE))
                    .findFirst()
                    .map(EndpointProperty::getValue)
                    .orElse(null);
            String token = properties.stream()
                    .filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTHORIZATION))
                    .findFirst()
                    .map(EndpointProperty::getValue)
                    .orElse(null);

            if (authType != null && token != null) {
                return authType + " " + token;
            }
        }
        return null;
    }

}
