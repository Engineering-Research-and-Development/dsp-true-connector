package it.eng.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request-only payload for updating a user's first and last name.
 *
 * <p>A {@code null} value for either field preserves the existing stored value.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = UserNamesUpdateRequest.Builder.class)
public class UserNamesUpdateRequest {

    private String firstName;
    private String lastName;

    /**
     * Builder for {@link UserNamesUpdateRequest}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final UserNamesUpdateRequest request;

        private Builder() {
            request = new UserNamesUpdateRequest();
        }

        /**
         * Creates a new builder instance.
         *
         * @return a new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets user first name.
         *
         * @param firstName user first name
         * @return this builder
         */
        public Builder firstName(String firstName) {
            request.firstName = firstName;
            return this;
        }

        /**
         * Sets user last name.
         *
         * @param lastName user last name
         * @return this builder
         */
        public Builder lastName(String lastName) {
            request.lastName = lastName;
            return this;
        }

        /**
         * Builds request instance.
         *
         * @return built request
         */
        public UserNamesUpdateRequest build() {
            return request;
        }
    }
}
