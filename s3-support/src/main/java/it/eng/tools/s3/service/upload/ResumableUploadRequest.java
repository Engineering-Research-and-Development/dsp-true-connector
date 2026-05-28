package it.eng.tools.s3.service.upload;

import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries the resumable context for a multipart S3 upload.
 *
 * <p>Use {@link #noOp()} to obtain a context for a fresh, non-resumable upload
 * with no checkpoint callbacks and no suspend support.
 *
 * <p>For a resumed upload, supply the {@code uploadId} of the in-progress
 * multipart upload together with the already-completed parts and their sizes.
 * The upload strategy will skip the {@code CreateMultipartUpload} call and
 * continue uploading from the first un-uploaded part.
 *
 * @param uploadId           the ID of an existing in-progress multipart upload to resume,
 *                           or {@code null} to start a fresh upload
 * @param completedParts     parts already uploaded in a previous session
 * @param partSizes          sizes in bytes of each entry in {@code completedParts},
 *                           in the same order; must have the same size as {@code completedParts}
 * @param confirmedBytes     total bytes confirmed contiguously from part 1 in the previous session
 * @param suspendRequested   flag that upload strategies monitor; when set to {@code true}
 *                           mid-upload, the strategy aborts and throws
 *                           {@link UploadPausedException} with the current checkpoint
 * @param checkpointCallback callback notified on multipart-upload creation and on each
 *                           completed part
 */
public record ResumableUploadRequest(
        String uploadId,
        List<CompletedPart> completedParts,
        List<Long> partSizes,
        long confirmedBytes,
        AtomicBoolean suspendRequested,
        UploadCheckpointCallback checkpointCallback) {

    /**
     * Returns a no-op {@link ResumableUploadRequest} for a fresh, non-resumable upload.
     *
     * <p>The returned request has no existing uploadId, no completed parts, zero
     * confirmed bytes, suspend disabled, and a no-op checkpoint callback.
     *
     * @return a new no-op {@link ResumableUploadRequest}
     */
    public static ResumableUploadRequest noOp() {
        return new ResumableUploadRequest(
                null,
                List.of(),
                List.of(),
                0L,
                new AtomicBoolean(false),
                UploadCheckpointCallback.noop());
    }
}
