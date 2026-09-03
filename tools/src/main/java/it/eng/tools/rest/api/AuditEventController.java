package it.eng.tools.rest.api;

import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventTypeDTO;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventService;
import it.eng.tools.service.GenericFilterBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.AUDIT_V1)
@Slf4j
public class AuditEventController {

    private final GenericFilterBuilder filterBuilder;
    private final AuditEventService auditEventService;
    private final PagedResourcesAssembler<AuditEvent> pagedResourcesAssembler;
    private final AuditEventResourceAssembler plainAssembler;

    public AuditEventController(GenericFilterBuilder filterBuilder, AuditEventService auditEventService,
                                PagedResourcesAssembler<AuditEvent> pagedResourcesAssembler, AuditEventResourceAssembler plainAssembler) {
        this.filterBuilder = filterBuilder;
        this.auditEventService = auditEventService;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
        this.plainAssembler = plainAssembler;
    }

    @GetMapping(path = "/{auditEventId}")
    public ResponseEntity<GenericApiResponse<AuditEvent>> getAuditEventById(@PathVariable String auditEventId) {
        log.info("Fetching audit event details for id {}", auditEventId);
        AuditEvent auditEvent = auditEventService.getAuditEventById(auditEventId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(auditEvent,
                        String.format("Audit event with id %s fetched", auditEventId)));
    }

    @GetMapping
    public ResponseEntity<PagedAPIResponse> getAuditEvents(HttpServletRequest request,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(defaultValue = "timestamp,desc") String[] sort) {

        Sort.Direction direction = sort[1].equalsIgnoreCase("desc") ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sorting = Sort.by(direction, sort[0]);
        Pageable pageable = PageRequest.of(page, size, sorting);
        // Build filter map automatically from ALL request parameters
        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        Page<AuditEvent> auditEvents = auditEventService.getAuditEvents(filters, pageable);
        PagedModel<EntityModel<Object>> pagedModel = pagedResourcesAssembler.toModel(auditEvents, plainAssembler);

        String filterString = filters.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(PagedAPIResponse.of(pagedModel,
                        "Audit events - Page " + page + " of " + auditEvents.getTotalPages() + ", Size: " + size +
                                ", Sort: " + sorting + ", Filters: [" + filterString + "]"));

    }

    @GetMapping("/types")
    public ResponseEntity<GenericApiResponse<Collection<AuditEventTypeDTO>>> getAuditEventTypes() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(auditEventService.getAuditEventTypes(), "Audit event types"));
    }
}
