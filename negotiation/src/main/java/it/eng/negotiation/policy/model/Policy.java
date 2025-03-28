package it.eng.negotiation.policy.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Represents a policy that can be evaluated.
 * Contains information about the policy type, description, and attributes.
 */
@Getter
@EqualsAndHashCode
public class Policy {

	 private final String id;
	    private final PolicyType type;
	    private final String description;
	    private final boolean enabled;
	    private final LocalDateTime validFrom;
	    private final LocalDateTime validUntil;
	    private final Map<String, Object> attributes;
	    private final List<String> agreementIds;

	    private Policy(String id, PolicyType type, String description, boolean enabled,
	                  LocalDateTime validFrom, LocalDateTime validUntil, Map<String, Object> attributes,
	                  List<String> agreementIds) {
	        this.id = id;
	        this.type = type;
	        this.description = description;
	        this.enabled = enabled;
	        this.validFrom = validFrom;
	        this.validUntil = validUntil;
	        this.attributes = attributes;
	        this.agreementIds = agreementIds != null ? agreementIds : new ArrayList<>();
	    }

	    public Object getAttribute(String key) {
	        return attributes.get(key);
	    }
	    
	    /**
	     * Returns whether the policy is valid at the specified date and time.
	     *
	     * @param dateTime the date and time
	     * @return true if the policy is valid at the specified date and time, false otherwise
	     */
	    public boolean isValidAt(LocalDateTime dateTime) {
	        if (!enabled) {
	            return false;
	        }
	        
	        if (validFrom != null && dateTime.isBefore(validFrom)) {
	            return false;
	        }
	        
	        if (validUntil != null && dateTime.isAfter(validUntil)) {
	            return false;
	        }
	        
	        return true;
	    }

	    /**
	     * Returns whether the policy is valid at the current date and time.
	     *
	     * @return true if the policy is valid at the current date and time, false otherwise
	     */
	    public boolean isValidNow() {
	        return isValidAt(LocalDateTime.now());
	    }

	    /**
	     * Builder for Policy.
	     */
	    public static class Builder {
	        private String id;
	        private PolicyType type;
	        private String description;
	        private boolean enabled = true;
	        private LocalDateTime validFrom;
	        private LocalDateTime validUntil;
	        private final Map<String, Object> attributes = new HashMap<>();
	        private final List<String> agreementIds = new ArrayList<>();

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
	         * Sets the policy ID.
	         *
	         * @param id the policy ID
	         * @return this builder
	         */
	        public Builder id(String id) {
	            this.id = id;
	            return this;
	        }

	        /**
	         * Sets the policy type.
	         *
	         * @param type the policy type
	         * @return this builder
	         */
	        public Builder type(PolicyType type) {
	            this.type = type;
	            return this;
	        }

	        /**
	         * Sets the policy description.
	         *
	         * @param description the policy description
	         * @return this builder
	         */
	        public Builder description(String description) {
	            this.description = description;
	            return this;
	        }

	        /**
	         * Sets whether the policy is enabled.
	         *
	         * @param enabled true if the policy is enabled, false otherwise
	         * @return this builder
	         */
	        public Builder enabled(boolean enabled) {
	            this.enabled = enabled;
	            return this;
	        }

	        /**
	         * Sets the date and time from which the policy is valid.
	         *
	         * @param validFrom the valid from date and time
	         * @return this builder
	         */
	        public Builder validFrom(LocalDateTime validFrom) {
	            this.validFrom = validFrom;
	            return this;
	        }

	        /**
	         * Sets the date and time until which the policy is valid.
	         *
	         * @param validUntil the valid until date and time
	         * @return this builder
	         */
	        public Builder validUntil(LocalDateTime validUntil) {
	            this.validUntil = validUntil;
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
			 * Adds multiple attributes.
			 *
			 * @param attributes the attributes
			 * @return this builder
			 */
			public Builder attributes(Map<String, Object> attributes) {
				this.attributes.putAll(attributes);
				return this;
			}
	        
	        /**
	         * Adds an agreement ID.
	         *
	         * @param agreementId the agreement ID
	         * @return this builder
	         */
	        public Builder agreementId(String agreementId) {
	            this.agreementIds.add(agreementId);
	            return this;
	        }

	        /**
	         * Builds a new Policy.
	         *
	         * @return a new Policy
	         */
	        public Policy build() {
	            return new Policy(id, type, description, enabled, validFrom, validUntil, 
	                             new HashMap<>(attributes), new ArrayList<>(agreementIds));
	        }
	    }
}
