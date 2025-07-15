package it.eng.tools.repository;

import it.eng.tools.event.AuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository extends MongoRepository<AuditEvent, String>, GenericDynamicFilterRepository<AuditEvent, String> {

}
