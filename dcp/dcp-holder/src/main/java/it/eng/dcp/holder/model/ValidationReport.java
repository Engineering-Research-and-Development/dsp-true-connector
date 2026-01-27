package it.eng.dcp.holder.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validation report for verifiable credential presentation validation.
 */
@Getter
public class ValidationReport {

    private final List<ValidationError> errors = new ArrayList<>();
    private final Set<String> acceptedCredentialTypes = new HashSet<>();

    private ValidationReport() {
    }

    /**
     * Add a validation error to the report.
     *
     * @param err the validation error to add
     */
    public void addError(ValidationError err) {
        if (err != null) errors.add(err);
    }

    /**
     * Check if the validation is successful (no ERROR severity errors).
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return errors.stream().noneMatch(e -> e.severity() == ValidationError.Severity.ERROR);
    }

    /**
     * Add an accepted credential type to the report.
     *
     * @param credentialType the credential type that was accepted
     */
    public void addAccepted(String credentialType) {
        if (credentialType != null) acceptedCredentialTypes.add(credentialType);
    }

    public static class Builder {
        private final ValidationReport report;

        private Builder() {
            report = new ValidationReport();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder valid(boolean valid) {
            // For backwards compatibility - if explicitly set to invalid, add a generic error
            if (!valid && report.errors.isEmpty()) {
                report.errors.add(new ValidationError(
                    "VALIDATION_FAILED",
                    "Validation failed",
                    ValidationError.Severity.ERROR
                ));
            }
            return this;
        }

        public Builder errors(List<String> errorMessages) {
            if (errorMessages != null) {
                for (String msg : errorMessages) {
                    if (msg != null && !msg.isEmpty()) {
                        report.errors.add(new ValidationError(
                            "VALIDATION_ERROR",
                            msg,
                            ValidationError.Severity.ERROR
                        ));
                    }
                }
            }
            return this;
        }

        public Builder error(ValidationError error) {
            if (error != null) {
                report.errors.add(error);
            }
            return this;
        }

        public Builder acceptedCredentialTypes(Set<String> types) {
            if (types != null) {
                report.acceptedCredentialTypes.addAll(types);
            }
            return this;
        }

        public Builder acceptedCredentialType(String type) {
            if (type != null) {
                report.acceptedCredentialTypes.add(type);
            }
            return this;
        }

        public ValidationReport build() {
            return report;
        }
    }
}
