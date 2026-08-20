package it.eng.tools.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Request-only payload for creating a tenant and optionally supplying bucket credentials.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = TenantCreateRequest.Builder.class)
public class TenantCreateRequest {

    @NotNull
    private String id;

    @NotNull
    private String name;

    private String description;

    @NotNull
    private String participantId;

    private boolean automaticNegotiation;

    private boolean automaticTransfer;

    private boolean enabled;

    private String bucketName;

    private String accessKey;

    private String secretKey;

    private boolean verifyConnection;

    /**
     * Converts this request to a plain tenant model.
     *
     * @return the tenant built from tenant-shaped fields only
     */
    public Tenant toTenant() {
        return Tenant.Builder.newInstance()
                .id(id)
                .name(name)
                .description(description)
                .participantId(participantId)
                .automaticNegotiation(automaticNegotiation)
                .automaticTransfer(automaticTransfer)
                .enabled(enabled)
                .build();
    }

    /**
     * Converts this request to tenant bucket credentials request.
     *
     * @return request containing optional bucket credential fields
     */
    public TenantBucketCredentialsRequest toCredentialsRequest() {
        return TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(bucketName)
                .accessKey(accessKey)
                .secretKey(secretKey)
                .verifyConnection(verifyConnection)
                .build();
    }

    /**
     * Builder for {@link TenantCreateRequest}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final TenantCreateRequest request;

        private Builder() {
            request = new TenantCreateRequest();
        }

        /**
         * Creates a new builder instance.
         *
         * @return a new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets tenant identifier.
         *
         * @param id tenant identifier
         * @return this builder
         */
        public Builder id(String id) {
            request.id = id;
            return this;
        }

        /**
         * Sets tenant name.
         *
         * @param name tenant name
         * @return this builder
         */
        public Builder name(String name) {
            request.name = name;
            return this;
        }

        /**
         * Sets tenant description.
         *
         * @param description tenant description
         * @return this builder
         */
        public Builder description(String description) {
            request.description = description;
            return this;
        }

        /**
         * Sets participant identifier.
         *
         * @param participantId participant identifier
         * @return this builder
         */
        public Builder participantId(String participantId) {
            request.participantId = participantId;
            return this;
        }

        /**
         * Sets automatic negotiation flag.
         *
         * @param automaticNegotiation automatic negotiation flag
         * @return this builder
         */
        public Builder automaticNegotiation(boolean automaticNegotiation) {
            request.automaticNegotiation = automaticNegotiation;
            return this;
        }

        /**
         * Sets automatic transfer flag.
         *
         * @param automaticTransfer automatic transfer flag
         * @return this builder
         */
        public Builder automaticTransfer(boolean automaticTransfer) {
            request.automaticTransfer = automaticTransfer;
            return this;
        }

        /**
         * Sets enabled flag.
         *
         * @param enabled enabled flag
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            request.enabled = enabled;
            return this;
        }

        /**
         * Sets candidate bucket name.
         *
         * @param bucketName candidate bucket name
         * @return this builder
         */
        public Builder bucketName(String bucketName) {
            request.bucketName = bucketName;
            return this;
        }

        /**
         * Sets candidate access key.
         *
         * @param accessKey candidate access key
         * @return this builder
         */
        public Builder accessKey(String accessKey) {
            request.accessKey = accessKey;
            return this;
        }

        /**
         * Sets candidate secret key.
         *
         * @param secretKey candidate secret key
         * @return this builder
         */
        public Builder secretKey(String secretKey) {
            request.secretKey = secretKey;
            return this;
        }

        /**
         * Sets connection verification flag.
         *
         * @param verifyConnection verification flag
         * @return this builder
         */
        public Builder verifyConnection(boolean verifyConnection) {
            request.verifyConnection = verifyConnection;
            return this;
        }

        /**
         * Validates and builds request instance.
         *
         * @return built request
         * @throws ValidationException if required fields are missing
         */
        public TenantCreateRequest build() {
            Set<ConstraintViolation<TenantCreateRequest>> violations =
                    Validation.buildDefaultValidatorFactory().getValidator().validate(request);
            if (violations.isEmpty()) {
                return request;
            }
            throw new ValidationException("TenantCreateRequest - " +
                    violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }
}
