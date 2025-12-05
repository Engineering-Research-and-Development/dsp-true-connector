package it.eng.tools.s3.service.upload;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.S3ClientRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Asynchronous S3 upload strategy implementation.
 * Uses S3AsyncClient with parallel part uploads for better performance.
 * Faster but may have issues with Minio behind reverse proxies.
 */
@Component
@Slf4j
public class S3AsyncUploadStrategy implements S3UploadStrategy {

    private final S3ClientProvider s3ClientProvider;

    public S3AsyncUploadStrategy(S3ClientProvider s3ClientProvider) {
        this.s3ClientProvider = s3ClientProvider;
    }

    @Override
    public CompletableFuture<String> uploadFile(InputStream inputStream,
                                               S3ClientRequest s3ClientRequest,
                                               String bucketName,
                                               String objectKey,
                                               String contentType,
                                               String contentDisposition) {
        S3AsyncClient s3AsyncClient = s3ClientProvider.s3AsyncClient(s3ClientRequest);

        CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .contentType(contentType)
                .contentDisposition(contentDisposition)
                .key(objectKey)
                .build();

        log.info("Creating multipart upload (ASYNC) for key: {}", objectKey);

        return s3AsyncClient.createMultipartUpload(createMultipartUploadRequest)
                .thenComposeAsync(response -> {
                    String uploadId = response.uploadId();
                    log.info("Created multipart upload (ASYNC) for key: {} with uploadId: {}", objectKey, uploadId);

                    return uploadParts(inputStream, s3AsyncClient, bucketName, objectKey, uploadId);
                })
                .thenComposeAsync(uploadResult -> {
                    return completeMultipartUpload(
                            s3AsyncClient,
                            bucketName,
                            objectKey,
                            uploadResult.uploadId(),
                            uploadResult.completedParts());
                })
                .exceptionally(throwable -> {
                    log.error("Failed to upload file (ASYNC) {}: {}", objectKey, throwable.getMessage());
                    throw new CompletionException("Failed to upload file", throwable);
                })
                .whenComplete((result, throwable) -> {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        log.error("Failed to close input stream: {}", e.getMessage());
                    }
                });
    }

    /**
     * Reads the input stream and uploads parts asynchronously in parallel.
     *
     * @param inputStream   the input stream to read from
     * @param s3AsyncClient the S3 async client
     * @param bucketName    the bucket name
     * @param objectKey     the object key
     * @param uploadId      the upload ID
     * @return a CompletableFuture with the upload result
     */
    private CompletableFuture<UploadResult> uploadParts(InputStream inputStream,
                                                        S3AsyncClient s3AsyncClient,
                                                        String bucketName,
                                                        String objectKey,
                                                        String uploadId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<CompletableFuture<CompletedPart>> partFutures = new ArrayList<>();
                int partNumber = 1;
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;

                ByteArrayOutputStream accumulator = new ByteArrayOutputStream();

                log.debug("Reading stream and initiating parallel uploads...");
                // Read stream and create upload futures for each part
                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    accumulator.write(buffer, 0, bytesRead);

                    // Upload part when accumulator reaches buffer size
                    if (accumulator.size() >= CHUNK_SIZE) {
                        byte[] partData = accumulator.toByteArray();
                        final int currentPartNumber = partNumber;

                        // Create async upload for this part (non-blocking)
                        CompletableFuture<CompletedPart> partFuture = uploadPart(
                                s3AsyncClient,
                                bucketName,
                                objectKey,
                                uploadId,
                                currentPartNumber,
                                partData);

                        partFutures.add(partFuture);
                        partNumber++;
                        accumulator.reset();
                    }
                }

                // Upload any remaining data as the last part
                if (accumulator.size() > 0) {
                    byte[] partData = accumulator.toByteArray();
                    final int currentPartNumber = partNumber;

                    CompletableFuture<CompletedPart> partFuture = uploadPart(
                            s3AsyncClient,
                            bucketName,
                            objectKey,
                            uploadId,
                            currentPartNumber,
                            partData);

                    partFutures.add(partFuture);
                }

                // Wait for all parts to complete in parallel
                CompletableFuture<Void> allParts = CompletableFuture.allOf(
                        partFutures.toArray(new CompletableFuture[0]));

                return allParts.thenApply(v -> {
                    // Collect all completed parts
                    List<CompletedPart> completedParts = partFutures.stream()
                            .map(CompletableFuture::join)
                            .toList();

                    log.info("All {} parts uploaded successfully for key: {}", completedParts.size(), objectKey);
                    return new UploadResult(uploadId, completedParts);
                }).join();

            } catch (IOException e) {
                throw new CompletionException("Failed to read input stream", e);
            }
        });
    }

    /**
     * Uploads a single part asynchronously.
     *
     * @param s3AsyncClient the S3 async client
     * @param bucketName    the bucket name
     * @param objectKey     the object key
     * @param uploadId      the upload ID
     * @param partNumber    the part number
     * @param partData      the part data
     * @return a CompletableFuture with the completed part
     */
    private CompletableFuture<CompletedPart> uploadPart(S3AsyncClient s3AsyncClient,
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

        log.debug("Uploading part {} for key: {} ({} bytes)", partNumber, objectKey, partData.length);

        return s3AsyncClient.uploadPart(uploadPartRequest, AsyncRequestBody.fromBytes(partData))
                .thenApply(response -> {
                    log.debug("Part {} uploaded successfully with ETag: {}", partNumber, response.eTag());
                    return CompletedPart.builder()
                            .partNumber(partNumber)
                            .eTag(response.eTag())
                            .build();
                });
    }

    /**
     * Completes the multipart upload.
     *
     * @param s3AsyncClient  the S3 async client
     * @param bucketName     the bucket name
     * @param objectKey      the object key
     * @param uploadId       the upload ID
     * @param completedParts the list of completed parts
     * @return a CompletableFuture with the ETag of the completed upload
     */
    private CompletableFuture<String> completeMultipartUpload(S3AsyncClient s3AsyncClient,
                                                              String bucketName,
                                                              String objectKey,
                                                              String uploadId,
                                                              List<CompletedPart> completedParts) {
        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .multipartUpload(completedMultipartUpload)
                .build();

        log.info("Completing multipart upload for key: {} with uploadId: {}", objectKey, uploadId);

        return s3AsyncClient.completeMultipartUpload(completeRequest)
                .thenApply(response -> {
                    log.info("Upload completed successfully for key: {} with ETag: {}", objectKey, response.eTag());
                    return response.eTag();
                });
    }

    /**
     * Helper record for passing upload state between async stages.
     */
    private record UploadResult(String uploadId, List<CompletedPart> completedParts) {
    }
}

