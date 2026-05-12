package it.eng.tools.s3.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.s3.encrypt.Encrypted;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "temporary_bucket_users")
@Getter
@JsonDeserialize(builder = TemporaryBucketUser.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TemporaryBucketUser {

    @Id
    private String transferProcessId;

    @JsonProperty("access_key")
    private String accessKey;

    @JsonProperty("secret_key")
    @Encrypted
    private String secretKey;

    @JsonProperty("bucket_name")
    private String bucketName;

    @JsonProperty("object_key")
    private String objectKey;

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

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private TemporaryBucketUser temporaryBucketUser;

        private Builder() {
            temporaryBucketUser = new TemporaryBucketUser();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty
        public Builder transferProcessId(String transferProcessId) {
            temporaryBucketUser.transferProcessId = transferProcessId;
            return this;
        }

        @JsonProperty("access_key")
        public Builder accessKey(String accessKey) {
            temporaryBucketUser.accessKey = accessKey;
            return this;
        }

        @JsonProperty("secret_key")
        public Builder secretKey(String secretKey) {
            temporaryBucketUser.secretKey = secretKey;
            return this;
        }

        @JsonProperty("bucket_name")
        public Builder bucketName(String bucketName) {
            temporaryBucketUser.bucketName = bucketName;
            return this;
        }

        @JsonProperty("object_key")
        public Builder objectKey(String objectKey) {
            temporaryBucketUser.objectKey = objectKey;
            return this;
        }

        public Builder issued(Instant issued) {
            temporaryBucketUser.issued = issued;
            return this;
        }

        public Builder modified(Instant modified) {
            temporaryBucketUser.modified = modified;
            return this;
        }

        public Builder createdBy(String createdBy) {
            temporaryBucketUser.createdBy = createdBy;
            return this;
        }

        public Builder lastModifiedBy(String lastModifiedBy) {
            temporaryBucketUser.lastModifiedBy = lastModifiedBy;
            return this;
        }

        @JsonProperty("version")
        public Builder version(Long version) {
            temporaryBucketUser.version = version;
            return this;
        }

        public TemporaryBucketUser build() {
            return temporaryBucketUser;
        }
    }
}

