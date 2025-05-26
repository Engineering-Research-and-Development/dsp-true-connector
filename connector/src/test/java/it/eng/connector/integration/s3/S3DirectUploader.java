package it.eng.connector.integration.s3;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public class S3DirectUploader {

    private final S3AsyncClient s3AsyncClient;
    private static final int CHUNK_SIZE = 50 * 1024 * 1024; // 50MB chunks

    public S3DirectUploader(S3AsyncClient s3AsyncClient) {
        this.s3AsyncClient = s3AsyncClient;
    }

    public CompletableFuture<Void> uploadStream(InputStream inputStream, String bucketName, String key) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Create a byte array of chunk size
                byte[] buffer = new byte[CHUNK_SIZE];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }

                // Create PutObjectRequest
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();

                // Upload using AsyncRequestBody
                s3AsyncClient.putObject(putObjectRequest,
                                AsyncRequestBody.fromBytes(baos.toByteArray()))
                        .join();

            } catch (IOException e) {
                throw new CompletionException("Failed to upload stream to S3", e);
            }
        });
    }

    // For uploading very large files using multipart upload
    public CompletableFuture<String> uploadLargeStream(InputStream inputStream,
                                                       String bucketName,
                                                       String key) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting multipart upload for key: {}", key);
                CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                        .bucket(bucketName)
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

                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    accumulator.write(buffer, 0, bytesRead);

                    // Upload part when accumulator reaches at least 5MB or on last part
                    if (accumulator.size() >= 15 * 1024 * 1024) {
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
                        .eTag();

            } catch (IOException e) {
                throw new CompletionException("Failed to upload large stream to S3", e);
            }
        });
    }

    private String uploadPart(String bucketName, String key, String uploadId,
                              int partNumber, byte[] partData) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();
        log.debug("Uploading part {} for key: {} with uploadId: {}", partNumber, key, uploadId);
        return s3AsyncClient.uploadPart(uploadPartRequest,
                        AsyncRequestBody.fromBytes(partData))
                .join()
                .eTag();
    }
}
