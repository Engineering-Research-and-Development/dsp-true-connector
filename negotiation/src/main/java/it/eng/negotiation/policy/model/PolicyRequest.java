package it.eng.negotiation.policy.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import it.eng.negotiation.model.Action;

/**
 * Represents a request to evaluate a policy.
 * Contains information about the agreement, resource, user, and operation.
 */
public class PolicyRequest {

	private final String agreementId;
    private final String resourceId;
    private final String userId;
    private final Action action;
    private final Map<String, Object> attributes;

    private PolicyRequest(String agreementId, String resourceId, String userId, Action action, Map<String, Object> attributes) {
        this.agreementId = agreementId;
        this.resourceId = resourceId;
        this.userId = userId;
        this.action = action;
        this.attributes = attributes;
    }

    /**
     * Returns the agreement ID.
     *
     * @return the agreement ID
     */
    public String getAgreementId() {
        return agreementId;
    }

    /**
     * Returns the resource ID.
     *
     * @return the resource ID
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * Returns the user ID.
     *
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Returns the operation.
     *
     * @return the operation
     */
    public Action getAction() {
        return action;
    }

    /**
     * Returns the attributes.
     *
     * @return the attributes
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Returns the value of the specified attribute.
     *
     * @param key the attribute key
     * @return the attribute value, or null if the attribute does not exist
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolicyRequest that = (PolicyRequest) o;
        return Objects.equals(agreementId, that.agreementId) &&
                Objects.equals(resourceId, that.resourceId) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(action, that.action) &&
                Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agreementId, resourceId, userId, action, attributes);
    }

    @Override
    public String toString() {
        return "PolicyRequest{" +
                "agreementId='" + agreementId + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", userId='" + userId + '\'' +
                ", action='" + action + '\'' +
                ", attributes=" + attributes +
                '}';
    }

    /**
     * Builder for PolicyRequest.
     */
    public static class Builder {
        private String agreementId;
        private String resourceId;
        private String userId;
        private Action action;
        private final Map<String, Object> attributes = new HashMap<>();

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
         * Sets the agreement ID.
         *
         * @param agreementId the agreement ID
         * @return this builder
         */
        public Builder agreementId(String agreementId) {
            this.agreementId = agreementId;
            return this;
        }

        /**
         * Sets the resource ID.
         *
         * @param resourceId the resource ID
         * @return this builder
         */
        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        /**
         * Sets the user ID.
         *
         * @param userId the user ID
         * @return this builder
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Sets the operation.
         *
         * @param action the operation
         * @return this builder
         */
        public Builder action(Action action) {
            this.action = action;
            return this;
        }

        /**
         * Adds an attribute.
         *
         * @param key the attribute key
         * @param value the attribute value
         * @return this builder
         */
        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        /**
         * Builds a new PolicyRequest.
         *
         * @return a new PolicyRequest
         */
        public PolicyRequest build() {
            return new PolicyRequest(agreementId, resourceId, userId, action, new HashMap<>(attributes));
        }
    }
}
