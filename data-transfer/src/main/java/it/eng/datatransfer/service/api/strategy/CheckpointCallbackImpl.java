package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.model.TransferArtifactState;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.tools.s3.service.upload.UploadCheckpointCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Thread-safe {@link UploadCheckpointCallback} that holds {@link TransferArtifactState}
 * in memory and flushes to MongoDB every {@link #FLUSH_EVERY_N_PARTS} parts.
 *
 * <p>Aligns the flush cadence with {@code S3AsyncUploadStrategy.MAX_PARALLEL_PARTS} so that
 * one checkpoint write is issued per parallel upload wave, eliminating the
 * per-part read-then-write pattern and the {@code OptimisticLockingFailureException}
 * race caused by concurrent callbacks.
 *
 * <p>All public methods are {@code synchronized} to serialise concurrent calls
 * from the parallel part-upload pool.
 */
@Slf4j
class CheckpointCallbackImpl implements UploadCheckpointCallback {

    /**
     * Flush cadence aligned with {@code S3AsyncUploadStrategy.MAX_PARALLEL_PARTS}.
     * One MongoDB write is issued per parallel upload wave.
     */
    static final int FLUSH_EVERY_N_PARTS = 4;

    private final TransferArtifactStateRepository repository;
    private final long rangeStart;
    private TransferArtifactState state;
    private int partsSinceLastFlush = 0;

    /**
     * @param state      the already-loaded (or freshly created) checkpoint entity
     * @param rangeStart byte offset from which this upload session starts;
     *                   added to {@code totalBytesUploaded} to compute the absolute position
     * @param repository repository used to persist the checkpoint state
     */
    CheckpointCallbackImpl(TransferArtifactState state, long rangeStart,
                           TransferArtifactStateRepository repository) {
        this.state = state;
        this.rangeStart = rangeStart;
        this.repository = repository;
    }

    /**
     * Records the S3 multipart upload ID and flushes immediately.
     * The upload ID must be durable before any parts are uploaded so that the
     * multipart upload can be aborted on crash recovery.
     *
     * @param uploadId the S3 multipart upload ID
     */
    @Override
    public synchronized void onUploadStarted(String uploadId) {
        state.setUploadId(uploadId);
        doFlush();
    }

    /**
     * Updates the in-memory byte offset and flushes every {@link #FLUSH_EVERY_N_PARTS} parts.
     * The {@code newBytes > state.getDownloadedBytes()} guard prevents regressions caused by
     * out-of-order part completions when {@code MAX_PARALLEL_PARTS > 1}.
     *
     * @param partNumber         the 1-based part number
     * @param etag               the ETag returned by S3 for this part
     * @param totalBytesUploaded cumulative bytes uploaded in this multipart session
     */
    @Override
    public synchronized void onPartCompleted(int partNumber, String etag, long totalBytesUploaded) {
        long newBytes = rangeStart + totalBytesUploaded;
        if (newBytes > state.getDownloadedBytes()) {
            state.setDownloadedBytes(newBytes);
        }
        if (++partsSinceLastFlush >= FLUSH_EVERY_N_PARTS) {
            doFlush();
            partsSinceLastFlush = 0;
        }
    }

    /**
     * Flushes any in-memory changes that have not yet been persisted.
     * No-op if no parts have completed since the last flush (avoids a redundant
     * write when the final part landed on a periodic flush boundary).
     * Called by the transfer strategy after the upload future completes.
     */
    public synchronized void flush() {
        if (partsSinceLastFlush > 0) {
            doFlush();
            partsSinceLastFlush = 0;
        }
    }

    private void doFlush() {
        try {
            state = repository.save(state);
        } catch (OptimisticLockingFailureException e) {
            log.debug("Concurrent update to TransferArtifactState {} during checkpoint flush; retrying",
                    state.getId());
            TransferArtifactState fresh = repository.findById(state.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Checkpoint missing during flush: " + state.getId()));
            fresh.setDownloadedBytes(state.getDownloadedBytes());
            if (state.getUploadId() != null) {
                fresh.setUploadId(state.getUploadId());
            }
            state = repository.save(fresh);
        }
    }
}
