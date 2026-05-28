package it.eng.tools.s3.service.upload;

/**
 * Callback interface for tracking multipart upload progress and checkpoints.
 *
 * <p>Implementations receive notifications when a multipart upload is created
 * and when each part completes, along with the count of contiguous confirmed bytes
 * from the beginning of the upload. This supports suspend/resume functionality
 * by allowing callers to persist the upload state at each checkpoint.
 */
public interface UploadCheckpointCallback {

    /**
     * Called when a new multipart upload has been created with the S3 backend.
     *
     * @param uploadId the upload ID assigned by S3/MinIO for the new multipart upload
     */
    void onMultipartCreated(String uploadId);

    /**
     * Called when a single part has been successfully uploaded.
     *
     * <p>{@code contiguousConfirmedBytes} reflects the total bytes confirmed from
     * part 1 through the highest uninterrupted sequence of completed parts.
     * For example, if parts 1 and 3 are done but part 2 is not, only the bytes
     * for part 1 are reported as contiguous.
     *
     * @param partNumber               the 1-based part number that just completed
     * @param eTag                     the ETag returned by S3 for the completed part
     * @param partSize                 the size in bytes of the completed part
     * @param contiguousConfirmedBytes the total number of bytes confirmed contiguously
     *                                 from part 1 through all currently-completed
     *                                 sequential parts
     */
    void onPartCompleted(int partNumber, String eTag, long partSize, long contiguousConfirmedBytes);

    /**
     * Returns a no-op implementation that ignores all callback events.
     *
     * @return a stateless, thread-safe no-op {@link UploadCheckpointCallback}
     */
    static UploadCheckpointCallback noop() {
        return new UploadCheckpointCallback() {
            @Override
            public void onMultipartCreated(String uploadId) {
            }

            @Override
            public void onPartCompleted(int partNumber, String eTag, long partSize, long contiguousConfirmedBytes) {
            }
        };
    }
}
