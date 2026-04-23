package it.eng.tools.s3.service;

import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import jakarta.servlet.http.HttpServletResponse;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service interface for S3 client operations.
 */
public interface S3ClientService {

    /**
     * Uploads a file without cancellation or checkpoint support.
     *
     * <p>Delegates to the 6-parameter overload with a no-op token and callback.
     * The {@code inputStream} is closed automatically after the upload completes.
     *
     * @param inputStream             data source
     * @param destinationS3Properties destination bucket properties
     * @param contentType             MIME type
     * @param contentDisposition      content-disposition header value
     * @return CompletableFuture resolving to the final ETag
     */
    default CompletableFuture<String> uploadFile(InputStream inputStream,
                                                  Map<String, String> destinationS3Properties,
                                                  String contentType,
                                                  String contentDisposition) {
        return uploadFile(inputStream, destinationS3Properties, contentType, contentDisposition,
                new AtomicBoolean(false),
                UploadCheckpointCallback.noOp());
    }

    /**
     * Uploads a file with cancellation support and checkpoint callbacks.
     *
     * <p>The {@code inputStream} is closed automatically after the upload completes.
     *
     * @param inputStream             data source
     * @param destinationS3Properties destination bucket properties
     * @param contentType             MIME type
     * @param contentDisposition      content-disposition header value
     * @param cancellationToken       set to {@code true} to request graceful stop
     * @param checkpointCallback      invoked after each successfully uploaded part
     * @return CompletableFuture resolving to the final ETag
     * @throws it.eng.tools.exceptions.TransferCancelledException if {@code cancellationToken}
     *         is set to {@code true} before or during the upload
     */
    CompletableFuture<String> uploadFile(InputStream inputStream,
                                         Map<String, String> destinationS3Properties,
                                         String contentType,
                                         String contentDisposition,
                                         AtomicBoolean cancellationToken,
                                         UploadCheckpointCallback checkpointCallback);

    /**
     * Downloads a file from the specified bucket with the specified object key.
     *
     * @param bucketName the name of the bucket to download from
     * @param objectKey  the key of the object to download
     * @param response   the HttpServletResponse to write the downloaded data to
     */
    void downloadFile(String bucketName, String objectKey, HttpServletResponse response);

    /**
     * Deletes a file from the specified bucket with the specified object key.
     *
     * @param bucketName the name of the bucket to delete from
     * @param objectKey  the key of the object to delete
     */
    void deleteFile(String bucketName, String objectKey);

    /**
     * Checks if a file with the specified object key exists in the specified bucket.
     *
     * @param bucketName the name of the bucket to check
     * @param objectKey  the key of the object to check
     * @return true if the file exists, false otherwise
     */
    boolean fileExists(String bucketName, String objectKey);

    /**
     * Generates a pre-signed URL for the specified object in the specified bucket.
     *
     * @param bucketName the name of the bucket
     * @param objectKey  the key of the object
     * @param expiration the expiration time of the URL
     * @return the pre-signed URL
     */
    String generateGetPresignedUrl(String bucketName, String objectKey, Duration expiration);

    /**
     * Lists all files in the specified bucket.
     *
     * @param bucketName the name of the bucket to list files from
     * @return a list of file names
     */
    List<String> listFiles(String bucketName);
}
