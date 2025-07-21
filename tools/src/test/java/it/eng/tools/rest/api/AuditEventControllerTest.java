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
import org.springframework.data.domain.*;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    @Mock
    private Pageable pageable;
    @Mock
    private PagedResourcesAssembler<AuditEvent> pagedResourcesAssembler;

    @InjectMocks
    private AuditEventController auditEventController;

    private Map<String, Object> filters;
    private Page<AuditEvent> auditEvents;

    @BeforeEach
    void setUp() {
        filters = new HashMap<>();
        filters.put("user", "testUser");

        List<AuditEvent> auditEventsList = List.of(
                AuditEvent.Builder.newInstance()
                        .description("Test event 1")
                        .eventType(AuditEventType.APPLICATION_START)
                        .timestamp(LocalDateTime.now())
                        .build(),
                AuditEvent.Builder.newInstance()
                        .description("Test event 2")
                        .eventType(AuditEventType.APPLICATION_START)
                        .timestamp(LocalDateTime.now().minusHours(1))
                        .build()
        );
        pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        auditEvents = new PageImpl<>(auditEventsList, pageable, auditEventsList.size());
    }

    @Test
    @DisplayName("getAuditEvents should return audit events with success response")
    void getAuditEvents_shouldReturnAuditEventsWithSuccessResponse() {
        PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(20, 0, 2, 1);
        List<EntityModel<AuditEvent>> content = auditEvents.getContent().stream()
                .map(EntityModel::of)
                .collect(Collectors.toList());
        PagedModel<EntityModel<AuditEvent>> pagedModel = PagedModel.of(content, metadata);

        when(filterBuilder.buildFromRequest(request)).thenReturn(filters);
        when(auditEventService.getAuditEvents(filters, pageable)).thenReturn(auditEvents);
        when(pagedResourcesAssembler.toModel(auditEvents)).thenReturn(pagedModel);

        ResponseEntity<PagedAPIResponse> response = auditEventController.getAuditEvents(request, 0, 20, new String[]{"timestamp", "desc"});

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getResponse().isSuccess());
        assertEquals(pagedModel, response.getBody().getResponse().getData());

        verify(filterBuilder).buildFromRequest(request);
        verify(auditEventService).getAuditEvents(filters, pageable);
        verify(pagedResourcesAssembler).toModel(auditEvents);
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
