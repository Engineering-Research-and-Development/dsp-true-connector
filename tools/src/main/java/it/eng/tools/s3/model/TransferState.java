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
@JsonDeserialize(builder = TransferState.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferState {

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
        private final TransferState transferState;

        private Builder() {
            transferState = new TransferState();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            transferState.id = id;
            return this;
        }

        public Builder uploadId(String uploadId) {
            transferState.uploadId = uploadId;
            return this;
        }

        public Builder downloadedBytes(long downloadedBytes) {
            transferState.downloadedBytes = downloadedBytes;
            return this;
        }

        public Builder partNumber(int partNumber) {
            transferState.partNumber = partNumber;
            return this;
        }

        public Builder etags(List<String> etags) {
            transferState.etags = etags;
            return this;
        }

        public Builder presignURL(String presignURL) {
            transferState.presignURL = presignURL;
            return this;
        }

        public Builder destBucket(String destBucket) {
            transferState.destBucket = destBucket;
            return this;
        }

        public Builder destObject(String destObject) {
            transferState.destObject = destObject;
            return this;
        }

        public Builder issued(Instant issued) {
            transferState.issued = issued;
            return this;
        }

        public Builder modified(Instant modified) {
            transferState.modified = modified;
            return this;
        }

        public Builder createdBy(String createdBy) {
            transferState.createdBy = createdBy;
            return this;
        }

        public Builder lastModifiedBy(String lastModifiedBy) {
            transferState.lastModifiedBy = lastModifiedBy;
            return this;
        }

        @JsonProperty("version")
        public Builder version(Long version) {
            transferState.version = version;
            return this;
        }

        public TransferState build() {
            return transferState;
        }
    }
}
