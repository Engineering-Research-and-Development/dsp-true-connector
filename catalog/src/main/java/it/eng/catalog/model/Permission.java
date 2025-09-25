package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
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
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode(exclude = {"target", "assigner", "assignee"}) // requires for offer check in negotiation flow
@JsonDeserialize(builder = Permission.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Permission implements Serializable {

    @Serial
    private static final long serialVersionUID = -6221623714296723036L;

    private String assigner;

    private String assignee;

    // not sure if this one is required at all or just optional for permission
    private String target;

    @NotNull
    private Action action;

    @NotNull
    private Set<Constraint> constraint;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final Permission permission;

        private Builder() {
            permission = new Permission();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder assigner(String assigner) {
            permission.assigner = assigner;
            return this;
        }

        public Builder assignee(String assignee) {
            permission.assignee = assignee;
            return this;
        }

        public Builder target(String target) {
            permission.target = target;
            return this;
        }

        public Builder action(Action action) {
            permission.action = action;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder constraint(Set<Constraint> constraint) {
            permission.constraint = constraint;
            return this;
        }

        public Permission build() {
            Set<ConstraintViolation<Permission>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(permission);
            if (violations.isEmpty()) {
                return permission;
            }
            throw new ValidationException("Permission - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(", ")));
        }
    }

    public void validateProtocol() {
        // for now, we just check if action and constraint are not null
        if (action == null) {
            throw new ValidationException("Permission - action is mandatory");
        }
        if (constraint == null || constraint.isEmpty()) {
            throw new ValidationException("Permission - constraint is mandatory");
        }
    }
}
