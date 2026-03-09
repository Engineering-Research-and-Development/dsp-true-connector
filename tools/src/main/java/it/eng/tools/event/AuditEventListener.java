package it.eng.tools.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.eng.tools.repository.AuditEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class AuditEventListener {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public AuditEventListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Handles an audit event by sanitizing its details and persisting it to MongoDB.
     * Details values are converted to their JSON string representation so that
     * MongoDB never encounters uncodeable types (e.g. {@code ZonedDateTime} embedded
     * inside domain objects stored in the flexible {@code Map<String, Object>} field).
     *
     * @param event the audit event to persist
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleAuditEvent(AuditEvent event) {
        try {
            AuditEvent sanitized = sanitizeDetails(event);
            auditEventRepository.save(sanitized);
        } catch (Exception e) {
            log.error("Failed to persist audit event of type {}: {}", event.getEventType(), e.getMessage(), e);
        }
    }

    /**
     * Returns a copy of the event with all details values converted to JSON strings.
     * If the event has no details, the original event is returned unchanged.
     *
     * @param event the audit event to sanitize
     * @return the sanitized audit event
     */
    private AuditEvent sanitizeDetails(AuditEvent event) {
        if (event.getDetails() == null || event.getDetails().isEmpty()) {
            return event;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : event.getDetails().entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                sanitized.put(entry.getKey(), null);
            } else if (value instanceof String) {
                sanitized.put(entry.getKey(), value);
            } else {
                try {
                    JsonNode node = objectMapper.valueToTree(value);
                    // Use asText() for textual scalars (e.g. ZonedDateTime → "2026-03-05T...")
                    // so the value is stored unquoted, consistent with plain String values.
                    // For objects, arrays, numbers, and booleans use toString() to preserve structure.
                    sanitized.put(entry.getKey(), node.isTextual() ? node.asText() : node.toString());
                } catch (IllegalArgumentException e) {
                    log.warn("Could not serialize audit detail '{}', storing toString(): {}", entry.getKey(), e.getMessage());
                    sanitized.put(entry.getKey(), String.valueOf(value));
                }
            }
        }
        return AuditEvent.Builder.newInstance()
                .id(event.getId())
                .eventType(event.getEventType())
                .username(event.getUsername())
                .timestamp(event.getTimestamp())
                .description(event.getDescription())
                .details(sanitized)
                .source(event.getSource())
                .ipAddress(event.getIpAddress())
                .build();
    }
}

