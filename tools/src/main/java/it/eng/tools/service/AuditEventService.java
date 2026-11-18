package it.eng.tools.service;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.AuditEventTypeDTO;
import it.eng.tools.exception.ResourceNotFoundException;
import it.eng.tools.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Service
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    public AuditEventService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Fetch all audit events based on filter passed.
     * Tenant aware method; will apply tenantId to filter only tenant related audit events
     *
     * @param filters Filters used in request
     * @param pageable page
     * @return List of audit events
     */
    public Page<AuditEvent> getAuditEvents(Map<String, Object> filters, Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            filters.put("tenantId", tenantId);
        }
        return auditEventRepository.findWithDynamicFilters(filters, AuditEvent.class, pageable);
    }

    public Collection<AuditEventTypeDTO> getAuditEventTypes() {
        return Arrays.stream(AuditEventType.values())
                .map(eventType -> new AuditEventTypeDTO(eventType.name(), eventType.toString()))
                .toList();
    }

    /**
     * Find an audit event by id, scoped to the current tenant when one is set.
     *
     * @param auditEventId the audit event id
     * @return the audit event
     * @throws ResourceNotFoundException if the event does not exist (for the current tenant)
     */
    public AuditEvent getAuditEventById(String auditEventId) {
        String tenantId = TenantContextHolder.getTenantId();
        Optional<AuditEvent> auditEvent = tenantId != null
                ? auditEventRepository.findByIdAndTenantId(auditEventId, tenantId)
                : auditEventRepository.findById(auditEventId);
        return auditEvent
                .orElseThrow(() -> new ResourceNotFoundException("Audit event with id " + auditEventId + " not found"));
    }
}
