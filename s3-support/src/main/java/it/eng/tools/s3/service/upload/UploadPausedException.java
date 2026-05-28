package it.eng.tools.s3.service.upload;

import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;

/**
 * Thrown when an upload is paused due to a suspend request.
 *
 * <p>Carries the checkpoint state needed to resume the upload later:
 * the multipart upload ID, all parts completed so far, their sizes,
 * and the number of contiguous confirmed bytes from part 1.
 */
public class UploadPausedException extends RuntimeException {

    private final String uploadId;
    private final List<CompletedPart> completedParts;
    private final List<Long> partSizes;
    private final long confirmedBytes;

    /**
     * Constructs an {@link UploadPausedException} with checkpoint state.
     *
     * @param message        human-readable description of the pause reason
     * @param uploadId       the multipart upload ID of the paused upload
     * @param completedParts all parts that have been successfully uploaded
     * @param partSizes      sizes in bytes of each entry in {@code completedParts},
     *                       in the same order
     * @param confirmedBytes the total number of contiguous bytes confirmed from part 1
     */
    public UploadPausedException(String message,
                                 String uploadId,
                                 List<CompletedPart> completedParts,
                                 List<Long> partSizes,
                                 long confirmedBytes) {
        super(message);
        this.uploadId = uploadId;
        this.completedParts = List.copyOf(completedParts);
        this.partSizes = List.copyOf(partSizes);
        this.confirmedBytes = confirmedBytes;
    }

    /**
     * Returns the multipart upload ID for the paused upload.
     *
     * @return the multipart upload ID
     */
    public String getUploadId() {
        return uploadId;
    }

    /**
     * Returns all parts that completed before the upload was paused.
     *
     * @return an unmodifiable list of completed parts
     */
    public List<CompletedPart> getCompletedParts() {
        return completedParts;
    }

    /**
     * Returns the sizes of the completed parts in the same order as {@link #getCompletedParts()}.
     *
     * @return an unmodifiable list of part sizes in bytes
     */
    public List<Long> getPartSizes() {
        return partSizes;
    }

    /**
     * Returns the number of bytes confirmed contiguously from part 1 at the time of the pause.
     *
     * @return the contiguous confirmed byte count
     */
    public long getConfirmedBytes() {
        return confirmedBytes;
    }
}
