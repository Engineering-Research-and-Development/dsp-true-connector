package it.eng.tools.s3.service.upload;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.S3ClientRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Synchronous S3 upload strategy implementation.
 * Uses S3Client for sequential part uploads.
 * More compatible with Minio behind reverse proxies like Caddy.
 */
@Component
@Slf4j
public class S3SyncUploadStrategy implements S3UploadStrategy {

    private final S3ClientProvider s3ClientProvider;

    public S3SyncUploadStrategy(S3ClientProvider s3ClientProvider) {
        this.s3ClientProvider = s3ClientProvider;
    }

    @Override
    public CompletableFuture<String> uploadFile(InputStream inputStream,
                                               S3ClientRequest s3ClientRequest,
                                               String bucketName,
                                               String objectKey,
                                               String contentType,
                                               String contentDisposition) {
        return CompletableFuture.supplyAsync(() -> {
            S3Client s3Client = s3ClientProvider.s3Client(s3ClientRequest);

            try {
                log.info("Creating multipart upload (SYNC) for key: {}", objectKey);

                CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .contentType(contentType)
                        .contentDisposition(contentDisposition)
                        .key(objectKey)
                        .build();

                CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
                String uploadId = createResponse.uploadId();

                log.info("Created multipart upload (SYNC) for key: {} with uploadId: {}", objectKey, uploadId);

                List<CompletedPart> completedParts = new ArrayList<>();
                int partNumber = 1;
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;
                ByteArrayOutputStream accumulator = new ByteArrayOutputStream();

                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    accumulator.write(buffer, 0, bytesRead);

                    if (accumulator.size() >= CHUNK_SIZE) {
                        byte[] partData = accumulator.toByteArray();
                        CompletedPart part = uploadPart(s3Client, bucketName, objectKey, uploadId, partNumber, partData);
                        completedParts.add(part);
                        partNumber++;
                        accumulator.reset();
                    }
                }

                if (accumulator.size() > 0) {
                    byte[] partData = accumulator.toByteArray();
                    CompletedPart part = uploadPart(s3Client, bucketName, objectKey, uploadId, partNumber, partData);
                    completedParts.add(part);
                }

                log.info("All {} parts uploaded successfully (SYNC) for key: {}", completedParts.size(), objectKey);

                CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build();

                CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .uploadId(uploadId)
                        .multipartUpload(completedUpload)
                        .build();

                log.info("Completing multipart upload (SYNC) for key: {} with uploadId: {}", objectKey, uploadId);

                CompleteMultipartUploadResponse completeResponse = s3Client.completeMultipartUpload(completeRequest);
                String eTag = completeResponse.eTag();

                log.info("Upload completed successfully (SYNC) for key: {} with ETag: {}", objectKey, eTag);

                return eTag;

            } catch (IOException e) {
                log.error("Failed to upload file (SYNC) {}: {}", objectKey, e.getMessage());
                throw new CompletionException("Failed to upload file", e);
            } catch (Exception e) {
                log.error("Failed to upload file (SYNC) {}: {}", objectKey, e.getMessage());
                throw new CompletionException("Failed to upload file", e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error("Failed to close input stream: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * Uploads a single part synchronously using S3Client.
     *
     * @param s3Client   the S3 client
     * @param bucketName the bucket name
     * @param objectKey  the object key
     * @param uploadId   the upload ID
     * @param partNumber the part number
     * @param partData   the part data
     * @return the completed part
     */
    private CompletedPart uploadPart(S3Client s3Client,
                                    String bucketName,
                                    String objectKey,
                                    String uploadId,
                                    int partNumber,
                                    byte[] partData) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        log.debug("Uploading part {} (SYNC) for key: {} ({} bytes)", partNumber, objectKey, partData.length);

        UploadPartResponse response = s3Client.uploadPart(uploadPartRequest,
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(partData));

        log.debug("Part {} uploaded successfully (SYNC) with ETag: {}", partNumber, response.eTag());

        return CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(response.eTag())
                .build();
    }
}

