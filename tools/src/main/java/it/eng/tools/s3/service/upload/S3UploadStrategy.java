package it.eng.tools.s3.service.upload;

import it.eng.tools.s3.model.S3ClientRequest;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy interface for S3 file upload operations.
 * Implementations provide different upload strategies (sync, async).
 */
public interface S3UploadStrategy {

    /**
     * Chunk size for multipart uploads (50MB).
     */
    int CHUNK_SIZE = 50 * 1024 * 1024; // 50MB chunks

    /**
     * Uploads a file to S3 using the specific strategy implementation.
     *
     * @param inputStream        the input stream to upload
     * @param s3ClientRequest    the S3 client request configuration
     * @param bucketName         the bucket name
     * @param objectKey          the object key
     * @param contentType        the content type
     * @param contentDisposition the content disposition
     * @return a CompletableFuture with the ETag
     */
    CompletableFuture<String> uploadFile(InputStream inputStream,
                                        S3ClientRequest s3ClientRequest,
                                        String bucketName,
                                        String objectKey,
                                        String contentType,
                                        String contentDisposition);
}

