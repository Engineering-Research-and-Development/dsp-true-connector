package it.eng.tools.s3.service;

import jakarta.servlet.http.HttpServletResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for S3 client operations.
 */
public interface S3ClientService {
    
    /**
     * Creates a bucket with the specified name.
     *
     * @param bucketName the name of the bucket to create
     */
    void createBucket(String bucketName);
    
    /**
     * Deletes a bucket with the specified name.
     *
     * @param bucketName the name of the bucket to delete
     */
    void deleteBucket(String bucketName);
    
    /**
     * Checks if a bucket with the specified name exists.
     *
     * @param bucketName the name of the bucket to check
     * @return true if the bucket exists, false otherwise
     */
    boolean bucketExists(String bucketName);

    /**
     * Uploads a file to the specified bucket with the specified object key.
     *
     * @param inputStream the input stream of the file to upload
     * @param bucketName  the name of the bucket to upload to
     * @param objectKey   the key of the object to upload
     * @param contentType the content type of the file
     * @param contentDisposition the content disposition of the file
     * @return a CompletableFuture that completes with the ETag of the uploaded object
     */
    CompletableFuture<String> uploadFile(InputStream inputStream,
                                         String bucketName,
                                         String objectKey,
                                         String contentType,
                                         String contentDisposition);

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
     * @param objectKey the key of the object to delete
     */
    void deleteFile(String bucketName, String objectKey);

    /**
     * Checks if a file with the specified object key exists in the specified bucket.
     *
     * @param bucketName the name of the bucket to check
     * @param objectKey the key of the object to check
     * @return true if the file exists, false otherwise
     */
    boolean fileExists(String bucketName, String objectKey);

    /**
     * Generates a pre-signed URL for the specified object in the specified bucket.
     *
     * @param bucketName the name of the bucket
     * @param objectKey the key of the object
     * @param expiration the expiration time of the URL
     * @return the pre-signed URL
     */
    String generatePresignedUrl(String bucketName, String objectKey, Duration expiration);

    /**
     * Lists all files in the specified bucket.
     *
     * @param bucketName the name of the bucket to list files from
     * @return a list of file names
     */
    List<String> listFiles(String bucketName);
}
