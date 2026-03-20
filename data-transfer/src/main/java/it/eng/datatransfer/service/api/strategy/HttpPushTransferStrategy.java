package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
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
import java.util.stream.Collectors;

@Service
@Slf4j
public class HttpPushTransferStrategy implements DataTransferStrategy {

    private final S3Properties s3Properties;
    private final S3ClientService s3ClientService;
    private final Executor transferExecutor;
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    /**
     * Fallback read timeout (30 minutes) used before Content-Length is known.
     * Refined to a dynamic value once response headers are received.
     */
    private static final int FALLBACK_READ_TIMEOUT = 1_800_000; // 30 minutes
    /** Assumed minimum transfer speed in bytes/sec used for dynamic timeout (1 MB/s). */
    private static final long MIN_TRANSFER_SPEED_BYTES_PER_SEC = 1024L * 1024L;

    /**
     * Creates an instance using the Spring-managed {@code httpPushTransferExecutor} bean.
     *
     * @param s3Properties S3 configuration properties
     * @param s3ClientService service for downloading and uploading data to S3
     * @param transferExecutor Spring-managed executor for running async transfer tasks
     */
    @Autowired
    public HttpPushTransferStrategy(S3Properties s3Properties,
                                    S3ClientService s3ClientService,
                                    @Qualifier("httpPushTransferExecutor") Executor transferExecutor) {
        this.s3Properties = s3Properties;
        this.s3ClientService = s3ClientService;
        this.transferExecutor = transferExecutor;
    }

    @Override
    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        // Convert endpoint properties to a map for easier access
        Map<String, String> destinationS3Properties = transferProcess.getDataAddress().getEndpointProperties()
                .stream()
                .collect(Collectors.toMap(EndpointProperty::getName, EndpointProperty::getValue));
        String presignedUrl = s3ClientService.generateGetPresignedUrl(s3Properties.getBucketName(), transferProcess.getDatasetId(), Duration.ofDays(1L));
        return transfer(presignedUrl, destinationS3Properties)
                .thenAccept(key ->
                        log.info("Pushed transfer process id - {} data!", key));
    }

    private CompletableFuture<String> transfer(String presignedUrl, Map<String, String> destinationS3Properties) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(presignedUrl);
                connection = (HttpURLConnection) url.openConnection();

                // Configure connection
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
                connection.setReadTimeout(FALLBACK_READ_TIMEOUT);

                // Log connection type for debugging
                if (connection instanceof HttpsURLConnection) {
                    log.debug("Using HTTPS connection to: {}", presignedUrl);
                } else {
                    log.debug("Using HTTP connection to: {}", presignedUrl);
                }

                // Check if the request was successful
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Failed to get stream. HTTP response code: " + responseCode);
                }

                log.info("Presigned URL: {}", presignedUrl);
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

                // Use S3ClientService's uploadFile method
                return s3ClientService.uploadFile(
                        connection.getInputStream(),
                        destinationS3Properties,
                        connection.getContentType(),
                        connection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION)).join();
            } catch (IOException e) {
                if (connection != null) {
                    connection.disconnect();
                }
                log.error("Failed to download stream from URL: {}", presignedUrl, e);
                throw new DataTransferAPIException(e.getMessage());
            }
        }, transferExecutor);
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
}
