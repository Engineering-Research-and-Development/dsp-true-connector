package it.eng.dataplane.core.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Audit event document for the Data Plane.
 * Stored in the {@code dp_audit_events} MongoDB collection, separate from the
 * Control Plane {@code audit_events} collection, because the Data Plane runs as an
 * independent service with its own database connection.
 */
@Getter
@JsonDeserialize(builder = DataPlaneAuditEvent.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {"timestamp", "eventType", "processId", "transferType"}, alphabetic = true)
@Document(collection = "dp_audit_events")
public class DataPlaneAuditEvent {

    @Id
    private String id;

    @NotNull
    private DataPlaneAuditEventType eventType;

    /** Transfer process ID from the Control Plane, when applicable. */
    private String processId;

    /** Transfer type, e.g. {@code HttpData-PULL} or {@code HttpData-PUSH}. */
    private String transferType;

    private String description;

    private LocalDateTime timestamp;

    /** Additional context (e.g. errorMessage, bucket, objectKey). */
    private Map<String, String> details;

    /** Endpoint URL of this Data Plane instance. */
    private String source;

    /**
     * Builder for {@link DataPlaneAuditEvent}.
     */
    public static class Builder {

        private final DataPlaneAuditEvent event;

        /**
         * Creates a new builder instance.
         *
         * @return new Builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        private Builder() {
            event = new DataPlaneAuditEvent();
        }

        /**
         * Sets the document ID.
         *
         * @param id document ID
         * @return this builder
         */
        public Builder id(String id) {
            event.id = id;
            return this;
        }

        /**
         * Sets the event type.
         *
         * @param eventType the type of audit event
         * @return this builder
         */
        public Builder eventType(DataPlaneAuditEventType eventType) {
            event.eventType = eventType;
            return this;
        }

        /**
         * Sets the transfer process ID.
         *
         * @param processId transfer process ID from the Control Plane
         * @return this builder
         */
        public Builder processId(String processId) {
            event.processId = processId;
            return this;
        }

        /**
         * Sets the transfer type.
         *
         * @param transferType e.g. {@code HttpData-PULL} or {@code HttpData-PUSH}
         * @return this builder
         */
        public Builder transferType(String transferType) {
            event.transferType = transferType;
            return this;
        }

        /**
         * Sets the human-readable description.
         *
         * @param description event description
         * @return this builder
         */
        public Builder description(String description) {
            event.description = description;
            return this;
        }

        /**
         * Sets the event timestamp. Defaults to {@link LocalDateTime#now()} when not set.
         *
         * @param timestamp event timestamp
         * @return this builder
         */
        public Builder timestamp(LocalDateTime timestamp) {
            event.timestamp = timestamp;
            return this;
        }

        /**
         * Sets additional key/value details for this event.
         *
         * @param details map of extra context information
         * @return this builder
         */
        public Builder details(Map<String, String> details) {
            event.details = details;
            return this;
        }

        /**
         * Sets the source (Data Plane endpoint URL).
         *
         * @param source the DP endpoint URL
         * @return this builder
         */
        public Builder source(String source) {
            event.source = source;
            return this;
        }

        /**
         * Builds and validates the {@link DataPlaneAuditEvent}.
         *
         * @return validated event
         * @throws ValidationException if required fields are missing
         */
        public DataPlaneAuditEvent build() {
            Set<ConstraintViolation<DataPlaneAuditEvent>> violations =
                    Validation.buildDefaultValidatorFactory().getValidator().validate(event);
            if (violations.isEmpty()) {
                if (event.timestamp == null) {
                    event.timestamp = LocalDateTime.now();
                }
                return event;
            }
            throw new ValidationException("DataPlaneAuditEvent - " +
                    violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }
}
