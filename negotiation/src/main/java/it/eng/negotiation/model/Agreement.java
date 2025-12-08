package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@JsonDeserialize(builder = Agreement.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "agreements")
public class Agreement implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @JsonProperty(DSpaceConstants.ID)
    private String id;

    @NotNull
    private String assigner;

    @NotNull
    private String assignee;

    @NotNull
    private String target;

    private String timestamp;

    private List<Permission> permission;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final Agreement agreement;

        private Builder() {
            agreement = new Agreement();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty(DSpaceConstants.ID)
        public Builder id(String id) {
            agreement.id = id;
            return this;
        }

        public Builder assigner(String assigner) {
            agreement.assigner = assigner;
            return this;
        }

        public Builder assignee(String assignee) {
            agreement.assignee = assignee;
            return this;
        }

        public Builder target(String target) {
            agreement.target = target;
            return this;
        }

        public Builder timestamp(String timestamp) {
            agreement.timestamp = timestamp;
            return this;
        }

        public Builder permission(List<Permission> permission) {
            agreement.permission = permission;
            return this;
        }

        public Agreement build() {
            if (agreement.id == null) {
                agreement.id = "urn:uuid:" + UUID.randomUUID();
            }
            Set<ConstraintViolation<Agreement>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(agreement);
            if (violations.isEmpty()) {
                return agreement;
            }
            throw new ValidationException("Agreement - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(", ")));
        }
    }

    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    public String getType() {
        return Agreement.class.getSimpleName();
    }

}
