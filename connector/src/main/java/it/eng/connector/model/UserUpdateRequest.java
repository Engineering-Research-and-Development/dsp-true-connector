package it.eng.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request-only payload for updating an existing user's profile, credentials, and account flags.
 *
 * <p>{@code firstName}, {@code lastName}, {@code email}, and {@code password} follow
 * partial-update semantics: a {@code null} value preserves the existing stored value. {@code
 * enabled}, {@code expired}, and {@code locked} are always applied as sent.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = UserUpdateRequest.Builder.class)
public class UserUpdateRequest {

    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private boolean enabled;
    private boolean expired;
    private boolean locked;

    /**
     * Builder for {@link UserUpdateRequest}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final UserUpdateRequest request;

        private Builder() {
            request = new UserUpdateRequest();
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
         * Sets user e-mail.
         *
         * @param email user e-mail
         * @return this builder
         */
        public Builder email(String email) {
            request.email = email;
            return this;
        }

        /**
         * Sets user password.
         *
         * @param password user password
         * @return this builder
         */
        public Builder password(String password) {
            request.password = password;
            return this;
        }

        /**
         * Sets enabled flag.
         *
         * @param enabled enabled flag
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            request.enabled = enabled;
            return this;
        }

        /**
         * Sets expired flag.
         *
         * @param expired expired flag
         * @return this builder
         */
        public Builder expired(boolean expired) {
            request.expired = expired;
            return this;
        }

        /**
         * Sets locked flag.
         *
         * @param locked locked flag
         * @return this builder
         */
        public Builder locked(boolean locked) {
            request.locked = locked;
            return this;
        }

        /**
         * Builds request instance.
         *
         * @return built request
         */
        public UserUpdateRequest build() {
            return request;
        }
    }
}
