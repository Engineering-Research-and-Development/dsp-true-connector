package it.eng.tools.repository;

import it.eng.tools.event.AuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditEventRepository extends MongoRepository<AuditEvent, String>, GenericDynamicFilterRepository<AuditEvent, String> {

    /**
     * Find an audit event by id restricted to the given tenant.
     *
     * @param id       the audit event id
     * @param tenantId the tenant identifier
     * @return an Optional containing the audit event if it exists for the tenant
     */
    Optional<AuditEvent> findByIdAndTenantId(String id, String tenantId);

}
