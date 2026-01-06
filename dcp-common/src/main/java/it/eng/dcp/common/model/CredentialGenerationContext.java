package it.eng.dcp.common.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Internal class for credential generation parameters.
 * This is NOT part of the DCP protocol - it's used internally by the issuance service
 * to pass additional parameters (claims, constraints) when generating credentials.
 *
 * This separates protocol-defined messages from internal business logic requirements.
 */
@Getter
public class CredentialGenerationContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Explicit getters
    /**
     * The protocol-defined credential request.
     */
    @NotNull
    private CredentialRequest request;

    /**
     * Claims to include in credentials (key-value pairs).
     * Example: {"country_code": "US", "role": "admin"}
     * These claims are used when generating credential subjects.
     */
    private Map<String, Object> requestedClaims = new HashMap<>();

    /**
     * Constraints that must be verified before issuance.
     * These can come from:
     * - Presentation query definitions
     * - Approval workflow logic
     * - Business rules
     */
    private List<ConstraintRule> constraints = new ArrayList<>();

    /**
     * Additional metadata for credential generation.
     * Can include things like:
     * - Credential validity period
     * - Issuer-specific attributes
     * - Audit information
     */
    private Map<String, Object> metadata = new HashMap<>();

    private CredentialGenerationContext() {}

    // Custom builder
    public static class Builder {
        private final CredentialGenerationContext context;

        private Builder() {
            context = new CredentialGenerationContext();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder request(CredentialRequest request) {
            context.request = request;
            return this;
        }

        public Builder requestedClaims(Map<String, Object> claims) {
            context.requestedClaims = (claims != null) ? new HashMap<>(claims) : new HashMap<>();
            return this;
        }

        public Builder constraints(List<ConstraintRule> constraints) {
            context.constraints = (constraints != null) ? new ArrayList<>(constraints) : new ArrayList<>();
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            context.metadata = (metadata != null) ? new HashMap<>(metadata) : new HashMap<>();
            return this;
        }

        public CredentialGenerationContext build() {
            try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<CredentialGenerationContext>> violations = vf.getValidator().validate(context);
                if (violations.isEmpty()) {
                    return context;
                }
                throw new ValidationException("CredentialGenerationContext - " +
                        violations.stream()
                                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                                .collect(Collectors.joining(",")));
            }
        }
    }

    // Static factory methods updated to use the new builder
    public static CredentialGenerationContext fromRequest(CredentialRequest request) {
        return Builder.newInstance()
                .request(request)
                .build();
    }
    public static CredentialGenerationContext withClaims(CredentialRequest request, Map<String, Object> claims) {
        return Builder.newInstance()
                .request(request)
                .requestedClaims(claims)
                .build();
    }
    public static CredentialGenerationContext withConstraints(CredentialRequest request,
                                                             Map<String, Object> claims,
                                                             List<ConstraintRule> constraints) {
        return Builder.newInstance()
                .request(request)
                .requestedClaims(claims)
                .constraints(constraints)
                .build();
    }
}
