package it.eng.dcp.common.service.audit;

import it.eng.dcp.common.audit.DcpAuditEvent;
import it.eng.dcp.common.audit.DcpAuditProperties;
import it.eng.dcp.common.repository.DcpAuditEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchronous Spring event listener that persists {@link DcpAuditEvent} instances
 * to MongoDB.
 *
 * <p>Uses {@link DcpAuditEventRepository} when the configured collection name matches
 * the default ({@value DcpAuditEvent#DEFAULT_COLLECTION}), consistent with how all
 * other services interact with MongoDB. Falls back to {@code MongoTemplate.save(entity,
 * collectionName)} only when a custom collection is configured (e.g. {@code audit_events}
 * to share the collection with the connector frontend).
 *
 * <p>All exceptions are caught and logged so that an audit persistence failure
 * never propagates back to the calling thread and disrupts normal application flow.
 *
 * <p>Active only when {@code dcp.audit.enabled=true} (the default).
 */
@Component
@ConditionalOnProperty(prefix = "dcp.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DcpAuditEventListener {

    private final DcpAuditEventRepository auditEventRepository;
    private final MongoTemplate mongoTemplate;
    private final DcpAuditProperties auditProperties;

    public DcpAuditEventListener(DcpAuditEventRepository auditEventRepository,
                                 MongoTemplate mongoTemplate,
                                 DcpAuditProperties auditProperties) {
        this.auditEventRepository = auditEventRepository;
        this.mongoTemplate = mongoTemplate;
        this.auditProperties = auditProperties;
    }

    /**
     * Handles a {@link DcpAuditEvent} published via the Spring
     * {@link org.springframework.context.ApplicationEventPublisher}.
     *
     * <p>Annotated with {@link EventListener} and {@link Async} so that the event
     * is processed on a separate thread and never blocks the publishing service.
     * Plain {@code @EventListener} is used instead of {@code @TransactionalEventListener}
     * because DCP services do not run inside Spring-managed transactions; combining
     * {@code @Async} with {@code @TransactionalEventListener} would silently suppress
     * delivery when no transaction is active.
     *
     * @param event the audit event to persist
     */
    @Async
    @EventListener
    public void handleAuditEvent(DcpAuditEvent event) {
        try {
            String collectionName = auditProperties.getCollectionName();
            if (DcpAuditEvent.DEFAULT_COLLECTION.equals(collectionName)) {
                auditEventRepository.save(event);
            } else {
                mongoTemplate.save(event, collectionName);
            }
            log.debug("DCP audit event persisted: type={}, source={}, collection={}",
                    event.getEventType(), event.getSource(), collectionName);
        } catch (Exception e) {
            log.error("Failed to persist DCP audit event type={}: {}",
                    event.getEventType(), e.getMessage(), e);
        }
    }
}
