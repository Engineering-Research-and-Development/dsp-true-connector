package it.eng.dcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Defines constraint rules for credential claim verification.
 * Similar to negotiation.model.Constraint but tailored for DCP credentials.
 *
 * Used to validate that credential claims satisfy specific requirements
 * before issuance (e.g., location constraints, role constraints).
 */
@Getter
@EqualsAndHashCode
@JsonDeserialize(builder = ConstraintRule.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConstraintRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The name of the claim this constraint applies to.
     * Examples: "country_code", "role", "clearance_level"
     */
    private String claimName;

    /**
     * The comparison operator to apply.
     */
    private Operator operator;

    /**
     * The expected value or constraint value.
     * Can be a single value, list of values, or pattern depending on operator.
     */
    private Object value;

    /**
     * Constraint operators for claim verification.
     */
    public enum Operator {
        /** Equals. */
        EQ,
        /** Not equals. */
        NEQ,
        /** In list. */
        IN,
        /** Not in list. */
        NOT_IN,
        /** Greater than. */
        GT,
        /** Less than. */
        LT,
        /** Greater than or equal. */
        GTE,
        /** Less than or equal. */
        LTE,
        /** Regex pattern match. */
        MATCHES
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final ConstraintRule constraint;

        private Builder() {
            constraint = new ConstraintRule();
        }

        /**
         * Create a new builder instance.
         *
         * @return A new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Set the claim name.
         *
         * @param claimName The claim name
         * @return This builder
         */
        public Builder claimName(String claimName) {
            constraint.claimName = claimName;
            return this;
        }

        /**
         * Set the operator.
         *
         * @param operator The operator
         * @return This builder
         */
        public Builder operator(Operator operator) {
            constraint.operator = operator;
            return this;
        }

        /**
         * Set the expected value.
         *
         * @param value The value
         * @return This builder
         */
        public Builder value(Object value) {
            constraint.value = value;
            return this;
        }

        /**
         * Build the constraint rule.
         *
         * @return The constraint rule
         */
        public ConstraintRule build() {
            if (constraint.claimName == null || constraint.claimName.isBlank()) {
                throw new IllegalArgumentException("claimName is required");
            }
            if (constraint.operator == null) {
                throw new IllegalArgumentException("operator is required");
            }
            return constraint;
        }
    }

    @Override
    public String toString() {
        return String.format("ConstraintRule{%s %s %s}", claimName, operator, value);
    }
}

