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
 * Request-only payload for creating a user.
 *
 * <p>The {@code role} is not part of this request: it is computed by the service
 * ({@code ADMIN} when {@code tenantId} is supplied, {@code SUPER_ADMIN} otherwise).
 * {@code tenantId} is required unless the created user is a {@code SUPER_ADMIN}, which is
 * validated in the service since it depends on the computed role rather than on the request shape alone.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = UserCreateRequest.Builder.class)
public class UserCreateRequest {

    private String firstName;

    private String lastName;

    @NotNull
    private String email;

    @NotNull
    private String password;

    private String tenantId;

    /**
     * Builder for {@link UserCreateRequest}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final UserCreateRequest request;

        private Builder() {
            request = new UserCreateRequest();
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
         * Sets the tenant this user belongs to.
         *
         * @param tenantId tenant identifier
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            request.tenantId = tenantId;
            return this;
        }

        /**
         * Validates and builds request instance.
         *
         * @return built request
         * @throws ValidationException if required fields are missing
         */
        public UserCreateRequest build() {
            Set<ConstraintViolation<UserCreateRequest>> violations =
                    Validation.buildDefaultValidatorFactory().getValidator().validate(request);
            if (violations.isEmpty()) {
                return request;
            }
            throw new ValidationException("UserCreateRequest - " +
                    violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }
}
