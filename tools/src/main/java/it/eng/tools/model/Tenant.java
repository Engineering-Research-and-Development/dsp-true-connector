package it.eng.tools.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a tenant in the multi-tenant DSP connector.
 * Each tenant has a unique identifier, a human-readable name, and DSP-specific settings.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = Tenant.Builder.class)
@JsonPropertyOrder({"id", "name", "description", "connectorId", "callbackAddress",
        "automaticNegotiation", "automaticTransfer", "enabled"})
@Document(collection = "tenants")
public class Tenant {

    @Id
    @NotNull
    private String id;

    @NotNull
    private String name;

    private String description;

    @NotNull
    private String connectorId;

    @NotNull
    private String callbackAddress;

    private boolean automaticNegotiation;

    private boolean automaticTransfer;

    private boolean enabled;

    @JsonIgnore
    @CreatedDate
    private Instant issued;

    @JsonIgnore
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

    /**
     * Builder for creating {@link Tenant} instances with validation.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final Tenant tenant;

        private Builder() {
            tenant = new Tenant();
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
         * Sets the tenant identifier.
         *
         * @param id the tenant ID
         * @return this builder
         */
        public Builder id(String id) {
            tenant.id = id;
            return this;
        }

        /**
         * Sets the human-readable name.
         *
         * @param name the tenant name
         * @return this builder
         */
        public Builder name(String name) {
            tenant.name = name;
            return this;
        }

        /**
         * Sets the optional description.
         *
         * @param description the description
         * @return this builder
         */
        public Builder description(String description) {
            tenant.description = description;
            return this;
        }

        /**
         * Sets the unique DSP connector identity for this tenant.
         *
         * @param connectorId the connector ID
         * @return this builder
         */
        public Builder connectorId(String connectorId) {
            tenant.connectorId = connectorId;
            return this;
        }

        /**
         * Sets the base URL for outgoing DSP calls.
         *
         * @param callbackAddress the callback address URL
         * @return this builder
         */
        public Builder callbackAddress(String callbackAddress) {
            tenant.callbackAddress = callbackAddress;
            return this;
        }

        /**
         * Sets whether automatic negotiation is enabled for this tenant.
         *
         * @param automaticNegotiation the automatic negotiation flag
         * @return this builder
         */
        public Builder automaticNegotiation(boolean automaticNegotiation) {
            tenant.automaticNegotiation = automaticNegotiation;
            return this;
        }

        /**
         * Sets whether automatic transfer is enabled for this tenant.
         *
         * @param automaticTransfer the automatic transfer flag
         * @return this builder
         */
        public Builder automaticTransfer(boolean automaticTransfer) {
            tenant.automaticTransfer = automaticTransfer;
            return this;
        }

        /**
         * Sets whether this tenant is enabled.
         *
         * @param enabled the enabled flag
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            tenant.enabled = enabled;
            return this;
        }

        /**
         * Sets the creation timestamp.
         *
         * @param issued the creation instant
         * @return this builder
         */
        public Builder issued(Instant issued) {
            tenant.issued = issued;
            return this;
        }

        /**
         * Sets the last-modified timestamp.
         *
         * @param modified the last-modified instant
         * @return this builder
         */
        public Builder modified(Instant modified) {
            tenant.modified = modified;
            return this;
        }

        /**
         * Sets the name of the user who created this tenant.
         *
         * @param createdBy the creator username
         * @return this builder
         */
        public Builder createdBy(String createdBy) {
            tenant.createdBy = createdBy;
            return this;
        }

        /**
         * Sets the name of the user who last modified this tenant.
         *
         * @param lastModifiedBy the last modifier username
         * @return this builder
         */
        public Builder lastModifiedBy(String lastModifiedBy) {
            tenant.lastModifiedBy = lastModifiedBy;
            return this;
        }

        /**
         * Sets the optimistic-locking version.
         *
         * @param version the version number
         * @return this builder
         */
        public Builder version(Long version) {
            tenant.version = version;
            return this;
        }

        /**
         * Validates and builds the {@link Tenant} instance.
         *
         * @return the validated tenant
         * @throws ValidationException if any required fields are missing or invalid
         */
        public Tenant build() {
            Set<ConstraintViolation<Tenant>> violations =
                    Validation.buildDefaultValidatorFactory().getValidator().validate(tenant);
            if (violations.isEmpty()) {
                return tenant;
            }
            throw new ValidationException("Tenant - " +
                    violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }
}
