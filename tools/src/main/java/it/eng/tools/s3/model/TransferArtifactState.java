package it.eng.tools.s3.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "transfer_states")
@Getter
@Setter
@JsonDeserialize(builder = TransferArtifactState.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferArtifactState {

    @Id
    private String id;
    private String uploadId;
    private long downloadedBytes;
    private int partNumber;
    private List<String> etags = new ArrayList<>();
    private String presignURL;
    private String destBucket;
    private String destObject;

    @CreatedDate
    private Instant issued;

    @LastModifiedDate
    private Instant modified;

    @JsonIgnore
    @CreatedBy
    private String createdBy;

    @JsonIgnore
    @LastModifiedBy
    private String lastModifiedBy;

    @JsonIgnore
    @Version
    @Field("version")
    private Long version;

    public int incrementPartNumber() {
        partNumber++;
        return partNumber;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final TransferArtifactState transferArtifactState;

        private Builder() {
            transferArtifactState = new TransferArtifactState();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            transferArtifactState.id = id;
            return this;
        }

        public Builder uploadId(String uploadId) {
            transferArtifactState.uploadId = uploadId;
            return this;
        }

        public Builder downloadedBytes(long downloadedBytes) {
            transferArtifactState.downloadedBytes = downloadedBytes;
            return this;
        }

        public Builder partNumber(int partNumber) {
            transferArtifactState.partNumber = partNumber;
            return this;
        }

        public Builder etags(List<String> etags) {
            transferArtifactState.etags = etags;
            return this;
        }

        public Builder presignURL(String presignURL) {
            transferArtifactState.presignURL = presignURL;
            return this;
        }

        public Builder destBucket(String destBucket) {
            transferArtifactState.destBucket = destBucket;
            return this;
        }

        public Builder destObject(String destObject) {
            transferArtifactState.destObject = destObject;
            return this;
        }

        public Builder issued(Instant issued) {
            transferArtifactState.issued = issued;
            return this;
        }

        public Builder modified(Instant modified) {
            transferArtifactState.modified = modified;
            return this;
        }

        public Builder createdBy(String createdBy) {
            transferArtifactState.createdBy = createdBy;
            return this;
        }

        public Builder lastModifiedBy(String lastModifiedBy) {
            transferArtifactState.lastModifiedBy = lastModifiedBy;
            return this;
        }

        @JsonProperty("version")
        public Builder version(Long version) {
            transferArtifactState.version = version;
            return this;
        }

        public TransferArtifactState build() {
            return transferArtifactState;
        }
    }
}
