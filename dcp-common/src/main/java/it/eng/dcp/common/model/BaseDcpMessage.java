package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import java.io.Serializable;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for DCP messages providing common fields such as @context and type.
 * Subclasses provide a read-only type via {@link #getType()} and may access the
 * mutable context through {@link #getContext()} in builders.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseDcpMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private final List<String> context = new ArrayList<>();

    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    public abstract String getType();

    /**
     * Validate the base fields of this message using Jakarta Validation.
     * Subclass builders should call this method before returning the built instance.
     *
     * @throws ValidationException when validation constraints are violated.
     */
    protected void validateBase() {
        // Enforce that subclass provides non-null, non-blank type via getter
        try {
            String t = getType();
            if (t == null || t.isBlank()) {
                throw new ValidationException("BaseDcpMessage - type must not be null");
            }
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            throw new ValidationException("BaseDcpMessage - failed to determine type: " + e.getMessage());
        }

        try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
            Set<ConstraintViolation<BaseDcpMessage>> violations = vf.getValidator().validate(this);
            if (!violations.isEmpty()) {
                throw new ValidationException("BaseDcpMessage - " +
                        violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
            }
        }
    }
}

