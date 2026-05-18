package it.eng.dataplane.core.controller;

import it.eng.dataplane.core.DataPlaneApiEndpoints;
import it.eng.dataplane.core.model.DataPlaneAuditEvent;
import it.eng.dataplane.core.model.DataPlaneAuditEventType;
import it.eng.dataplane.core.service.DataPlaneAuditEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataPlaneAuditEventController}.
 */
@ExtendWith(MockitoExtension.class)
class DataPlaneAuditEventControllerTest {

    @Mock
    private DataPlaneAuditEventService auditEventService;

    @InjectMocks
    private DataPlaneAuditEventController controller;

    private DataPlaneAuditEvent buildEvent() {
        return DataPlaneAuditEvent.Builder.newInstance()
                .eventType(DataPlaneAuditEventType.DATAFLOW_STARTED)
                .processId("proc-1")
                .transferType("HttpData-PULL")
                .build();
    }

    // ─── getAuditEvents ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getAuditEvents returns 200 with paginated response body")
    void getAuditEvents_returns200WithBody() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<DataPlaneAuditEvent> page = new PageImpl<>(List.of(buildEvent()), pageable, 1);
        when(auditEventService.getAuditEvents(any(), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Map<String, Object>> response = controller.getAuditEvents(
                null, null, null, 0, 20, new String[]{"timestamp", "desc"});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.get("page"));
        assertEquals(20, body.get("size"));
        assertEquals(1L, body.get("totalElements"));
    }

    @Test
    @DisplayName("getAuditEvents passes non-blank filters to service")
    void getAuditEvents_passesFiltersToService() {
        Page<DataPlaneAuditEvent> empty = Page.empty();
        ArgumentCaptor<Map<String, String>> filtersCaptor = ArgumentCaptor.forClass(Map.class);
        when(auditEventService.getAuditEvents(filtersCaptor.capture(), any())).thenReturn(empty);

        controller.getAuditEvents("Data flow started", "proc-1", "HttpData-PULL",
                0, 20, new String[]{"timestamp", "desc"});

        Map<String, String> filters = filtersCaptor.getValue();
        assertEquals("Data flow started", filters.get("eventType"));
        assertEquals("proc-1", filters.get("processId"));
        assertEquals("HttpData-PULL", filters.get("transferType"));
    }

    @Test
    @DisplayName("getAuditEvents ignores blank filter values")
    void getAuditEvents_ignoresBlankFilters() {
        Page<DataPlaneAuditEvent> empty = Page.empty();
        ArgumentCaptor<Map<String, String>> filtersCaptor = ArgumentCaptor.forClass(Map.class);
        when(auditEventService.getAuditEvents(filtersCaptor.capture(), any())).thenReturn(empty);

        controller.getAuditEvents("  ", "", null, 0, 20, new String[]{"timestamp", "asc"});

        Map<String, String> filters = filtersCaptor.getValue();
        assertFalse(filters.containsKey("eventType"));
        assertFalse(filters.containsKey("processId"));
        assertFalse(filters.containsKey("transferType"));
    }

    @Test
    @DisplayName("getAuditEvents uses ASC sort when sort[1] is asc")
    void getAuditEvents_usesAscSort() {
        when(auditEventService.getAuditEvents(any(), any())).thenReturn(Page.empty());

        ResponseEntity<Map<String, Object>> response = controller.getAuditEvents(
                null, null, null, 0, 10, new String[]{"timestamp", "asc"});

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("getAuditEvents defaults to DESC sort when sort has only one element")
    void getAuditEvents_defaultsToDescSort() {
        when(auditEventService.getAuditEvents(any(), any())).thenReturn(Page.empty());

        ResponseEntity<Map<String, Object>> response = controller.getAuditEvents(
                null, null, null, 0, 10, new String[]{"timestamp"});

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ─── getAuditEventById ────────────────────────────────────────────────────

    @Test
    @DisplayName("getAuditEventById returns 200 with event when found")
    void getAuditEventById_returns200WhenFound() {
        DataPlaneAuditEvent event = buildEvent();
        when(auditEventService.getById("audit-1")).thenReturn(Optional.of(event));

        ResponseEntity<Map<String, Object>> response = controller.getAuditEventById("audit-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(event, response.getBody().get("content"));
    }

    @Test
    @DisplayName("getAuditEventById returns 404 when not found")
    void getAuditEventById_returns404WhenNotFound() {
        when(auditEventService.getById("missing")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.getAuditEventById("missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ─── getAuditEventTypes ───────────────────────────────────────────────────

    @Test
    @DisplayName("getAuditEventTypes returns 200 with event types list")
    void getAuditEventTypes_returns200WithTypes() {
        Collection<Map<String, String>> types = List.of(
                Map.of("name", "DATAFLOW_STARTED", "description", "Data flow started"));
        when(auditEventService.getEventTypes()).thenReturn(types);

        ResponseEntity<Map<String, Object>> response = controller.getAuditEventTypes();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(types, response.getBody().get("content"));
    }
}
