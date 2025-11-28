package it.eng.tools.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
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
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@JsonDeserialize(builder = ApplicationProperty.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {IConstants.ID, IConstants.KEY, IConstants.VALUE, IConstants.SAMPLE_VALUE, IConstants.MANDATORY}, alphabetic = true)
@Document(collection = "application_properties")
public class ApplicationProperty {

    /*
     * @JsonProperty(IConstants.ID)
     *
     * @Setter
     *
     * @Id private String id;
     */

    @Id
    @NotNull
    @JsonProperty(IConstants.KEY)
    private String key;

    @JsonProperty(IConstants.VALUE)
    private String value;

    @JsonProperty(IConstants.LABEL)
    private String label;

    @JsonProperty(IConstants.GROUP)
    private String group;

    @JsonProperty(IConstants.TOOLTIP)
    private String tooltip;

    @JsonProperty(IConstants.SAMPLE_VALUE)
    private String sampleValue;

    @JsonProperty(IConstants.MANDATORY)
    private boolean mandatory;

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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final ApplicationProperty property;

        private Builder() {
            property = new ApplicationProperty();
        }

        public static Builder newInstance() {
            return new Builder();
        }

//		@JsonProperty(IConstants.ID)
//		public Builder id(String id) {
//			property.id = id;
//			return this;
//		}

        @JsonProperty(IConstants.KEY)
        public Builder key(String key) {
            property.key = key;
            return this;
        }

        @JsonProperty(IConstants.VALUE)
        public Builder value(String value) {
            property.value = value;
            return this;
        }
        
        @JsonProperty(IConstants.LABEL)
        public Builder label(String label) {
            property.label = label;
            return this;
        }

        @JsonProperty(IConstants.GROUP)
        public Builder group(String group) {
            property.group = group;
            return this;
        }

        @JsonProperty(IConstants.TOOLTIP)
        public Builder tooltip(String tooltip) {
            property.tooltip = tooltip;
            return this;
        }

        @JsonProperty(IConstants.SAMPLE_VALUE)
        public Builder sampleValue(String sampleValue) {
            property.sampleValue = sampleValue;
            return this;
        }

        @JsonProperty(IConstants.MANDATORY)
        public Builder mandatory(boolean mandatory) {
            property.mandatory = mandatory;
            return this;
        }

        @JsonProperty("createdBy")
        public Builder createdBy(String createdBy) {
            property.createdBy = createdBy;
            return this;
        }

        @JsonProperty("lastModifiedBy")
        public Builder lastModifiedBy(String lastModifiedBy) {
            property.lastModifiedBy = lastModifiedBy;
            return this;
        }

        public Builder issued(Instant issued) {
            property.issued = issued;
            return this;
        }

        public Builder modified(Instant modified) {
            property.modified = modified;
            return this;
        }

        @JsonProperty("version")
        public Builder version(Long version) {
            property.version = version;
            return this;
        }

        public ApplicationProperty build() {
            Set<ConstraintViolation<ApplicationProperty>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(property);
            if (violations.isEmpty()) {
                return property;
            }
            throw new ValidationException("Property - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(mandatory, key, sampleValue, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass()) {
            return false;
        }
        ApplicationProperty other = (ApplicationProperty) obj;
        return mandatory == other.mandatory && Objects.equals(key, other.key)
                && Objects.equals(sampleValue, other.sampleValue) && Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        return "Property [" /* + IConstants.ID + "=" + id +
				", "*/ + IConstants.KEY + "=" + key +
                ", " + IConstants.VALUE + "=" + value +
                ", " + IConstants.SAMPLE_VALUE + "=" + sampleValue +
                ", " + IConstants.MANDATORY + "=" + mandatory + "]";
    }

    @JsonProperty(value = IConstants.TYPE, access = Access.READ_ONLY)
    public String getType() {
        return ApplicationProperty.class.getSimpleName();
    }
}
