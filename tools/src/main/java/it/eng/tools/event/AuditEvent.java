package it.eng.tools.event;

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

@Getter
@JsonDeserialize(builder = AuditEvent.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {"timestamp", "eventType", "tenantId"}, alphabetic = true)
@Document(collection = "audit_events")
public class AuditEvent {

    @Id
    private String id;
    @NotNull
    private AuditEventType eventType;     // LOGIN, ACTION, MODIFICATION, etc.
    private String username;      // who performed the action
    private LocalDateTime timestamp;
    private String description;
    private Map<String, Object> details; // flexible structure for additional data
    private String source;       // component/module where event occurred
    private String ipAddress;
    private String tenantId;

    public static class Builder {
        private final AuditEvent event;

        public static Builder newInstance() {
            return new Builder();
        }

        private Builder() {
            event = new AuditEvent();
        }

        public Builder id(String id) {
            event.id = id;
            return this;
        }

        public Builder eventType(AuditEventType eventType) {
            event.eventType = eventType;
            return this;
        }

        public Builder username(String username) {
            event.username = username;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            event.timestamp = timestamp;
            return this;
        }

        public Builder description(String description) {
            event.description = description;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            event.details = details;
            return this;
        }

        public Builder source(String source) {
            event.source = source;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            event.ipAddress = ipAddress;
            return this;
        }

        /**
         * Sets the tenant identifier for this audit event.
         *
         * @param tenantId the tenant identifier
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            event.tenantId = tenantId;
            return this;
        }

        public AuditEvent build() {
            Set<ConstraintViolation<AuditEvent>> violations =
                    Validation.buildDefaultValidatorFactory().getValidator().validate(event);
            if (violations.isEmpty()) {
                if (event.timestamp == null) {
                    event.timestamp = LocalDateTime.now();
                }
                return event;
            }
            throw new ValidationException("AuditEvent - " +
                    violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }

    /**
     * Directly sets the tenant identifier on this instance, bypassing the builder.
     * Used by the service layer to stamp tenant ownership before persisting.
     *
     * @param tenantId the tenant ID to associate with this catalog
     */
    public void injectTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

}
