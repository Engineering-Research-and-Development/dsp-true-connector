package it.eng.connector.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Response-only payload for the current user.
 *
 * <p>This class is used to return the current user's information in a response.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = UserCurrentUserResponse.Builder.class)
public class UserCurrentUserResponse {
    private String firstName;

    private String lastName;

    private String email;

    private String tenantId;

    private String role;

    /**
     * Builder for {@link UserCurrentUserResponse}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final UserCurrentUserResponse response;

        private Builder() {
            response = new UserCurrentUserResponse();
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
            response.firstName = firstName;
            return this;
        }

        /**
         * Sets user last name.
         *
         * @param lastName user last name
         * @return this builder
         */
        public Builder lastName(String lastName) {
            response.lastName = lastName;
            return this;
        }

        /**
         * Sets user e-mail.
         *
         * @param email user e-mail
         * @return this builder
         */
        public Builder email(String email) {
            response.email = email;
            return this;
        }

        /**
         * Sets the tenant this user belongs to.
         *
         * @param tenantId tenant identifier
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            response.tenantId = tenantId;
            return this;
        }

        /**
         * Sets the role of the user.
         *
         * @param role the role of the user
         * @return this builder
         */
        public Builder role(String role) {
            response.role = role;
            return this;
        }

        /**
         * Validates and builds the {@link UserCurrentUserResponse} instance.
         *
         * @return the built {@link UserCurrentUserResponse}
         */
        public UserCurrentUserResponse build() {
            Set<ConstraintViolation<UserCurrentUserResponse>> violations =
                    Validation.buildDefaultValidatorFactory().getValidator().validate(response);
            if (violations.isEmpty()) {
                return response;
            }
            throw new ValidationException("UserCurrentUserResponse - " +
                    violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }

    }
}
