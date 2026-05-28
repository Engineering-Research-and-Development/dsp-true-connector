package it.eng.tools.s3.service.upload;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;

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

    /**
     * Constructs an {@link S3AsyncUploadStrategy}.
     *
     * @param s3ClientProvider provider for S3 async client instances
     * @param s3Properties     S3 configuration properties
     */
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
                                                ResumableUploadRequest resumable) {
        S3AsyncClient s3AsyncClient = s3ClientProvider.s3AsyncClient(s3ClientRequest);
        UploadCheckpointCallback callback = resumable.checkpointCallback();

        // Initialize part-size map from previously completed parts
        Map<Integer, Long> partSizeByNumber = new HashMap<>();
        for (int i = 0; i < resumable.completedParts().size(); i++) {
            partSizeByNumber.put(
                    resumable.completedParts().get(i).partNumber(),
                    resumable.partSizes().get(i));
        }

        // Determine the upload ID: reuse existing or create a new one
        CompletableFuture<String> uploadIdFuture;
        if (resumable.uploadId() != null && !resumable.uploadId().isBlank()) {
            log.info("Reusing existing multipart upload (ASYNC) for key: {} with uploadId: {}",
                    objectKey, resumable.uploadId());
            uploadIdFuture = CompletableFuture.completedFuture(resumable.uploadId());
        } else {
            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .contentType(contentType)
                    .contentDisposition(contentDisposition)
                    .key(objectKey)
                    .build();

            log.info("Creating multipart upload (ASYNC) for key: {}", objectKey);

            uploadIdFuture = s3AsyncClient.createMultipartUpload(createRequest)
                    .thenApply(response -> {
                        String newUploadId = response.uploadId();
                        log.info("Created multipart upload (ASYNC) for key: {} with uploadId: {}",
                                objectKey, newUploadId);
                        callback.onMultipartCreated(newUploadId);
                        return newUploadId;
                    });
        }

        return uploadIdFuture
                .thenComposeAsync(uploadId ->
                        uploadParts(inputStream, s3AsyncClient, bucketName, objectKey,
                                uploadId, resumable, partSizeByNumber))
                .thenComposeAsync(uploadResult ->
                        completeMultipartUpload(s3AsyncClient, bucketName, objectKey,
                                uploadResult.uploadId(), uploadResult.completedParts()))
                .exceptionally(throwable -> {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (cause instanceof UploadPausedException pausedException) {
                        throw pausedException;
                    }
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
     * Reads the input stream and uploads parts asynchronously with bounded parallelism.
     *
     * @param inputStream      the input stream to read from
     * @param s3AsyncClient    the S3 async client
     * @param bucketName       the bucket name
     * @param objectKey        the object key
     * @param uploadId         the upload ID
     * @param resumable        the resumable upload context
     * @param partSizeByNumber mutable map of partNumber to size in bytes,
     *                         pre-populated with previously completed parts
     * @return a CompletableFuture with the upload result
     */
    private CompletableFuture<UploadResult> uploadParts(InputStream inputStream,
                                                         S3AsyncClient s3AsyncClient,
                                                         String bucketName,
                                                         String objectKey,
                                                         String uploadId,
                                                         ResumableUploadRequest resumable,
                                                         Map<Integer, Long> partSizeByNumber) {
        List<CompletableFuture<CompletedPart>> partFutures = new ArrayList<>();
        List<CompletedPart> existingParts = resumable.completedParts();
        UploadCheckpointCallback callback = resumable.checkpointCallback();

        return CompletableFuture.runAsync(() -> {
            try {
                int partNumber = existingParts.size() + 1;
                byte[] buffer = new byte[s3Properties.getChunkSize()];
                Semaphore parallelism = new Semaphore(MAX_PARALLEL_PARTS);

                log.debug("Reading stream and initiating bounded-parallel uploads...");

                while (true) {
                    if (resumable.suspendRequested().get()) {
                        buildPauseException(uploadId, partFutures, existingParts, partSizeByNumber);
                    }

                    int totalRead = readFully(inputStream, buffer);
                    if (totalRead == 0) break;

                    byte[] partData = Arrays.copyOf(buffer, totalRead);

                    final int currentPartNumber = partNumber;
                    final long currentPartSize = totalRead;

                    parallelism.acquire();

                    CompletableFuture<CompletedPart> partFuture = uploadPart(
                            s3AsyncClient, bucketName, objectKey, uploadId, currentPartNumber, partData)
                            .thenApply(part -> {
                                synchronized (partSizeByNumber) {
                                    partSizeByNumber.put(currentPartNumber, currentPartSize);
                                    long contiguous = calculateContiguous(partSizeByNumber);
                                    callback.onPartCompleted(currentPartNumber, part.eTag(), currentPartSize, contiguous);
                                }
                                return part;
                            })
                            .whenComplete((r, t) -> parallelism.release());

                    partFutures.add(partFuture);
                    partNumber++;
                }
            } catch (IOException e) {
                throw new CompletionException("Failed to read input stream", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException("Upload interrupted", e);
            }
        })
        .thenCompose(v ->
            CompletableFuture.allOf(partFutures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    List<CompletedPart> newParts = partFutures.stream()
                            .map(CompletableFuture::join)
                            .toList();
                    List<CompletedPart> allParts = new ArrayList<>(existingParts);
                    allParts.addAll(newParts);
                    log.info("All {} parts uploaded successfully for key: {}", allParts.size(), objectKey);
                    return new UploadResult(uploadId, allParts);
                })
        );
    }

    /**
     * Builds and throws an {@link UploadPausedException} from the current in-flight state.
     *
     * @param uploadId         the current multipart upload ID
     * @param partFutures      futures for the current run's parts
     * @param existingParts    parts carried over from a previous run
     * @param partSizeByNumber map of partNumber to size in bytes
     */
    private void buildPauseException(String uploadId,
                                     List<CompletableFuture<CompletedPart>> partFutures,
                                     List<CompletedPart> existingParts,
                                     Map<Integer, Long> partSizeByNumber) {
        synchronized (partSizeByNumber) {
            List<CompletedPart> confirmedParts = new ArrayList<>(existingParts);
            partFutures.stream()
                    .filter(f -> f.isDone() && !f.isCompletedExceptionally())
                    .map(CompletableFuture::join)
                    .forEach(confirmedParts::add);

            List<Long> sizes = new ArrayList<>();
            for (CompletedPart cp : confirmedParts) {
                sizes.add(partSizeByNumber.getOrDefault(cp.partNumber(), 0L));
            }
            long contiguous = calculateContiguous(partSizeByNumber);
            log.info("Upload paused (ASYNC) for uploadId: {}, confirmed bytes: {}", uploadId, contiguous);
            throw new UploadPausedException("Upload paused on request", uploadId, confirmedParts, sizes, contiguous);
        }
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
     * @param completedParts the list of all completed parts (previous + current run)
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
     * Calculates the total number of contiguous confirmed bytes from part 1.
     *
     * @param sizeByPartNumber map of part number to size in bytes
     * @return the sum of sizes for all parts in the contiguous prefix starting at part 1
     */
    private long calculateContiguous(Map<Integer, Long> sizeByPartNumber) {
        long total = 0L;
        int nextPart = 1;
        while (sizeByPartNumber.containsKey(nextPart)) {
            total += sizeByPartNumber.get(nextPart);
            nextPart++;
        }
        return total;
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
     * Helper record for passing upload state between async stages.
     */
    private record UploadResult(String uploadId, List<CompletedPart> completedParts) {
    }
}
