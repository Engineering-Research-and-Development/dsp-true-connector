package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class HttpPullTransferStrategy implements DataTransferStrategy {

    private final S3ClientService s3ClientService;
    private final S3Properties s3Properties;
    private final Executor transferExecutor;
    private static final int DEFAULT_CONNECT_TIMEOUT = 10_000; // 10 seconds
    /**
     * Fallback read timeout (30 minutes) used when the server does not advertise
     * Content-Length. For known sizes the timeout is computed dynamically.
     */
    private static final int FALLBACK_READ_TIMEOUT = 1_800_000; // 30 minutes
    /** Assumed minimum transfer speed in bytes/sec used for dynamic timeout (1 MB/s). */
    private static final long MIN_TRANSFER_SPEED_BYTES_PER_SEC = 1024L * 1024L;
    /**
     * Bounded thread pool to cap concurrent HTTP-PULL transfers.
     * Each transfer holds up to ~50 MB; 8 concurrent transfers = ~400 MB.
     */
    private static final Executor DEFAULT_EXECUTOR = Executors.newFixedThreadPool(8);

    /**
     * Creates an instance using the default bounded thread pool.
     *
     * @param s3ClientService service for uploading data to S3
     * @param s3Properties S3 configuration properties
     */
    @Autowired
    public HttpPullTransferStrategy(S3ClientService s3ClientService, S3Properties s3Properties) {
        this(s3ClientService, s3Properties, DEFAULT_EXECUTOR);
    }

    /**
     * Creates an instance with a custom executor.
     * Package-private to allow injection of a synchronous executor in tests.
     *
     * @param s3ClientService service for uploading data to S3
     * @param s3Properties S3 configuration properties
     * @param transferExecutor executor used to run async transfer tasks
     */
    HttpPullTransferStrategy(S3ClientService s3ClientService, S3Properties s3Properties, Executor transferExecutor) {
        this.s3ClientService = s3ClientService;
        this.s3Properties = s3Properties;
        this.transferExecutor = transferExecutor;
    }

    @Override
    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PULL transfer for process {}", transferProcess.getId());

        // get authorization information from Data Address if present
        String authorization = extractAuthorization(transferProcess);

        return downloadAndUploadToS3(
                transferProcess.getDataAddress().getEndpoint(),
                authorization,
                transferProcess.getId()
        ).thenAccept(key ->
                log.info("Stored transfer process id - {} data!", key));
    }

    private CompletableFuture<String> downloadAndUploadToS3(String presignedUrl,
                                                            String authorization,
                                                            String key) {
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
                if (StringUtils.isNotBlank(authorization)) {
                    connection.setRequestProperty(HttpHeaders.AUTHORIZATION, authorization);
                }

                if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                    log.debug("Using HTTPS connection to: {}", presignedUrl);
                } else {
                    log.debug("Using HTTP connection to: {}", presignedUrl);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    // Disconnect eagerly on error response and clear the ref so whenComplete skips it
                    connection.disconnect();
                    connectionRef.set(null);
                    throw new DataTransferAPIException("Failed to get stream. HTTP response code: " + responseCode);
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
                return s3ClientService.uploadFile(connection.getInputStream(), destinationS3Properties, contentType, contentDisposition);
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
