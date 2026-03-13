package it.eng.dcp.common.repository;

import it.eng.dcp.common.audit.DcpAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for {@link DcpAuditEvent} documents.
 *
 * <p>This is the primary persistence mechanism for audit events when the default
 * collection ({@value DcpAuditEvent#DEFAULT_COLLECTION}) is in use.
 * {@link it.eng.dcp.common.service.audit.DcpAuditEventListener} falls back to
 * {@code MongoTemplate.save(entity, collectionName)} only when a custom collection
 * is configured via {@code dcp.audit.collection-name}.
 */
@Repository
public interface DcpAuditEventRepository extends MongoRepository<DcpAuditEvent, String> {
}
