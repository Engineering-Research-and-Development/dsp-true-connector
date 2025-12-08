package it.eng.tools.s3.model;

/**
 * Enum representing the S3 upload mode.
 * ASYNC mode uses S3AsyncClient for parallel multipart uploads (faster, but may have issues with some reverse proxies).
 * SYNC mode uses S3Client for sequential multipart uploads (slower, but more compatible with reverse proxies like Caddy).
 */
public enum S3UploadMode {
    /**
     * Asynchronous upload mode using S3AsyncClient.
     * Uploads parts in parallel for better performance.
     * May have issues with Minio behind reverse proxies (e.g., Caddy).
     */
    ASYNC,

    /**
     * Synchronous upload mode using S3Client.
     * Uploads parts sequentially.
     * More compatible with reverse proxies and Minio deployments.
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
            return SYNC; // Default to SYNC for safety
        }

        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SYNC; // Default to SYNC if invalid value
        }
    }
}

