package it.eng.tools.auth.keycloak.realm;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.s3.encrypt.Encrypted;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Keycloak realm client credentials for a tenant, keyed by tenant id.
 * The client secret is stored encrypted at rest and is never included in {@link #toString()}.
 */
@Document(collection = "realm_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = RealmCredentials.Builder.class)
public class RealmCredentials {

    @Id
    @NotNull
    private String tenantId;

    @Encrypted
    @NotNull
    @JsonIgnore
    private String clientSecret;

    @CreatedDate
    private Instant issued;

    @LastModifiedDate
    private Instant modified;

    @JsonIgnore
    @Version
    @Field("version")
    private Long version;

    @Override
    public String toString() {
        return "RealmCredentials{tenantId='" + tenantId + "'}";
    }

    /**
     * Builder for {@link RealmCredentials} with validation.
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private final RealmCredentials credentials;

        private Builder() {
            credentials = new RealmCredentials();
        }

        /**
         * Creates a new builder.
         *
         * @return a new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets the tenant id.
         *
         * @param tenantId the tenant identifier
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            credentials.tenantId = tenantId;
            return this;
        }

        /**
         * Sets the client secret.
         *
         * @param clientSecret the realm client secret
         * @return this builder
         */
        public Builder clientSecret(String clientSecret) {
            credentials.clientSecret = clientSecret;
            return this;
        }

        /**
         * Sets the creation timestamp.
         *
         * @param issued the creation instant
         * @return this builder
         */
        public Builder issued(Instant issued) {
            credentials.issued = issued;
            return this;
        }

        /**
         * Sets the last-modified timestamp.
         *
         * @param modified the last-modified instant
         * @return this builder
         */
        public Builder modified(Instant modified) {
            credentials.modified = modified;
            return this;
        }

        /**
         * Sets the optimistic-locking version.
         *
         * @param version the version number
         * @return this builder
         */
        public Builder version(Long version) {
            credentials.version = version;
            return this;
        }

        /**
         * Validates and builds the credentials.
         *
         * @return the validated credentials
         * @throws ValidationException if required fields are missing
         */
        public RealmCredentials build() {
            Set<ConstraintViolation<RealmCredentials>> violations =
                    Validation.buildDefaultValidatorFactory().getValidator().validate(credentials);
            if (violations.isEmpty()) {
                return credentials;
            }
            throw new ValidationException("RealmCredentials - " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(",")));
        }
    }
}
