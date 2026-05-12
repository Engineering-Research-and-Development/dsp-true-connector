package it.eng.dataplane.s3.util;

/**
 * Constants for S3 properties map keys used in upload operations.
 */
public interface S3Utils {
    /** Key for the S3 bucket name. */
    String BUCKET_NAME = "bucketName";
    /** Key for the AWS region. */
    String REGION = "region";
    /** Key for the S3 object key. */
    String OBJECT_KEY = "objectKey";
    /** Key for the S3 access key. */
    String ACCESS_KEY = "accessKey";
    /** Key for the S3 secret key. */
    String SECRET_KEY = "secretKey";
    /** Key for the S3 endpoint override URL. */
    String ENDPOINT_OVERRIDE = "endpointOverride";
}
