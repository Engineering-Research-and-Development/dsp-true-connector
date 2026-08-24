package it.eng.dataplane.api.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.dataplane.api.DataPlaneConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Schema-aligned data address endpoint property.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = EndpointProperty.Builder.class)
public class EndpointProperty {

    @NotBlank
    private String name;

    @NotBlank
    private String value;

    /** Builder for {@link EndpointProperty}. */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final EndpointProperty property = new EndpointProperty();

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
         * Sets the property name.
         *
         * @param name property name
         * @return this builder
         */
        public Builder name(String name) {
            property.name = name;
            return this;
        }

        /**
         * Sets the property value.
         *
         * @param value property value
         * @return this builder
         */
        public Builder value(String value) {
            property.value = value;
            return this;
        }

        /**
         * Builds and validates the endpoint property.
         *
         * @return validated endpoint property
         * @throws ValidationException if required fields are missing
         */
        public EndpointProperty build() {
            Set<ConstraintViolation<EndpointProperty>> violations;
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(property);
            }
            if (violations.isEmpty()) {
                return property;
            }
            throw new ValidationException("EndpointProperty - " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", ")));
        }
    }

    /**
     * Returns the schema type name.
     *
     * @return simple type name
     */
    @JsonProperty(value = DataPlaneConstants.TYPE, access = Access.READ_ONLY)
    public String getType() {
        return "EndpointProperty";
    }
}
