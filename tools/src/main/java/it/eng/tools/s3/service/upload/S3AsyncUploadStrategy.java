package it.eng.tools.s3.service.upload;

import it.eng.tools.exceptions.TransferCancelledException;
import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous S3 upload strategy implementation.
 * Uses S3AsyncClient with parallel part uploads for better performance.
 * Faster but may have issues with Minio behind reverse proxies.
 */
@Component
@Slf4j
public class S3AsyncUploadStrategy implements S3UploadStrategy {

    /**
     * Maximum number of parts to upload in parallel.
     * Each in-flight part holds one {@code s3.chunkSize} (default 10 MB) buffer.
     * Capping at 4 limits the async strategy's peak RAM to ~200 MB per transfer.
     */
    private static final int MAX_PARALLEL_PARTS = 4;

    private final S3ClientProvider s3ClientProvider;
    private final S3Properties s3Properties;

    public S3AsyncUploadStrategy(S3ClientProvider s3ClientProvider, S3Properties s3Properties) {
        this.s3ClientProvider = s3ClientProvider;
        this.s3Properties = s3Properties;
    }

    @Override
    public CompletableFuture<String> uploadFile(InputStream inputStream,
                                               S3ClientRequest s3ClientRequest,
                                               String bucketName,
                                               String objectKey,
                                               String contentType,
                                               String contentDisposition,
                                               AtomicBoolean cancellationToken,
                                               UploadCheckpointCallback checkpointCallback) {
        S3AsyncClient s3AsyncClient = s3ClientProvider.s3AsyncClient(s3ClientRequest);

        log.info("Creating multipart upload (ASYNC) for key: {}", objectKey);

        return s3AsyncClient.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(bucketName).contentType(contentType)
                        .contentDisposition(contentDisposition).key(objectKey).build())
                .thenComposeAsync(response -> {
                    String uploadId = response.uploadId();
                    log.info("Created multipart upload (ASYNC) uploadId={} key={}", uploadId, objectKey);
                    checkpointCallback.onUploadStarted(uploadId);
                    return uploadParts(inputStream, s3AsyncClient, bucketName, objectKey, uploadId,
                            cancellationToken, checkpointCallback);
                })
                .thenComposeAsync(uploadResult ->
                        completeMultipartUpload(s3AsyncClient, bucketName, objectKey,
                                uploadResult.uploadId(), uploadResult.completedParts()))
                .exceptionally(throwable -> {
                    Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                            ? throwable.getCause() : throwable;
                    if (cause instanceof TransferCancelledException) {
                        log.info("Transfer cancelled (ASYNC) key={}. Aborting multipart.", objectKey);
                        throw new CompletionException(cause);
                    }
                    log.error("Upload failed (ASYNC) key={}: {}", objectKey, throwable.getMessage());
                    throw new CompletionException("Failed to upload file", throwable);
                })
                .whenComplete((result, throwable) -> {
                    try { inputStream.close(); } catch (IOException e) {
                        log.error("Failed to close input stream: {}", e.getMessage());
                    }
                });
    }


    /**
     * Reads the input stream and uploads parts asynchronously in parallel.
     *
     * @param inputStream        the input stream to read from
     * @param s3AsyncClient      the S3 async client
     * @param bucketName         the bucket name
     * @param objectKey          the object key
     * @param uploadId           the upload ID
     * @param cancellationToken  set to true externally to request graceful stop
     * @param checkpointCallback invoked after each successfully uploaded part
     * @return a CompletableFuture with the upload result
     */
    private CompletableFuture<UploadResult> uploadParts(InputStream inputStream,
                                                        S3AsyncClient s3AsyncClient,
                                                        String bucketName,
                                                        String objectKey,
                                                        String uploadId,
                                                        AtomicBoolean cancellationToken,
                                                        UploadCheckpointCallback checkpointCallback) {
        List<CompletableFuture<CompletedPart>> partFutures = new ArrayList<>();
        AtomicLong totalBytesUploaded = new AtomicLong(0);

        return CompletableFuture.runAsync(() -> {
            try {
                int partNumber = 1;
                byte[] buffer = new byte[s3Properties.getChunkSize()];
                Semaphore parallelism = new Semaphore(MAX_PARALLEL_PARTS);

                while (true) {
                    if (cancellationToken.get()) {
                        log.info("Cancellation signalled before part {} (ASYNC) key={}. Aborting.", partNumber, objectKey);
                        s3AsyncClient.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                                .bucket(bucketName).key(objectKey).uploadId(uploadId).build());
                        throw new CompletionException(new TransferCancelledException(objectKey));
                    }
                    int totalRead = readFully(inputStream, buffer);
                    if (totalRead == 0) break;

                    byte[] partData = Arrays.copyOf(buffer, totalRead);
                    final int currentPartNumber = partNumber;
                    final long partBytes = totalRead;
                    parallelism.acquire();

                    CompletableFuture<CompletedPart> partFuture = uploadPart(
                            s3AsyncClient, bucketName, objectKey, uploadId, currentPartNumber, partData)
                            .whenComplete((completedPart, t) -> {
                                parallelism.release();
                                if (t == null && completedPart != null) {
                                    long cumulative = totalBytesUploaded.addAndGet(partBytes);
                                    checkpointCallback.onPartCompleted(currentPartNumber, completedPart.eTag(), cumulative);
                                }
                            });
                    partFutures.add(partFuture);
                    partNumber++;
                }
            } catch (IOException e) {
                throw new CompletionException("Failed to read input stream", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException("Upload interrupted", e);
            }
        }).thenCompose(v ->
            CompletableFuture.allOf(partFutures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    List<CompletedPart> completedParts = partFutures.stream()
                            .map(CompletableFuture::join).toList();
                    log.info("All {} parts uploaded (ASYNC) key={}", completedParts.size(), objectKey);
                    return new UploadResult(uploadId, completedParts);
                })
        );
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
     * Reads bytes from the stream until the buffer is full or the stream is exhausted.
     *
     * @param in  the input stream
     * @param buf the buffer to fill
     * @return the number of bytes actually read; 0 means the stream is exhausted
     * @throws IOException if an I/O error occurs
     */
    private int readFully(InputStream in, byte[] buf) throws IOException {
        int offset = 0, read;
        while (offset < buf.length && (read = in.read(buf, offset, buf.length - offset)) != -1) {
            offset += read;
        }
        return offset;
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

