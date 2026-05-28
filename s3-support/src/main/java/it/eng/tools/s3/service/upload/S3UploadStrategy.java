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
     * Uploads a file to S3 with resumable checkpoint support.
     *
     * <p>If {@link ResumableUploadRequest#uploadId()} is non-null, the existing
     * multipart upload is resumed; otherwise a new one is created.
     * The {@link UploadCheckpointCallback} is notified on upload creation and
     * on each successfully uploaded part. If
     * {@link ResumableUploadRequest#suspendRequested()} becomes {@code true}
     * mid-upload, the returned future completes exceptionally with a
     * {@link java.util.concurrent.CompletionException} whose cause is
     * {@link UploadPausedException} carrying the current checkpoint state.
     *
     * @param inputStream              the input stream to upload
     * @param s3ClientRequest          the S3 client request configuration
     * @param bucketName               the bucket name
     * @param objectKey                the object key
     * @param contentType              the content type
     * @param contentDisposition       the content disposition
     * @param resumableUploadRequest   the resumable upload context; use
     *                                 {@link ResumableUploadRequest#noOp()} for a
     *                                 fresh upload without suspend/checkpoint support
     * @return a CompletableFuture with the ETag of the completed upload; if suspend is
     *         requested before completion, the future completes exceptionally with a
     *         {@link java.util.concurrent.CompletionException} whose cause is
     *         {@link UploadPausedException}
     */
    CompletableFuture<String> uploadFile(InputStream inputStream,
                                         S3ClientRequest s3ClientRequest,
                                         String bucketName,
                                         String objectKey,
                                         String contentType,
                                         String contentDisposition,
                                         ResumableUploadRequest resumableUploadRequest);

    /**
     * Uploads a file to S3 without resumable context.
     *
     * <p>Delegates to the 7-argument overload with a no-op
     * {@link ResumableUploadRequest}. Existing callers are not affected.
     *
     * @param inputStream        the input stream to upload
     * @param s3ClientRequest    the S3 client request configuration
     * @param bucketName         the bucket name
     * @param objectKey          the object key
     * @param contentType        the content type
     * @param contentDisposition the content disposition
     * @return a CompletableFuture with the ETag of the completed upload
     */
    default CompletableFuture<String> uploadFile(InputStream inputStream,
                                                  S3ClientRequest s3ClientRequest,
                                                  String bucketName,
                                                  String objectKey,
                                                  String contentType,
                                                  String contentDisposition) {
        return uploadFile(inputStream, s3ClientRequest, bucketName, objectKey,
                contentType, contentDisposition, ResumableUploadRequest.noOp());
    }
}
