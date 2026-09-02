package it.eng.dataplane.core.repository;

import it.eng.dataplane.core.model.DataPlaneAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB repository for {@link DataPlaneAuditEvent} documents.
 * Dynamic multi-field filtering is handled via {@code MongoTemplate} in the service layer.
 */
@Repository
public interface DataPlaneAuditEventRepository extends MongoRepository<DataPlaneAuditEvent, String> {
}
