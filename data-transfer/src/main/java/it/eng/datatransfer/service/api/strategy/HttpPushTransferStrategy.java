package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.PresignedUrlExpiredException;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferArtifactState;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.service.CancellationRegistry;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.exceptions.TransferCancelledException;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.service.FieldEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HttpPushTransferStrategy implements DataTransferStrategy {

    private final S3Properties s3Properties;
    private final S3ClientService s3ClientService;
    private final Executor transferExecutor;
    private final FieldEncryptionService fieldEncryptionService;
    private final TransferArtifactStateRepository transferArtifactStateRepository;
    private final CancellationRegistry cancellationRegistry;
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    /**
     * Fallback read timeout (30 minutes) used before Content-Length is known.
     * Refined to a dynamic value once response headers are received.
     */
    private static final int FALLBACK_READ_TIMEOUT = 1_800_000; // 30 minutes
    /** Assumed minimum transfer speed in bytes/sec used for dynamic timeout (1 MB/s). */
    private static final long MIN_TRANSFER_SPEED_BYTES_PER_SEC = 1024L * 1024L;
    /** HTTP 206 Partial Content — returned when a Range header was honoured. */
    private static final int HTTP_PARTIAL_CONTENT = 206;

    /**
     * Creates an instance using the Spring-managed {@code httpPushTransferExecutor} bean.
     *
     * @param s3Properties S3 configuration properties
     * @param s3ClientService service for downloading and uploading data to S3
     * @param transferExecutor Spring-managed executor for running async transfer tasks
     * @param fieldEncryptionService service for decrypting sensitive fields stored in MongoDB
     * @param transferArtifactStateRepository repository for managing transfer checkpoint state
     * @param cancellationRegistry registry for managing transfer cancellation tokens
     */
    @Autowired
    public HttpPushTransferStrategy(S3Properties s3Properties,
                                    S3ClientService s3ClientService,
                                    @Qualifier("httpPushTransferExecutor") Executor transferExecutor,
                                    FieldEncryptionService fieldEncryptionService,
                                    TransferArtifactStateRepository transferArtifactStateRepository,
                                    CancellationRegistry cancellationRegistry) {
        this.s3Properties = s3Properties;
        this.s3ClientService = s3ClientService;
        this.transferExecutor = transferExecutor;
        this.fieldEncryptionService = fieldEncryptionService;
        this.transferArtifactStateRepository = transferArtifactStateRepository;
        this.cancellationRegistry = cancellationRegistry;
    }

    @Override
    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        Map<String, String> destinationS3Properties = buildDestinationProperties(transferProcess);

        TransferArtifactState checkpoint = transferArtifactStateRepository
                .findById(transferProcess.getId())
                .orElseGet(() -> TransferArtifactState.Builder.newInstance()
                        .id(transferProcess.getId()).downloadedBytes(0).build());

        long rangeStart = checkpoint.getDownloadedBytes();
        if (rangeStart > 0) {
            log.info("Resuming HTTP PUSH for process {} from byte offset {}", transferProcess.getId(), rangeStart);
            checkpoint.setUploadId(null);
        }
        transferArtifactStateRepository.save(checkpoint);

        AtomicBoolean cancellationToken = cancellationRegistry.register(transferProcess.getId());

        CheckpointCallbackImpl checkpointCallback = new CheckpointCallbackImpl(
                transferProcess.getId(), rangeStart, transferArtifactStateRepository);

        // Always generate a fresh presigned URL for PUSH (provider controls the source)
        String presignedUrl = s3ClientService.generateGetPresignedUrl(
                s3Properties.getBucketName(), transferProcess.getDatasetId(), Duration.ofDays(1L));

        return transfer(presignedUrl, destinationS3Properties, rangeStart, cancellationToken, checkpointCallback)
                .thenAccept(key -> log.info("Pushed transfer process id - {} data!", key));
    }

    /**
     * Builds destination S3 properties map from the transfer process.
     * 
     * @param transferProcess the transfer process containing endpoint properties
     * @return map of destination S3 properties with decrypted secret keys
     */
    private Map<String, String> buildDestinationProperties(TransferProcess transferProcess) {
        return transferProcess.getDataAddress().getEndpointProperties()
                .stream()
                .collect(Collectors.toMap(
                        EndpointProperty::getName,
                        prop -> S3Utils.SECRET_KEY.equals(prop.getName())
                                ? fieldEncryptionService.decrypt(prop.getValue())
                                : prop.getValue()
                ));
    }

    private CompletableFuture<String> transfer(String presignedUrl,
                                               Map<String, String> destinationS3Properties,
                                               long rangeStart,
                                               AtomicBoolean cancellationToken,
                                               UploadCheckpointCallback checkpointCallback) {
        // AtomicReference allows the connection to be shared across two separate lambda stages
        // (supplyAsync and whenComplete) without violating Java's effectively-final capture rule.
        // The supplyAsync lambda opens the connection and stores it here; whenComplete reads it
        // to guarantee disconnect() is called on all paths — success, failure, or cancellation.
        AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();

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

                if (rangeStart > 0) {
                    connection.setRequestProperty(HttpHeaders.RANGE, "bytes=" + rangeStart + "-");
                    log.debug("Added Range header bytes={}- for push presignedUrl: {}", rangeStart, presignedUrl);
                }

                if (connection instanceof HttpsURLConnection) {
                    log.debug("Using HTTPS connection to: {}", presignedUrl);
                } else {
                    log.debug("Using HTTP connection to: {}", presignedUrl);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    connection.disconnect();
                    connectionRef.set(null);
                    throw new PresignedUrlExpiredException(presignedUrl);
                }
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HTTP_PARTIAL_CONTENT) {
                    // Disconnect eagerly on error response and clear the ref so whenComplete skips it
                    connection.disconnect();
                    connectionRef.set(null);
                    throw new DataTransferAPIException("Failed to get stream. HTTP response code: " + responseCode);
                }

                log.debug("Presigned URL: {}", presignedUrl);
                log.info("HTTP response code: {}", responseCode);

                // Refine read timeout now that response headers are available.
                // NOTE: setFixedLengthStreamingMode is intentionally NOT used — it controls outgoing
                // request body size (PUT/POST only) and throws IllegalStateException: Already connected
                // when called after getResponseCode() on a GET request.
                long contentLength = connection.getContentLengthLong();
                if (contentLength > 0) {
                    int dynamicTimeout = computeReadTimeout(contentLength);
                    connection.setReadTimeout(dynamicTimeout);
                    log.debug("Content-Length: {} bytes — dynamic read timeout set to {} ms", contentLength, dynamicTimeout);
                }

                // uploadFile is non-blocking and returns a CompletableFuture<String>.
                // Returning it here produces a CompletableFuture<CompletableFuture<String>>,
                // which thenCompose below flattens into a single CompletableFuture<String>.
                // The connection must remain open until the upload future completes.
                return s3ClientService.uploadFile(
                        connection.getInputStream(),
                        destinationS3Properties,
                        connection.getContentType(),
                        connection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION),
                        cancellationToken,
                        checkpointCallback);
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

    /**
     * Implementation of UploadCheckpointCallback for saving transfer progress.
     */
    private static class CheckpointCallbackImpl implements UploadCheckpointCallback {
        private final String transferProcessId;
        private final long rangeStart;
        private final TransferArtifactStateRepository repository;

        CheckpointCallbackImpl(String transferProcessId, long rangeStart, 
                              TransferArtifactStateRepository repository) {
            this.transferProcessId = transferProcessId;
            this.rangeStart = rangeStart;
            this.repository = repository;
        }

        @Override
        public void onUploadStarted(String uploadId) {
            TransferArtifactState state = repository.findById(transferProcessId)
                    .orElseThrow(() -> new IllegalStateException("Checkpoint missing for transfer: " + transferProcessId));
            state.setUploadId(uploadId);
            repository.save(state);
        }

        @Override
        public void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) {
            TransferArtifactState state = repository.findById(transferProcessId)
                    .orElseThrow(() -> new IllegalStateException("Checkpoint missing for transfer: " + transferProcessId));
            state.setDownloadedBytes(rangeStart + totalBytesUploaded);
            repository.save(state);
        }
    }
}
