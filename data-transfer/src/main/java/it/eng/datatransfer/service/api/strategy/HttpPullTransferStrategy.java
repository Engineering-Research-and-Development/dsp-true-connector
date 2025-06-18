package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class HttpPullTransferStrategy implements DataTransferStrategy {

    private final S3Properties s3Properties;
    private final S3ClientService s3ClientService;
    private static final int DEFAULT_TIMEOUT = 10000; // 10 seconds

    public HttpPullTransferStrategy(S3Properties s3Properties, S3ClientService s3ClientService) {
        this.s3Properties = s3Properties;
        this.s3ClientService = s3ClientService;
    }

    @Override
    public void transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PULL transfer for process {}", transferProcess.getId());

        // get authorization information from Data Address if present
        String authorization = extractAuthorization(transferProcess);

        try {
            String key = downloadAndUploadToS3(
                    transferProcess.getDataAddress().getEndpoint(),
                    authorization,
                    transferProcess.getId()
            ).get();
            log.info("Stored transfer process id - {} data!", key);
        } catch (Exception e) {
            log.error("Download failed, {}", e.getLocalizedMessage());
            throw new DataTransferAPIException("Download failed, " + e.getLocalizedMessage());
        }
    }

    private CompletableFuture<String> downloadAndUploadToS3(String presignedUrl,
                                                            String authorization,
                                                            String key) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(presignedUrl);
            connection = (HttpURLConnection) url.openConnection();

            // Configure connection
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DEFAULT_TIMEOUT);
            connection.setReadTimeout(DEFAULT_TIMEOUT);
            if (StringUtils.isNotBlank(authorization)) {
                connection.setRequestProperty(HttpHeaders.AUTHORIZATION, authorization);
            }

            // Check if the request was successful
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to get stream. HTTP response code: " + responseCode);
            }

            log.info("Presigned URL: {}", presignedUrl);
            log.info("HTTP response code: {}", responseCode);

            String contentType = connection.getContentType();
            String contentDisposition = connection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION);
            String bucketName = s3Properties.getBucketName();
            // Use S3ClientService's uploadFile method
            return s3ClientService.uploadFile(
                    connection.getInputStream(),
                    bucketName,
                    key,
                    contentType,
                    contentDisposition
            );
        } catch (IOException e) {
            log.error("Failed to download stream", e);
            throw new DataTransferAPIException(e.getMessage());
        }
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
