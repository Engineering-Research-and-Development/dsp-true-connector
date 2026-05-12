package it.eng.dataplane.s3.model;

/**
 * Enum representing the S3 upload mode.
 * ASYNC mode uses S3AsyncClient for parallel multipart uploads.
 * SYNC mode uses S3Client for sequential multipart uploads.
 */
public enum S3UploadMode {
    /**
     * Asynchronous upload mode using S3AsyncClient.
     */
    ASYNC,

    /**
     * Synchronous upload mode using S3Client.
     */
    SYNC;

    /**
     * Parse the upload mode from a string value.
     * Defaults to SYNC if the value is null, empty, or invalid.
     *
     * @param value the string value to parse
     * @return the corresponding S3UploadMode
     */
    public static S3UploadMode fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return SYNC;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SYNC;
        }
    }
}
