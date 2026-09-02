package it.eng.dataplane.core.controller;

import it.eng.dataplane.core.DataPlaneApiEndpoints;
import it.eng.dataplane.core.model.DataPlaneAuditEvent;
import it.eng.dataplane.core.service.DataPlaneAuditEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller exposing Data Plane audit event query endpoints.
 * All endpoints require API-key authentication via the {@code X-Api-Key} header.
 *
 * <p>Base path: {@code /api/v1/audit}
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(path = DataPlaneApiEndpoints.AUDIT_EVENTS_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class DataPlaneAuditEventController {

    private final DataPlaneAuditEventService auditEventService;

    /**
     * Returns a paginated list of audit events with optional filtering.
     *
     * <p>Supported query parameters:
     * <ul>
     *   <li>{@code eventType} — filter by event type description (e.g. {@code Data flow started})</li>
     *   <li>{@code processId} — filter by transfer process ID</li>
     *   <li>{@code transferType} — filter by transfer type (e.g. {@code HttpData-PULL})</li>
     *   <li>{@code page} — zero-based page index (default: 0)</li>
     *   <li>{@code size} — page size (default: 20)</li>
     *   <li>{@code sort} — sort field and direction, e.g. {@code timestamp,desc} (default)</li>
     * </ul>
     *
     * @param eventType    optional event type filter
     * @param processId    optional process ID filter
     * @param transferType optional transfer type filter
     * @param page         zero-based page index
     * @param size         page size
     * @param sort         sort field and direction ({@code field,asc|desc})
     * @return paginated audit events
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAuditEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String processId,
            @RequestParam(required = false) String transferType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp,desc") String[] sort) {

        Map<String, String> filters = new HashMap<>();
        if (eventType != null && !eventType.isBlank()) {
            filters.put("eventType", eventType);
        }
        if (processId != null && !processId.isBlank()) {
            filters.put("processId", processId);
        }
        if (transferType != null && !transferType.isBlank()) {
            filters.put("transferType", transferType);
        }

        Sort.Direction direction = sort.length > 1 && sort[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));

        Page<DataPlaneAuditEvent> result = auditEventService.getAuditEvents(filters, pageable);

        Map<String, Object> body = new HashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        body.put("message", "Audit events - page " + page + " of " + result.getTotalPages());

        return ResponseEntity.ok(body);
    }

    /**
     * Returns a single audit event by its MongoDB document ID.
     *
     * @param auditEventId the document ID
     * @return the audit event or 404 if not found
     */
    @GetMapping("/{auditEventId}")
    public ResponseEntity<Map<String, Object>> getAuditEventById(@PathVariable String auditEventId) {
        log.info("Fetching DP audit event by id={}", auditEventId);
        return auditEventService.getById(auditEventId)
                .map(event -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("content", event);
                    body.put("message", "Audit event " + auditEventId);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Returns all supported Data Plane audit event types with their descriptions.
     *
     * @return list of event type name/description pairs
     */
    @GetMapping("/types")
    public ResponseEntity<Map<String, Object>> getAuditEventTypes() {
        Collection<Map<String, String>> types = auditEventService.getEventTypes();
        Map<String, Object> body = new HashMap<>();
        body.put("content", types);
        body.put("message", "Data Plane audit event types");
        return ResponseEntity.ok(body);
    }
}
