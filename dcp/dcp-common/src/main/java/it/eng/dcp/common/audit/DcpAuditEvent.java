package it.eng.dcp.common.audit;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a single DCP audit event that is persisted to MongoDB.
 *
 * <p>Mapped to the {@value DEFAULT_COLLECTION} collection by default via {@link Document}.
 * {@link it.eng.dcp.common.service.audit.DcpAuditEventListener} uses
 * {@link it.eng.dcp.common.repository.DcpAuditEventRepository} for saving when the
 * configured collection name matches the default, and falls back to
 * {@code MongoTemplate.save(entity, collectionName)} when a different collection is
 * configured (e.g. {@code audit_events} to share with the connector).
 *
 * <p>Use {@link Builder#newInstance()} to construct instances:
 * <pre>{@code
 * DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
 *         .eventType(DcpAuditEventType.CREDENTIAL_SAVED)
 *         .description("Credential MembershipCredential saved")
 *         .source("holder")
 *         .holderDid("did:web:holder.example.com")
 *         .issuerDid("did:web:issuer.example.com")
 *         .credentialTypes(List.of("MembershipCredential"))
 *         .requestId("req-abc-123")
 *         .build();
 * }</pre>
 */
@Document(collection = DcpAuditEvent.DEFAULT_COLLECTION)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DcpAuditEvent {

    /** Default MongoDB collection name — matches {@link it.eng.dcp.common.audit.DcpAuditProperties#getCollectionName()} default. */
    public static final String DEFAULT_COLLECTION = "dcp_audit_events";

    /** MongoDB document identifier — assigned by the listener before saving. */
    @Id
    private String id;

    /** The type of DCP operation that triggered this event. Must not be {@code null}. */
    @NotNull
    private DcpAuditEventType eventType;

    /** UTC timestamp set automatically in {@link Builder#build()}. */
    private Instant timestamp;

    /** Human-readable summary of what happened. */
    private String description;

    /** Flexible key/value map for any additional structured data relevant to the event. */
    private Map<String, Object> details;

    /**
     * The module that emitted this event.
     * Typical values: {@code "issuer"}, {@code "holder"}, {@code "verifier"}.
     */
    private String source;

    /** DID of the credential holder involved in the event (when applicable). */
    private String holderDid;

    /** DID of the credential issuer involved in the event (when applicable). */
    private String issuerDid;

    /**
     * List of credential type identifiers involved in the event
     * (e.g. {@code ["MembershipCredential"]}).
     */
    private List<String> credentialTypes;

    /**
     * Correlation identifier for the underlying DCP request.
     * Maps to {@code issuerPid} or {@code holderPid} depending on context.
     */
    private String requestId;

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Builder for {@link DcpAuditEvent}. */
    public static class Builder {

        private final DcpAuditEvent event;

        private Builder() {
            event = new DcpAuditEvent();
        }

        /** Creates a new {@link Builder} instance.
         * @return Builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            event.id = id;
            return this;
        }

        public Builder eventType(DcpAuditEventType eventType) {
            event.eventType = eventType;
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

        public Builder holderDid(String holderDid) {
            event.holderDid = holderDid;
            return this;
        }

        public Builder issuerDid(String issuerDid) {
            event.issuerDid = issuerDid;
            return this;
        }

        public Builder credentialTypes(List<String> credentialTypes) {
            event.credentialTypes = credentialTypes;
            return this;
        }

        public Builder requestId(String requestId) {
            event.requestId = requestId;
            return this;
        }

        /**
         * Validates all {@link jakarta.validation.constraints} annotations and sets
         * {@link DcpAuditEvent#timestamp} to the current UTC instant.
         *
         * @return the built {@link DcpAuditEvent}
         * @throws ValidationException if any constraint is violated
         */
        public DcpAuditEvent build() {
            Set<ConstraintViolation<DcpAuditEvent>> violations =
                    Validation.buildDefaultValidatorFactory().getValidator().validate(event);
            if (!violations.isEmpty()) {
                throw new ValidationException("DcpAuditEvent - " +
                        violations.stream()
                                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                                .collect(Collectors.joining(",")));
            }
            event.timestamp = Instant.now();
            return event;
        }
    }
}

