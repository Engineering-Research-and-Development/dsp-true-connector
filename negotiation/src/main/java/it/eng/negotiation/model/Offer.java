package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Getter
@EqualsAndHashCode
@JsonDeserialize(builder = Offer.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic = true)
public class Offer {

    //	@NotNull
    @JsonProperty(DSpaceConstants.ID)
    private String id;

    @NotNull
    private String target;

    @NotNull
    private String assigner;

    private String assignee;

    //	@NotNull
    private List<Permission> permission;

    /**
     * The original ID as in the provider's Catalog.
     */
    @JsonIgnore
    private String originalId;

    @JsonIgnoreProperties(value = {"type"}, allowGetters = true)
    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    private String getType() {
        return DSpaceConstants.ODRL + Offer.class.getSimpleName();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final Offer offer;

        private Builder() {
            offer = new Offer();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty(DSpaceConstants.ID)
        public Builder id(String id) {
            offer.id = id;
            return this;
        }

        public Builder target(String target) {
            offer.target = target;
            return this;
        }

        public Builder assigner(String assigner) {
            offer.assigner = assigner;
            return this;
        }

        public Builder assignee(String assignee) {
            offer.assignee = assignee;
            return this;
        }

        public Builder originalId(String originalId) {
            offer.originalId = originalId;
            return this;
        }

        public Builder permission(List<Permission> permission) {
            offer.permission = permission;
            return this;
        }

        public Offer build() {
            if (offer.id == null) {
                offer.id = "urn:uuid:" + UUID.randomUUID();
            }
            Set<ConstraintViolation<Offer>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(offer);
            if (violations.isEmpty()) {
                return offer;
            }
            throw new ValidationException("Offer - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(", ")));
        }
    }
}
