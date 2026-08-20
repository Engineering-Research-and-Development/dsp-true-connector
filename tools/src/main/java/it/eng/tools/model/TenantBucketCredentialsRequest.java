package it.eng.tools.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request-only carrier for the optional "bring your own bucket" fields that an admin may
 * supply when creating or updating a tenant. This class is never persisted (no Spring Data
 * annotations) and must never be returned from any controller method, since it may carry a
 * plaintext {@code secretKey}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = TenantBucketCredentialsRequest.Builder.class)
public class TenantBucketCredentialsRequest {

    /**
     * The candidate or existing S3 bucket name, or {@code null} for fully automatic provisioning.
     */
    private String bucketName;

    /**
     * The candidate external S3 access key, or {@code null} when not supplying external credentials.
     */
    private String accessKey;

    /**
     * The candidate external S3 secret key, or {@code null} when not supplying external credentials.
     */
    private String secretKey;

    /**
     * Whether the supplied candidate credentials should be verified against S3 before being
     * persisted. Defaults to {@code false} when not explicitly set.
     */
    private boolean verifyConnection;

    /**
     * Builder for creating {@link TenantBucketCredentialsRequest} instances.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final TenantBucketCredentialsRequest request;

        private Builder() {
            request = new TenantBucketCredentialsRequest();
        }

        /**
         * Creates a new {@link Builder} instance.
         *
         * @return a new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets the candidate or existing S3 bucket name.
         *
         * @param bucketName the S3 bucket name, or {@code null} for fully automatic provisioning
         * @return this builder
         */
        public Builder bucketName(String bucketName) {
            request.bucketName = bucketName;
            return this;
        }

        /**
         * Sets the candidate external S3 access key.
         *
         * @param accessKey the S3 access key, or {@code null} when not supplying external credentials
         * @return this builder
         */
        public Builder accessKey(String accessKey) {
            request.accessKey = accessKey;
            return this;
        }

        /**
         * Sets the candidate external S3 secret key.
         *
         * @param secretKey the S3 secret key, or {@code null} when not supplying external credentials
         * @return this builder
         */
        public Builder secretKey(String secretKey) {
            request.secretKey = secretKey;
            return this;
        }

        /**
         * Sets whether the supplied candidate credentials should be verified against S3
         * before being persisted.
         *
         * @param verifyConnection the verify-connection flag
         * @return this builder
         */
        public Builder verifyConnection(boolean verifyConnection) {
            request.verifyConnection = verifyConnection;
            return this;
        }

        /**
         * Builds the {@link TenantBucketCredentialsRequest} instance.
         *
         * @return the built request
         */
        public TenantBucketCredentialsRequest build() {
            return request;
        }
    }
}
