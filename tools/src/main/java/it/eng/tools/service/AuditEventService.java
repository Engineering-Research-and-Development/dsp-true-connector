package it.eng.tools.service;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.AuditEventTypeDTO;
import it.eng.tools.repository.AuditEventRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

@Service
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    public AuditEventService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public Collection<AuditEvent> getAuditEvents(Map<String, Object> filters) {

        return auditEventRepository.findWithDynamicFilters(filters, AuditEvent.class);
    }

    public Collection<AuditEventTypeDTO> getAuditEventTypes() {
        return Arrays.stream(AuditEventType.values())
                .map(eventType -> new AuditEventTypeDTO(eventType.name(), eventType.toString()))
                .toList();
    }
}
