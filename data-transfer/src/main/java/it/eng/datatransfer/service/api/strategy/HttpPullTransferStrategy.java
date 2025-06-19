package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@Slf4j
public class HttpPullTransferStrategy implements DataTransferStrategy {

    private final S3Properties s3Properties;
    private final S3AsyncClient s3AsyncClient;
    private static final int CHUNK_SIZE = 50 * 1024 * 1024; // 50MB chunks
    private static final int DEFAULT_TIMEOUT = 10000; // 10 seconds

    public HttpPullTransferStrategy(S3Properties s3Properties, S3AsyncClient s3AsyncClient) {
        this.s3Properties = s3Properties;
        this.s3AsyncClient = s3AsyncClient;
    }

    @Override
    public void transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PULL transfer for process {}", transferProcess.getId());

        // get authorization information from Data Address if present
        String authorization = null;
        if (transferProcess.getDataAddress().getEndpointProperties() != null) {
            List<EndpointProperty> properties = transferProcess.getDataAddress().getEndpointProperties();
            String authType = properties.stream().filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTH_TYPE))
                    .findFirst().map(EndpointProperty::getValue).orElse(null);
            String token = properties.stream()
                    .filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTHORIZATION)).findFirst()
                    .map(EndpointProperty::getValue).orElse(null);

            if (authType != null && token != null) {
                authorization = authType + " " + token;
            }
        }

        try {
            CompletableFuture<String> stringCompletableFuture = uploadStream(
                    transferProcess.getDataAddress().getEndpoint(),
                    authorization,
                    s3Properties.getBucketName(),
                    transferProcess.getId());
            log.info("Stored transfer process id - {} data!", stringCompletableFuture.get());
        } catch (Exception e) {
            log.error("Download failed, {}", e.getLocalizedMessage());
            throw new DataTransferAPIException("Download failed, " + e.getLocalizedMessage());
        }log.info("Stored transfer process id - {} data!", transferProcess.getId());
    }

    public CompletableFuture<String> uploadStream(String presignedUrl,
                                                  String authorization,
                                                  String bucketName,
                                                  String key) {
        var connRef = new Object() {
            HttpURLConnection connection = null;
        };
        try {
            URL url = new URL(presignedUrl);
            connRef.connection = (HttpURLConnection) url.openConnection();

            // Configure connection
            connRef.connection.setRequestMethod("GET");
            connRef.connection.setConnectTimeout(DEFAULT_TIMEOUT);
            connRef.connection.setReadTimeout(DEFAULT_TIMEOUT);
            if (StringUtils.isNotBlank(authorization)) {
                connRef.connection.getHeaderFields().put(HttpHeaders.AUTHORIZATION, Collections.singletonList(authorization));
            }

            // Check if the request was successful
            int responseCode = connRef.connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to get stream. HTTP response code: " + responseCode);
            }

            BufferedInputStream bufferedInputStream = new BufferedInputStream(connRef.connection.getInputStream());
            String contentType = connRef.connection.getContentType();
            String contentDisposition = connRef.connection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    log.info("Starting multipart upload for key: {}", key);
                    CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                            .bucket(bucketName)
                            .contentType(contentType)
                            .contentDisposition(contentDisposition)
                            .key(key)
                            .build();

                    log.info("Creating multipart upload for key: {}", key);
                    String uploadId = s3AsyncClient.createMultipartUpload(createMultipartUploadRequest)
                            .join()
                            .uploadId();

                    log.info("Created multipart upload for key: {} with uploadId: {}", key, uploadId);
                    List<CompletedPart> completedParts = new ArrayList<>();
                    int partNumber = 1;
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead;

                    ByteArrayOutputStream accumulator = new ByteArrayOutputStream();

                    while ((bytesRead = bufferedInputStream.read(buffer)) > 0) {
                        accumulator.write(buffer, 0, bytesRead);

                        // Upload part when accumulator reaches at least 5MB or on last part
                        if (accumulator.size() >= CHUNK_SIZE) {
                            byte[] partData = accumulator.toByteArray();
                            String eTag = uploadPart(bucketName, key, uploadId, partNumber, partData);

                            completedParts.add(CompletedPart.builder()
                                    .partNumber(partNumber)
                                    .eTag(eTag)
                                    .build());

                            partNumber++;
                            accumulator.reset();
                        }
                    }

                    // Upload any remaining data as the last part (can be less than 5MB)
                    if (accumulator.size() > 0) {
                        byte[] partData = accumulator.toByteArray();
                        String eTag = uploadPart(bucketName, key, uploadId, partNumber, partData);

                        completedParts.add(CompletedPart.builder()
                                .partNumber(partNumber)
                                .eTag(eTag)
                                .build());
                    }

                    CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                            .parts(completedParts)
                            .build();

                    CompleteMultipartUploadRequest completeMultipartUploadRequest =
                            CompleteMultipartUploadRequest.builder()
                                    .bucket(bucketName)
                                    .key(key)
                                    .uploadId(uploadId)
                                    .multipartUpload(completedMultipartUpload)
                                    .build();

                    log.info("Completing multipart upload for key: {} with uploadId: {}", key, uploadId);
                    return s3AsyncClient.completeMultipartUpload(completeMultipartUploadRequest)
                            .join()
                            .key();

                } catch (IOException e) {
                    throw new CompletionException("Failed to upload large stream to S3", e);
                } finally {
                    if (connRef.connection != null) {
                        connRef.connection.disconnect();
                    }
                }
            });
        } catch (IOException e) {
            if (connRef.connection != null) {
                connRef.connection.disconnect();
            }
            throw new RuntimeException(e);
        }
    }

    private String uploadPart(String bucketName, String key, String uploadId,
                              int partNumber, byte[] partData) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();
        log.info("Uploading part {} for key: {} with uploadId: {}", partNumber, key, uploadId);
        return s3AsyncClient.uploadPart(uploadPartRequest,
                        AsyncRequestBody.fromBytes(partData))
                .join()
                .eTag();
    }
}
