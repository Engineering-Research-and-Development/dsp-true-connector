package it.eng.tools.s3.service.upload;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final S3Properties s3Properties;

    /**
     * Constructs an {@link S3SyncUploadStrategy}.
     *
     * @param s3ClientProvider provider for S3 client instances
     * @param s3Properties     S3 configuration properties
     */
    public S3SyncUploadStrategy(S3ClientProvider s3ClientProvider, S3Properties s3Properties) {
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
        return CompletableFuture.supplyAsync(() -> {
            S3Client s3Client = s3ClientProvider.s3Client(s3ClientRequest);
            UploadCheckpointCallback callback = resumable.checkpointCallback();

            // Initialize part-size map from previously completed parts
            Map<Integer, Long> partSizeByNumber = new HashMap<>();
            for (int i = 0; i < resumable.completedParts().size(); i++) {
                partSizeByNumber.put(
                        resumable.completedParts().get(i).partNumber(),
                        resumable.partSizes().get(i));
            }

            try {
                // Determine upload ID: reuse existing or create new
                String uploadId;
                if (resumable.uploadId() != null && !resumable.uploadId().isBlank()) {
                    uploadId = resumable.uploadId();
                    log.info("Reusing existing multipart upload (SYNC) for key: {} with uploadId: {}", objectKey, uploadId);
                } else {
                    log.info("Creating multipart upload (SYNC) for key: {}", objectKey);
                    CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                            .bucket(bucketName)
                            .contentType(contentType)
                            .contentDisposition(contentDisposition)
                            .key(objectKey)
                            .build();
                    CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
                    uploadId = createResponse.uploadId();
                    log.info("Created multipart upload (SYNC) for key: {} with uploadId: {}", objectKey, uploadId);
                    callback.onMultipartCreated(uploadId);
                }

                // Collect all completed parts: pre-existing + newly uploaded
                List<CompletedPart> allCompletedParts = new ArrayList<>(resumable.completedParts());
                int partNumber = allCompletedParts.size() + 1;
                byte[] buffer = new byte[s3Properties.getChunkSize()];

                while (true) {
                    if (resumable.suspendRequested().get()) {
                        buildPauseException(uploadId, allCompletedParts, partSizeByNumber);
                    }

                    int totalRead = readFully(inputStream, buffer);
                    if (totalRead == 0) break;

                    byte[] partData = (totalRead == buffer.length)
                            ? buffer
                            : Arrays.copyOf(buffer, totalRead);

                    CompletedPart part = uploadPart(s3Client, bucketName, objectKey, uploadId, partNumber, partData);
                    allCompletedParts.add(part);

                    partSizeByNumber.put(partNumber, (long) totalRead);
                    long contiguous = calculateContiguous(partSizeByNumber);
                    callback.onPartCompleted(partNumber, part.eTag(), totalRead, contiguous);

                    partNumber++;
                }

                log.info("All {} parts uploaded successfully (SYNC) for key: {}", allCompletedParts.size(), objectKey);

                CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                        .parts(allCompletedParts)
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

            } catch (UploadPausedException e) {
                throw e;
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
     * Builds and throws an {@link UploadPausedException} with the current checkpoint state.
     *
     * @param uploadId         the current multipart upload ID
     * @param completedParts   the parts completed so far
     * @param partSizeByNumber map of part number to size in bytes
     */
    private void buildPauseException(String uploadId,
                                     List<CompletedPart> completedParts,
                                     Map<Integer, Long> partSizeByNumber) {
        List<Long> sizes = new ArrayList<>();
        for (CompletedPart cp : completedParts) {
            sizes.add(partSizeByNumber.getOrDefault(cp.partNumber(), 0L));
        }
        long contiguous = calculateContiguous(partSizeByNumber);
        log.info("Upload paused (SYNC) for uploadId: {}, confirmed bytes: {}", uploadId, contiguous);
        throw new UploadPausedException("Upload paused on request", uploadId, completedParts, sizes, contiguous);
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
                software.amazon.awssdk.core.sync.RequestBody.fromInputStream(
                        new ByteArrayInputStream(partData), partData.length));

        log.debug("Part {} uploaded successfully (SYNC) with ETag: {}", partNumber, response.eTag());

        return CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(response.eTag())
                .build();
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
     * Avoids the extra copy introduced by ByteArrayOutputStream.toByteArray().
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
}

