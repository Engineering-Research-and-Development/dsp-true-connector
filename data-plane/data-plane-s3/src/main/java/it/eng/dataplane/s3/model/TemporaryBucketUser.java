package it.eng.dataplane.s3.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.dataplane.s3.encrypt.Encrypted;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * MongoDB document storing short-lived S3 credentials for HTTP-PUSH transfers.
 * Keyed by the transfer process ID.
 */
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

    /** Builder for {@link TemporaryBucketUser}. */
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private TemporaryBucketUser temporaryBucketUser;

        private Builder() {
            temporaryBucketUser = new TemporaryBucketUser();
        }

        /**
         * Creates a new builder instance.
         *
         * @return new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets the transfer process ID.
         *
         * @param transferProcessId the transfer process ID
         * @return this builder
         */
        @JsonProperty
        public Builder transferProcessId(String transferProcessId) {
            temporaryBucketUser.transferProcessId = transferProcessId;
            return this;
        }

        /**
         * Sets the access key.
         *
         * @param accessKey the access key
         * @return this builder
         */
        @JsonProperty("access_key")
        public Builder accessKey(String accessKey) {
            temporaryBucketUser.accessKey = accessKey;
            return this;
        }

        /**
         * Sets the secret key.
         *
         * @param secretKey the secret key
         * @return this builder
         */
        @JsonProperty("secret_key")
        public Builder secretKey(String secretKey) {
            temporaryBucketUser.secretKey = secretKey;
            return this;
        }

        /**
         * Sets the bucket name.
         *
         * @param bucketName the bucket name
         * @return this builder
         */
        @JsonProperty("bucket_name")
        public Builder bucketName(String bucketName) {
            temporaryBucketUser.bucketName = bucketName;
            return this;
        }

        /**
         * Sets the object key.
         *
         * @param objectKey the object key
         * @return this builder
         */
        @JsonProperty("object_key")
        public Builder objectKey(String objectKey) {
            temporaryBucketUser.objectKey = objectKey;
            return this;
        }

        /**
         * Sets the issued timestamp.
         *
         * @param issued the issued timestamp
         * @return this builder
         */
        public Builder issued(Instant issued) {
            temporaryBucketUser.issued = issued;
            return this;
        }

        /**
         * Sets the modified timestamp.
         *
         * @param modified the modified timestamp
         * @return this builder
         */
        public Builder modified(Instant modified) {
            temporaryBucketUser.modified = modified;
            return this;
        }

        /**
         * Sets the created-by principal.
         *
         * @param createdBy the creator identifier
         * @return this builder
         */
        public Builder createdBy(String createdBy) {
            temporaryBucketUser.createdBy = createdBy;
            return this;
        }

        /**
         * Sets the last-modified-by principal.
         *
         * @param lastModifiedBy the modifier identifier
         * @return this builder
         */
        public Builder lastModifiedBy(String lastModifiedBy) {
            temporaryBucketUser.lastModifiedBy = lastModifiedBy;
            return this;
        }

        /**
         * Sets the document version.
         *
         * @param version the version number
         * @return this builder
         */
        @JsonProperty("version")
        public Builder version(Long version) {
            temporaryBucketUser.version = version;
            return this;
        }

        /**
         * Builds the entity.
         *
         * @return the built entity
         */
        public TemporaryBucketUser build() {
            return temporaryBucketUser;
        }
    }
}
