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

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private BucketCredentialsEntity bucketCredentials;

        private Builder() {
            bucketCredentials = new BucketCredentialsEntity();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty
        public Builder bucketName(String bucketName) {
            bucketCredentials.bucketName = bucketName;
            return this;
        }

        @JsonProperty("access_key")
        public Builder accessKey(String accessKey) {
            bucketCredentials.accessKey = accessKey;
            return this;
        }

        @JsonProperty("secret_key")
        public Builder secretKey(String secretKey) {
            bucketCredentials.secretKey = secretKey;
            return this;
        }

        public Builder issued(Instant issued) {
            bucketCredentials.issued = issued;
            return this;
        }

        public Builder modified(Instant modified) {
            bucketCredentials.modified = modified;
            return this;
        }

        public Builder createdBy(String createdBy) {
            bucketCredentials.createdBy = createdBy;
            return this;
        }

        public Builder lastModifiedBy(String lastModifiedBy) {
            bucketCredentials.lastModifiedBy = lastModifiedBy;
            return this;
        }

        @JsonProperty("version")
        public Builder version(Long version) {
            bucketCredentials.version = version;
            return this;
        }

        public BucketCredentialsEntity build() {
            return bucketCredentials;
        }
    }
}
