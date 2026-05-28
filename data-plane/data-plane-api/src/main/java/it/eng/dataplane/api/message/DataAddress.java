package it.eng.dataplane.api.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.dataplane.api.DataPlaneConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Schema-aligned data address embedded in DPS start messages.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DataAddress.Builder.class)
public class DataAddress {

    @NotBlank
    private String endpointType;

    private String endpoint;

    @Valid
    private List<EndpointProperty> endpointProperties;

    /** Builder for {@link DataAddress}. */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final DataAddress dataAddress = new DataAddress();

        private Builder() {
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
         * Sets the endpoint type.
         *
         * @param endpointType endpoint type value
         * @return this builder
         */
        public Builder endpointType(String endpointType) {
            dataAddress.endpointType = endpointType;
            return this;
        }

        /**
         * Sets the endpoint.
         *
         * @param endpoint endpoint value
         * @return this builder
         */
        public Builder endpoint(String endpoint) {
            dataAddress.endpoint = endpoint;
            return this;
        }

        /**
         * Sets endpoint properties.
         *
         * @param endpointProperties endpoint properties
         * @return this builder
         */
        public Builder endpointProperties(List<EndpointProperty> endpointProperties) {
            dataAddress.endpointProperties = endpointProperties;
            return this;
        }

        /**
         * Builds and validates the data address.
         *
         * @return validated data address
         * @throws ValidationException if required fields are missing
         */
        public DataAddress build() {
            Set<ConstraintViolation<DataAddress>> violations;
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(dataAddress);
            }
            if (violations.isEmpty()) {
                return dataAddress;
            }
            throw new ValidationException("DataAddress - " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", ")));
        }
    }

    /**
     * Flattens the schema-aligned data address into the legacy internal map representation.
     *
     * @return flat property map used by dataplane protocols
     */
    public Map<String, String> toPropertyMap() {
        Map<String, String> properties = new LinkedHashMap<>();
        if (endpoint != null) {
            properties.put(DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT, endpoint);
        }
        properties.put(DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT_TYPE, endpointType);
        if (endpointProperties != null) {
            endpointProperties.forEach(property -> properties.put(property.getName(), property.getValue()));
        }
        return Map.copyOf(properties);
    }

    /**
     * Returns the schema type name.
     *
     * @return simple type name
     */
    @JsonProperty(value = DataPlaneConstants.TYPE, access = Access.READ_ONLY)
    public String getType() {
        return "DataAddress";
    }
}
