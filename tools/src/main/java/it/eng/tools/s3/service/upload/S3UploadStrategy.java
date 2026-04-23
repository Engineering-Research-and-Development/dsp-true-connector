package it.eng.tools.s3.service.upload;

import it.eng.tools.exceptions.TransferCancelledException;
import it.eng.tools.s3.model.S3ClientRequest;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Strategy interface for S3 file upload operations.
 */
public interface S3UploadStrategy {

    /**
     * Uploads a file to S3 with cancellation support and checkpoint callbacks.
     *
     * <p>Implementations MUST check {@code cancellationToken} after each part upload and,
     * if {@code true}, abort the multipart upload and throw {@link TransferCancelledException}.
     *
     * @param inputStream        the data source
     * @param s3ClientRequest    S3 connection config
     * @param bucketName         destination bucket
     * @param objectKey          destination object key
     * @param contentType        MIME type
     * @param contentDisposition content-disposition header value
     * @param cancellationToken  set to {@code true} externally to request graceful stop
     * @param checkpointCallback invoked after each successfully uploaded part
     * @return CompletableFuture resolving to the final ETag
     */
    CompletableFuture<String> uploadFile(InputStream inputStream,
                                        S3ClientRequest s3ClientRequest,
                                        String bucketName,
                                        String objectKey,
                                        String contentType,
                                        String contentDisposition,
                                        AtomicBoolean cancellationToken,
                                        UploadCheckpointCallback checkpointCallback);

    /**
     * Uploads a file to S3 without cancellation or checkpoint support.
     *
     * <p>Delegates to the 8-parameter overload with a no-op token and no-op callback.
     *
     * @param inputStream        the data source
     * @param s3ClientRequest    S3 connection config
     * @param bucketName         destination bucket
     * @param objectKey          destination object key
     * @param contentType        MIME type
     * @param contentDisposition content-disposition header value
     * @return CompletableFuture resolving to the final ETag
     */
    default CompletableFuture<String> uploadFile(InputStream inputStream,
                                                 S3ClientRequest s3ClientRequest,
                                                 String bucketName,
                                                 String objectKey,
                                                 String contentType,
                                                 String contentDisposition) {
        return uploadFile(inputStream, s3ClientRequest, bucketName, objectKey,
                contentType, contentDisposition,
                new AtomicBoolean(false), UploadCheckpointCallback.noOp());
    }
}

