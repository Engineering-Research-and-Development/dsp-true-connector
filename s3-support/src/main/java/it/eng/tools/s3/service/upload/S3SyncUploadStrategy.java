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
    private final S3Properties s3Properties;

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
                byte[] buffer = new byte[s3Properties.getChunkSize()];

                while (true) {
                    int totalRead = readFully(inputStream, buffer);
                    if (totalRead == 0) break;

                    // Reuse the same buffer when full; copy only the final (smaller) part
                    byte[] partData = (totalRead == buffer.length)
                            ? buffer
                            : Arrays.copyOf(buffer, totalRead);

                    CompletedPart part = uploadPart(s3Client, bucketName, objectKey, uploadId, partNumber, partData);
                    completedParts.add(part);
                    partNumber++;
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
                software.amazon.awssdk.core.sync.RequestBody.fromInputStream(
                        new ByteArrayInputStream(partData), partData.length));

        log.debug("Part {} uploaded successfully (SYNC) with ETag: {}", partNumber, response.eTag());

        return CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(response.eTag())
                .build();
    }

    /**
     * Reads bytes from the stream until the buffer is full or the stream is exhausted.
     * Avoids the extra copy introduced by ByteArrayOutputStream.toByteArray().
     *
     * @param in  the input stream
     * @param buf the buffer to fill
     * @return the number of bytes actually read; 0 means the stream is exhausted
     */
    private int readFully(InputStream in, byte[] buf) throws IOException {
        int offset = 0, read;
        while (offset < buf.length && (read = in.read(buf, offset, buf.length - offset)) != -1) {
            offset += read;
        }
        return offset;
    }
}

