package it.eng.tools.rest.api;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.AuditEventTypeDTO;
import it.eng.tools.exception.ResourceNotFoundException;
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
    @Mock
    private AuditEventResourceAssembler plainAssembler;

    @InjectMocks
    private AuditEventController auditEventController;

    private Map<String, Object> filters;
    private Page<AuditEvent> auditEventPage;

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
        auditEventPage = new PageImpl<>(auditEventsList, pageable, auditEventsList.size());
    }

    @Test
    @DisplayName("getAuditEvents should return audit events with success response")
    void getAuditEvents_shouldReturnAuditEventsWithSuccessResponse() {
        PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(20, 0, 2, 1);
        List<EntityModel<AuditEvent>> content = auditEventPage.getContent().stream()
                .map(EntityModel::of)
                .collect(Collectors.toList());
        PagedModel<EntityModel<AuditEvent>> pagedModel = PagedModel.of(content, metadata);

        when(filterBuilder.buildFromRequest(request)).thenReturn(filters);
        when(auditEventService.getAuditEvents(filters, pageable)).thenReturn(auditEventPage);
        when(pagedResourcesAssembler.toModel(auditEventPage, plainAssembler)).thenReturn((PagedModel) pagedModel);

        ResponseEntity<PagedAPIResponse> response = auditEventController.getAuditEvents(request, 0, 20, new String[]{"timestamp", "desc"});

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getResponse().isSuccess());
        assertEquals(pagedModel, response.getBody().getResponse().getData());

        verify(filterBuilder).buildFromRequest(request);
        verify(auditEventService).getAuditEvents(filters, pageable);
        verify(pagedResourcesAssembler).toModel(auditEventPage, plainAssembler);
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

    @Test
    @DisplayName("getAuditEventById should return audit event with success response")
    public void getAuditEventById_shouldReturnAuditEventWithSuccessResponse() {
        String auditEventId = "12345";
        AuditEvent auditEvent = AuditEvent.Builder.newInstance()
                .id(auditEventId)
                .description("Test event")
                .eventType(AuditEventType.APPLICATION_START)
                .timestamp(LocalDateTime.now())
                .build();

        when(auditEventService.getAuditEventById(auditEventId)).thenReturn(auditEvent);

        ResponseEntity<GenericApiResponse<AuditEvent>> response = auditEventController.getAuditEventById(auditEventId);

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(auditEvent, response.getBody().getData());
        assertEquals(String.format("Audit event with id %s fetched", auditEventId), response.getBody().getMessage());

        verify(auditEventService).getAuditEventById(auditEventId);
    }

    @Test
    @DisplayName("getAuditEventById should return 404 when audit event not found")
    public void getAuditEventById_shouldReturn404WhenAuditEventNotFound() {
        String auditEventId = "12345";
        when(auditEventService.getAuditEventById(auditEventId)).thenThrow(new ResourceNotFoundException("Test error message"));

        assertThrows(ResourceNotFoundException.class, () ->
                auditEventController.getAuditEventById(auditEventId));

        verify(auditEventService).getAuditEventById(auditEventId);
    }
}
