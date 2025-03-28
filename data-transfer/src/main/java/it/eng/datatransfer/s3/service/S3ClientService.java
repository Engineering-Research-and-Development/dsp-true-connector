package it.eng.datatransfer.s3.service;

import java.time.Duration;

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
     * @param bucketName  the name of the bucket to upload to
     * @param objectKey   the key of the object to upload
     * @param data        the data to upload
     * @param contentType the content type of the data
     * @param fileName    the name of the file
     */
    void uploadFile(String bucketName, String objectKey, byte[] data, String contentType, String fileName);
    
    /**
     * Downloads a file from the specified bucket with the specified object key.
     *
     * @param bucketName the name of the bucket to download from
     * @param objectKey the key of the object to download
     * @return the downloaded data
     */
    byte[] downloadFile(String bucketName, String objectKey);
    
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
}
