package it.eng.datatransfer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents a Data Plane registration on the Control Plane.
 * Stores the endpoint, supported transfer types, and optional authentication details.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "data_plane_registrations")
@JsonDeserialize(builder = DataPlaneRegistration.Builder.class)
public class DataPlaneRegistration {

    @Id
    private String id;

    @NotBlank
    private String endpoint;

    @NotEmpty
    private Set<String> supportedTransferTypes;

    private String authType;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    private Instant lastHeartbeat;

    private Instant registeredAt;

    /**
     * Builder for {@link DataPlaneRegistration}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final DataPlaneRegistration registration;

        private Builder() {
            registration = new DataPlaneRegistration();
        }

        /**
         * Creates a new builder instance.
         *
         * @return new Builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets the registration id.
         *
         * @param id the id
         * @return this builder
         */
        public Builder id(String id) {
            registration.id = id;
            return this;
        }

        /**
         * Sets the Data Plane endpoint URL.
         *
         * @param endpoint the endpoint URL
         * @return this builder
         */
        public Builder endpoint(String endpoint) {
            registration.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the supported transfer types.
         *
         * @param supportedTransferTypes set of transfer type identifiers
         * @return this builder
         */
        public Builder supportedTransferTypes(Set<String> supportedTransferTypes) {
            registration.supportedTransferTypes = supportedTransferTypes;
            return this;
        }

        /**
         * Sets the optional authentication type.
         *
         * @param authType e.g. "API_KEY"
         * @return this builder
         */
        public Builder authType(String authType) {
            registration.authType = authType;
            return this;
        }

        /**
         * Sets the optional API key for authentication.
         *
         * @param apiKey the API key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            registration.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the last heartbeat timestamp.
         *
         * @param lastHeartbeat the last heartbeat instant
         * @return this builder
         */
        public Builder lastHeartbeat(Instant lastHeartbeat) {
            registration.lastHeartbeat = lastHeartbeat;
            return this;
        }

        /**
         * Sets the registration timestamp.
         *
         * @param registeredAt the registration instant
         * @return this builder
         */
        public Builder registeredAt(Instant registeredAt) {
            registration.registeredAt = registeredAt;
            return this;
        }

        /**
         * Builds and validates the {@link DataPlaneRegistration}.
         * Auto-generates {@code id} and {@code registeredAt} if not set.
         *
         * @return validated DataPlaneRegistration instance
         * @throws ValidationException if required fields are missing or invalid
         */
        public DataPlaneRegistration build() {
            if (registration.id == null) {
                registration.id = UUID.randomUUID().toString();
            }
            if (registration.registeredAt == null) {
                registration.registeredAt = Instant.now();
            }
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<DataPlaneRegistration>> violations =
                        factory.getValidator().validate(registration);
                if (violations.isEmpty()) {
                    return registration;
                }
                throw new ValidationException("DataPlaneRegistration - " +
                        violations.stream()
                                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                                .collect(Collectors.joining(",")));
            }
        }
    }
}
