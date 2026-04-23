package it.eng.tools.s3.service.upload;

/**
 * Callback invoked by S3 upload strategies to record progress for crash-safe pause/resume checkpointing.
 *
 * <p>{@link #onUploadStarted} is called once the multipart upload has been created on S3.
 * {@link #onPartCompleted} is called after every successfully uploaded part so the caller
 * can persist the current byte offset before continuing.
 */
public interface UploadCheckpointCallback {

    /**
     * Called once the multipart upload has been initiated on S3.
     *
     * @param uploadId the S3 multipart upload ID
     */
    void onUploadStarted(String uploadId);

    /**
     * Called after a part has been successfully uploaded.
     *
     * @param partNumber          the 1-based part number within the current multipart upload
     * @param etag                the ETag returned by S3 for this part
     * @param totalBytesUploaded  cumulative bytes uploaded in this multipart upload so far
     */
    void onPartCompleted(int partNumber, String etag, long totalBytesUploaded);

    /**
     * Returns a no-op callback suitable for callers that do not need checkpointing.
     *
     * @return a no-op {@code UploadCheckpointCallback}
     */
    static UploadCheckpointCallback noOp() {
        return new UploadCheckpointCallback() {
            @Override
            public void onUploadStarted(String uploadId) { /* no-op */ }

            @Override
            public void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) { /* no-op */ }
        };
    }
}
