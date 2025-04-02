package it.eng.negotiation.policy.model;

import java.util.Objects;

import it.eng.negotiation.model.LeftOperand;

/**
 * Represents a decision made by a policy evaluator.
 * Contains information about whether access is allowed or denied,
 * along with a message explaining the decision.
 */
public class PolicyDecision {

	private final boolean allowed;
    private final String message;
    private final String policyId;
    private final LeftOperand policyType;

    private PolicyDecision(boolean allowed, String message, String policyId, LeftOperand policyType) {
        this.allowed = allowed;
        this.message = message;
        this.policyId = policyId;
        this.policyType = policyType;
    }

    /**
     * Returns whether access is allowed.
     *
     * @return true if access is allowed, false otherwise
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Returns a message explaining the decision.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the ID of the policy that made the decision.
     *
     * @return the policy ID
     */
    public String getPolicyId() {
        return policyId;
    }

    /**
     * Returns the type of the policy that made the decision.
     *
     * @return the policy type
     */
    public LeftOperand getPolicyType() {
        return policyType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolicyDecision that = (PolicyDecision) o;
        return allowed == that.allowed &&
                Objects.equals(message, that.message) &&
                Objects.equals(policyId, that.policyId) &&
                policyType == that.policyType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, message, policyId, policyType);
    }

    @Override
    public String toString() {
        return "PolicyDecision{" +
                "allowed=" + allowed +
                ", message='" + message + '\'' +
                ", policyId='" + policyId + '\'' +
                ", policyType=" + policyType +
                '}';
    }

    /**
     * Builder for PolicyDecision.
     */
    public static class Builder {
        private boolean allowed;
        private String message;
        private String policyId;
        private LeftOperand policyType;

        private Builder() {
        }

        /**
         * Creates a new Builder instance.
         *
         * @return a new Builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets whether access is allowed.
         *
         * @param allowed true if access is allowed, false otherwise
         * @return this builder
         */
        public Builder allowed(boolean allowed) {
            this.allowed = allowed;
            return this;
        }

        /**
         * Sets the message explaining the decision.
         *
         * @param message the message
         * @return this builder
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Sets the ID of the policy that made the decision.
         *
         * @param policyId the policy ID
         * @return this builder
         */
        public Builder policyId(String policyId) {
            this.policyId = policyId;
            return this;
        }

        /**
         * Sets the type of the policy that made the decision.
         *
         * @param policyType the policy type
         * @return this builder
         */
        public Builder policyType(LeftOperand policyType) {
            this.policyType = policyType;
            return this;
        }

        /**
         * Builds a new PolicyDecision.
         *
         * @return a new PolicyDecision
         */
        public PolicyDecision build() {
            return new PolicyDecision(allowed, message, policyId, policyType);
        }
    }
}
