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
 * MongoDB document storing per-bucket S3 credentials.
 * The {@code secretKey} field is encrypted at rest via the service layer.
 */
@Document(collection = "bucket_credentials")
@Getter
@JsonDeserialize(builder = BucketCredentialsEntity.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BucketCredentialsEntity {

    @Id
    private String bucketName;

    @JsonProperty("access_key")
    private String accessKey;

    @JsonProperty("secret_key")
    @Encrypted
    private String secretKey;

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

    /** Builder for {@link BucketCredentialsEntity}. */
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private final BucketCredentialsEntity bucketCredentials;

        private Builder() {
            bucketCredentials = new BucketCredentialsEntity();
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
         * Sets the bucket name.
         *
         * @param bucketName the bucket name
         * @return this builder
         */
        @JsonProperty
        public Builder bucketName(String bucketName) {
            bucketCredentials.bucketName = bucketName;
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
            bucketCredentials.accessKey = accessKey;
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
            bucketCredentials.secretKey = secretKey;
            return this;
        }

        /**
         * Sets the issued timestamp.
         *
         * @param issued the issued timestamp
         * @return this builder
         */
        public Builder issued(Instant issued) {
            bucketCredentials.issued = issued;
            return this;
        }

        /**
         * Sets the modified timestamp.
         *
         * @param modified the modified timestamp
         * @return this builder
         */
        public Builder modified(Instant modified) {
            bucketCredentials.modified = modified;
            return this;
        }

        /**
         * Sets the created-by principal.
         *
         * @param createdBy the creator identifier
         * @return this builder
         */
        public Builder createdBy(String createdBy) {
            bucketCredentials.createdBy = createdBy;
            return this;
        }

        /**
         * Sets the last-modified-by principal.
         *
         * @param lastModifiedBy the modifier identifier
         * @return this builder
         */
        public Builder lastModifiedBy(String lastModifiedBy) {
            bucketCredentials.lastModifiedBy = lastModifiedBy;
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
            bucketCredentials.version = version;
            return this;
        }

        /**
         * Builds the entity.
         *
         * @return the built entity
         */
        public BucketCredentialsEntity build() {
            return bucketCredentials;
        }
    }
}
