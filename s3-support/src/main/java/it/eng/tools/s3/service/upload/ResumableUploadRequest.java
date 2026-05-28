package it.eng.tools.s3.service.upload;

import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
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
 * <h3>Relationship between {@code confirmedBytes}, {@code completedParts} and {@code partSizes}</h3>
 * <ul>
 *   <li>{@code completedParts} and {@code partSizes} must have the same length; entry {@code i}
 *       of {@code partSizes} is the byte count of {@code completedParts[i]}.</li>
 *   <li>{@code confirmedBytes} must equal the total of the <em>contiguous prefix</em>
 *       of {@code partSizes} starting from part&nbsp;1.  When {@code completedParts} is
 *       empty, {@code confirmedBytes} must be&nbsp;0.</li>
 *   <li>Upload strategies use {@code confirmedBytes} as a floor for the contiguous byte
 *       count reported to the {@link UploadCheckpointCallback}: even if the reconstructed
 *       contiguous count from {@code partSizes} is lower (e.g. due to gaps introduced by
 *       async out-of-order completion), the reported value never drops below
 *       {@code confirmedBytes}.</li>
 * </ul>
 *
 * <h3>Suspend / pause behaviour</h3>
 * <p>When {@code suspendRequested} is set to {@code true} mid-upload, the strategy stops
 * uploading new parts and raises an {@link UploadPausedException} carrying the current
 * checkpoint.  Because upload strategies return a {@link java.util.concurrent.CompletableFuture},
 * the exception is surfaced as an <em>exceptional completion</em>: callers should expect a
 * {@link CompletionException} (or {@link ExecutionException} when using
 * {@link java.util.concurrent.Future#get()}) whose {@linkplain Throwable#getCause() cause}
 * is the {@link UploadPausedException}.
 *
 * @param uploadId           the ID of an existing in-progress multipart upload to resume,
 *                           or {@code null} to start a fresh upload
 * @param completedParts     parts already uploaded in a previous session; must have the
 *                           same length as {@code partSizes}
 * @param partSizes          sizes in bytes of each entry in {@code completedParts},
 *                           in the same order; must have the same size as {@code completedParts}
 * @param confirmedBytes     total bytes confirmed contiguously from part&nbsp;1 in the
 *                           previous session; must be&nbsp;0 when {@code completedParts}
 *                           is empty, and non-negative in all cases
 * @param suspendRequested   flag that upload strategies monitor; when set to {@code true}
 *                           mid-upload, the strategy stops and the returned future completes
 *                           exceptionally with a {@link CompletionException} wrapping an
 *                           {@link UploadPausedException} that carries the current checkpoint
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
     * Compact constructor that validates field consistency.
     *
     * @throws NullPointerException     if any required field is {@code null}
     * @throws IllegalArgumentException if {@code completedParts} and {@code partSizes} differ in
     *                                  length, if {@code confirmedBytes} is negative, or if
     *                                  {@code confirmedBytes} is non-zero while {@code completedParts}
     *                                  is empty
     */
    public ResumableUploadRequest {
        Objects.requireNonNull(completedParts, "completedParts must not be null");
        Objects.requireNonNull(partSizes, "partSizes must not be null");
        Objects.requireNonNull(suspendRequested, "suspendRequested must not be null");
        Objects.requireNonNull(checkpointCallback, "checkpointCallback must not be null");
        if (completedParts.size() != partSizes.size()) {
            throw new IllegalArgumentException(
                    "completedParts.size() (" + completedParts.size()
                    + ") must equal partSizes.size() (" + partSizes.size() + ")");
        }
        if (confirmedBytes < 0) {
            throw new IllegalArgumentException(
                    "confirmedBytes must be >= 0, was: " + confirmedBytes);
        }
        if (completedParts.isEmpty() && confirmedBytes != 0) {
            throw new IllegalArgumentException(
                    "confirmedBytes must be 0 when completedParts is empty, was: " + confirmedBytes);
        }
        completedParts = List.copyOf(completedParts);
        partSizes = List.copyOf(partSizes);
    }

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
