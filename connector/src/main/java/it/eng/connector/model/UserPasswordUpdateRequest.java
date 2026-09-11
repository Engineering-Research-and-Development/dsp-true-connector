package it.eng.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Request-only payload for changing a user's password.
 *
 * <p>{@code password} must match the user's current stored password; {@code newPassword} is the
 * candidate replacement, subject to password-strength validation in the service.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = UserPasswordUpdateRequest.Builder.class)
public class UserPasswordUpdateRequest {

    @NotNull
    private String password;

    @NotNull
    private String newPassword;

    /**
     * Builder for {@link UserPasswordUpdateRequest}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final UserPasswordUpdateRequest request;

        private Builder() {
            request = new UserPasswordUpdateRequest();
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
         * Sets the current password.
         *
         * @param password current password
         * @return this builder
         */
        public Builder password(String password) {
            request.password = password;
            return this;
        }

        /**
         * Sets the new password.
         *
         * @param newPassword new password
         * @return this builder
         */
        public Builder newPassword(String newPassword) {
            request.newPassword = newPassword;
            return this;
        }

        /**
         * Validates and builds request instance.
         *
         * @return built request
         * @throws ValidationException if required fields are missing
         */
        public UserPasswordUpdateRequest build() {
            Set<ConstraintViolation<UserPasswordUpdateRequest>> violations =
                    Validation.buildDefaultValidatorFactory().getValidator().validate(request);
            if (violations.isEmpty()) {
                return request;
            }
            throw new ValidationException("UserPasswordUpdateRequest - " +
                    violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }
}
