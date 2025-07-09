package it.eng.tools.rest.api;

import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventService;
import it.eng.tools.service.GenericFilterBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.AUDIT_V1)
public class AuditEventController {

    private final GenericFilterBuilder filterBuilder;
    private final AuditEventService auditEventService;

    public AuditEventController(GenericFilterBuilder filterBuilder, AuditEventService auditEventService) {
        this.filterBuilder = filterBuilder;
        this.auditEventService = auditEventService;
    }

    @GetMapping
    public ResponseEntity<GenericApiResponse<Collection<AuditEvent>>> getAuditEvents(HttpServletRequest request) {

        // Build filter map automatically from ALL request parameters
        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        Collection<AuditEvent> auditEvents = auditEventService.getAuditEvents(filters);

        String filterString = filters.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(auditEvents, "Audit events for criteria: " + filterString));

    }
}
