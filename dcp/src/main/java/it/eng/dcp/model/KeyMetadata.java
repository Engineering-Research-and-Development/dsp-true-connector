package it.eng.dcp.model;

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
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@JsonDeserialize(builder = KeyMetadata.Builder.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "key_metadata")
public class KeyMetadata {

    @Id
    private String id;
    @NotNull
    private String alias;
    private Instant createdAt;
    private boolean active;
    private boolean archived;
    private Instant archivedAt;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final KeyMetadata metadata;

        private Builder() {
            metadata = new KeyMetadata();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            metadata.id = id;
            return this;
        }

        public Builder alias(String alias) {
            metadata.alias = alias;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            metadata.createdAt = createdAt;
            return this;
        }

        public Builder active(boolean active) {
            metadata.active = active;
            return this;
        }

        public Builder archived(boolean archived) {
            metadata.archived = archived;
            return this;
        }

        public Builder archivedAt(Instant archivedAt) {
            metadata.archivedAt = archivedAt;
            return this;
        }

        public KeyMetadata build() {
            // generate id if missing
            if (metadata.id == null) {
                metadata.id = "urn:uuid:" + UUID.randomUUID();
            }

            if (metadata.createdAt == null) {
                metadata.createdAt = Instant.now();
            }

            // if marked archived but no archivedAt provided, set now
            if (metadata.archived && metadata.archivedAt == null) {
                metadata.archivedAt = Instant.now();
            }

            Set<ConstraintViolation<KeyMetadata>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(metadata);
            if (violations.isEmpty()) {
                return metadata;
            }
            throw new ValidationException("KeyMetadata - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }
}
