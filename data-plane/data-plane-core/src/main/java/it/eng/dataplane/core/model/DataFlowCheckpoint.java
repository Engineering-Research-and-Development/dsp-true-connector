package it.eng.dataplane.core.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document that records the resumable state of an in-progress multipart data transfer.
 *
 * <p>A checkpoint is created when a multipart upload begins and updated as each part completes.
 * On Data Plane restart, {@code DataFlowRecoveryStartupBean} uses checkpoints to decide whether
 * an interrupted transfer can be safely resumed (SUSPENDED) rather than terminated.</p>
 *
 * <p>The document ID is the {@code processId} — one checkpoint per transfer process.</p>
 *
 * <p>Immutable — use {@link Builder#newInstance()} to construct and
 * {@code withXxx()} methods to produce updated copies.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document("data_flow_checkpoints")
public class DataFlowCheckpoint {

    @Id
    private String processId;

    private String dataFlowId;
    private String transferType;
    private String tenantId;
    private String uploadId;
    private String destinationBucket;
    private String destinationObjectKey;

    /** Ordered list of completed part numbers (1-based). */
    private List<Integer> completedParts;

    /** Map of part number to part size in bytes. */
    private Map<Integer, Long> partSizes;

    /** Map of part number to the ETag returned by S3 for that part. Required for multipart resume. */
    private Map<Integer, String> partETags;

    /** Total contiguous bytes confirmed uploaded so far (from part 1 through the last uninterrupted part). */
    private long confirmedBytes;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Returns a new {@link DataFlowCheckpoint} with the given multipart upload ID set.
     *
     * @param newUploadId the S3 multipart upload ID returned by {@code CreateMultipartUpload}
     * @return updated copy
     */
    public DataFlowCheckpoint withUploadId(String newUploadId) {
        DataFlowCheckpoint copy = copyOf(this);
        copy.uploadId = newUploadId;
        copy.updatedAt = Instant.now();
        return copy;
    }

    /**
     * Returns a new {@link DataFlowCheckpoint} with the given part appended to the completed list,
     * along with its ETag for multipart resume and the updated confirmed byte count.
     *
     * <p>The {@code eTag} is required by S3 / MinIO when calling {@code CompleteMultipartUpload}
     * after a resume. The {@code confirmedBytes} value should be the contiguous byte count
     * reported by the {@link it.eng.tools.s3.service.upload.UploadCheckpointCallback}; it is
     * stored directly rather than re-derived from part sizes.</p>
     *
     * @param partNumber      the 1-based part number
     * @param partSize        the size of the part in bytes
     * @param eTag            the ETag returned by S3 for this part
     * @param confirmedBytes  total contiguous bytes confirmed from part 1 through the latest
     *                        uninterrupted sequence at the time this part completed
     * @return updated copy with part appended
     */
    public DataFlowCheckpoint withCompletedPart(int partNumber, long partSize, String eTag, long confirmedBytes) {
        DataFlowCheckpoint copy = copyOf(this);
        List<Integer> parts = new ArrayList<>(copy.completedParts == null ? List.of() : copy.completedParts);
        parts.add(partNumber);
        copy.completedParts = List.copyOf(parts);
        Map<Integer, Long> sizes = new HashMap<>(copy.partSizes == null ? Map.of() : copy.partSizes);
        sizes.put(partNumber, partSize);
        copy.partSizes = Map.copyOf(sizes);
        Map<Integer, String> etags = new HashMap<>(copy.partETags == null ? Map.of() : copy.partETags);
        etags.put(partNumber, eTag);
        copy.partETags = Map.copyOf(etags);
        copy.confirmedBytes = confirmedBytes;
        copy.updatedAt = Instant.now();
        return copy;
    }

    private static DataFlowCheckpoint copyOf(DataFlowCheckpoint source) {
        DataFlowCheckpoint copy = new DataFlowCheckpoint();
        copy.processId = source.processId;
        copy.dataFlowId = source.dataFlowId;
        copy.transferType = source.transferType;
        copy.tenantId = source.tenantId;
        copy.uploadId = source.uploadId;
        copy.destinationBucket = source.destinationBucket;
        copy.destinationObjectKey = source.destinationObjectKey;
        copy.completedParts = source.completedParts;
        copy.partSizes = source.partSizes;
        copy.partETags = source.partETags;
        copy.confirmedBytes = source.confirmedBytes;
        copy.createdAt = source.createdAt;
        copy.updatedAt = source.updatedAt;
        return copy;
    }

    /** Builder for {@link DataFlowCheckpoint}. */
    public static class Builder {

        private final DataFlowCheckpoint instance = new DataFlowCheckpoint();

        private Builder() {
        }

        /**
         * Creates a new {@link Builder} instance.
         *
         * @return new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets the transfer process ID (also used as document ID).
         *
         * @param processId the DSP transfer process ID
         * @return this builder
         */
        public Builder processId(String processId) {
            instance.processId = processId;
            return this;
        }

        /**
         * Sets the data flow ID.
         *
         * @param dataFlowId the data flow identifier
         * @return this builder
         */
        public Builder dataFlowId(String dataFlowId) {
            instance.dataFlowId = dataFlowId;
            return this;
        }

        /**
         * Sets the transfer type.
         *
         * @param transferType the transfer protocol identifier (e.g. {@code HttpData-PULL})
         * @return this builder
         */
        public Builder transferType(String transferType) {
            instance.transferType = transferType;
            return this;
        }

        /**
         * Sets the tenant ID.
         *
         * @param tenantId the tenant that owns this data flow
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            instance.tenantId = tenantId;
            return this;
        }

        /**
         * Sets the S3 multipart upload ID.
         *
         * @param uploadId the upload ID returned by {@code CreateMultipartUpload}
         * @return this builder
         */
        public Builder uploadId(String uploadId) {
            instance.uploadId = uploadId;
            return this;
        }

        /**
         * Sets the destination bucket name.
         *
         * @param destinationBucket the S3 bucket for the upload
         * @return this builder
         */
        public Builder destinationBucket(String destinationBucket) {
            instance.destinationBucket = destinationBucket;
            return this;
        }

        /**
         * Sets the destination object key.
         *
         * @param destinationObjectKey the S3 object key for the upload
         * @return this builder
         */
        public Builder destinationObjectKey(String destinationObjectKey) {
            instance.destinationObjectKey = destinationObjectKey;
            return this;
        }

        /**
         * Sets the list of completed part numbers.
         *
         * @param completedParts ordered list of 1-based part numbers that have been uploaded
         * @return this builder
         */
        public Builder completedParts(List<Integer> completedParts) {
            instance.completedParts = completedParts;
            return this;
        }

        /**
         * Sets the part sizes map.
         *
         * @param partSizes map of part number to size in bytes
         * @return this builder
         */
        public Builder partSizes(Map<Integer, Long> partSizes) {
            instance.partSizes = partSizes;
            return this;
        }

        /**
         * Sets the part ETags map used for multipart resume.
         *
         * @param partETags map of part number to the ETag returned by S3 for that part
         * @return this builder
         */
        public Builder partETags(Map<Integer, String> partETags) {
            instance.partETags = partETags;
            return this;
        }

        /**
         * Sets the number of bytes confirmed uploaded.
         *
         * @param confirmedBytes bytes confirmed as successfully uploaded
         * @return this builder
         */
        public Builder confirmedBytes(long confirmedBytes) {
            instance.confirmedBytes = confirmedBytes;
            return this;
        }

        /**
         * Sets the creation timestamp.
         *
         * @param createdAt creation instant
         * @return this builder
         */
        public Builder createdAt(Instant createdAt) {
            instance.createdAt = createdAt;
            return this;
        }

        /**
         * Sets the last-updated timestamp.
         *
         * @param updatedAt last-updated instant
         * @return this builder
         */
        public Builder updatedAt(Instant updatedAt) {
            instance.updatedAt = updatedAt;
            return this;
        }

        /**
         * Builds the {@link DataFlowCheckpoint}.
         *
         * @return the constructed checkpoint
         */
        public DataFlowCheckpoint build() {
            return instance;
        }
    }
}
