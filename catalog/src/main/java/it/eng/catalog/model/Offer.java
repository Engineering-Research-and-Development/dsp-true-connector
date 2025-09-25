package it.eng.catalog.model;

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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode(exclude = {"target", "assigner", "assignee"}) // requires for offer check in negotiation flow
@JsonDeserialize(builder = Offer.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic = true)
public class Offer implements Serializable {

    @Serial
    private static final long serialVersionUID = 4003295986049329564L;

    //	@NotNull (if new offer from consumer Id of offer is null)
    @JsonProperty(DSpaceConstants.ID)
    private String id;

    // Different to a Catalog or Dataset, the Offer inside a Contract Request Message must have an odrl:target attribute.
    // not mandatory for Catalog or Dataset offer to have target field - different from the Offer in negotiation module
    private String target;

    // required in catalog???
    private String assigner;

    // required in catalog???
    private String assignee;

    @NotNull
    private Set<Permission> permission;

    @JsonIgnoreProperties(value = {"type"}) //, allowGetters=true
    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    private String getType() {
        return Offer.class.getSimpleName();
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

        @JsonDeserialize(as = Set.class)
        public Builder permission(Set<Permission> permission) {
            offer.permission = permission;
            return this;
        }

        public Offer build() {
            if (offer.id == null) {
                offer.id = "urn:uuid:" + UUID.randomUUID().toString();
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

    public void validateProtocol() {
        // Validate Permissions collection
        if (this.getPermission() == null || this.getPermission().isEmpty()) {
            throw new ValidationException("Offer must have at least one Permission defined");
        }

        // Check if there's at least one non-null accessService
        if (this.getPermission().stream().noneMatch(Objects::nonNull)) {
            throw new ValidationException("Offer must have at least one non-null Permission");
        }

        // Validate each Permission in collection
        for (Permission permission : this.getPermission()) {
            try {
                permission.validateProtocol();
            } catch (ValidationException e) {
                throw new ValidationException("Invalid Permission in Offer: " + e.getMessage());
            }
        }
    }

}
