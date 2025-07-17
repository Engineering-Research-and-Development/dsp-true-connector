package it.eng.tools.rest.api;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.AuditEventTypeDTO;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventService;
import it.eng.tools.service.GenericFilterBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditEventControllerTest {

    @Mock
    private GenericFilterBuilder filterBuilder;
    @Mock
    private AuditEventService auditEventService;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AuditEventController auditEventController;

    private Map<String, Object> filters;
    private List<AuditEvent> auditEvents;

    @BeforeEach
    void setUp() {
        filters = new HashMap<>();
        filters.put("user", "testUser");
        auditEvents = List.of(AuditEvent.Builder.newInstance()
                .description("test description")
                .eventType(AuditEventType.APPLICATION_START)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("getAuditEvents should return audit events with success response")
    void getAuditEvents_shouldReturnAuditEventsWithSuccessResponse() {
        when(filterBuilder.buildFromRequest(request)).thenReturn(filters);
        when(auditEventService.getAuditEvents(filters)).thenReturn(auditEvents);

        ResponseEntity<GenericApiResponse<Collection<AuditEvent>>> response =
                auditEventController.getAuditEvents(request);

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(auditEvents, response.getBody().getData());
        assertTrue(response.getBody().getMessage().contains("user:testUser"));
        verify(filterBuilder).buildFromRequest(request);
        verify(auditEventService).getAuditEvents(filters);
    }

    @Test
    @DisplayName("getAuditEventTypes should return audit event types with success response")
    public void getAuditEventTypes_shouldReturnAuditEventTypesWithSuccessResponse() {
        List<AuditEventTypeDTO> auditEventTypeDTOS = Arrays.stream(AuditEventType.values())
                .map(eventType -> new AuditEventTypeDTO(eventType.name(), eventType.toString()))
                .toList();
        when(auditEventService.getAuditEventTypes()).thenReturn(auditEventTypeDTOS);

        ResponseEntity<GenericApiResponse<Collection<AuditEventTypeDTO>>> response = auditEventController.getAuditEventTypes();

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Audit event types", response.getBody().getMessage());
        assertEquals(auditEventTypeDTOS, response.getBody().getData());

        verify(auditEventService).getAuditEventTypes();
    }
}
