package it.eng.tools.service;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.AuditEventTypeDTO;
import it.eng.tools.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditEventServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private Pageable pageable;

    @InjectMocks
    private AuditEventService auditEventService;

    private Map<String, Object> filters;
    private Page<AuditEvent> expectedEvents;

    @BeforeEach
    void setUp() {
        filters = new HashMap<>();
        filters.put("eventType", AuditEventType.APPLICATION_START);

        List<AuditEvent> auditEvents = List.of(
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
        expectedEvents = new PageImpl<>(auditEvents, pageable, auditEvents.size());
    }

    @Test
    void getAuditEvents_shouldReturnEventsFromRepository() {
        when(auditEventRepository.findWithDynamicFilters(filters, AuditEvent.class, pageable))
                .thenReturn(expectedEvents);

        Page<AuditEvent> actualEvents = auditEventService.getAuditEvents(filters, pageable);

        assertNotNull(actualEvents);
        assertEquals(expectedEvents, actualEvents);
        verify(auditEventRepository).findWithDynamicFilters(filters, AuditEvent.class, pageable);
    }

    @Test
    void getAuditEvents_withEmptyFilters_shouldReturnEventsFromRepository() {
        Map<String, Object> emptyFilters = new HashMap<>();
        when(auditEventRepository.findWithDynamicFilters(emptyFilters, AuditEvent.class, pageable))
                .thenReturn(expectedEvents);

        Page<AuditEvent> actualEvents = auditEventService.getAuditEvents(emptyFilters, pageable);

        assertNotNull(actualEvents);
        assertEquals(expectedEvents, actualEvents);
        verify(auditEventRepository).findWithDynamicFilters(emptyFilters, AuditEvent.class, pageable);
    }

    @Test
    @DisplayName("getAuditEventTypes should return all audit event types")
    public void getAuditEventTypes_shouldReturnAllAuditEventTypes() {
        Collection<AuditEventTypeDTO> auditEventTypes = auditEventService.getAuditEventTypes();

        assertNotNull(auditEventTypes);
        assertEquals(Arrays.stream(AuditEventType.values())
                .map(eventType -> new AuditEventTypeDTO(eventType.name(), eventType.toString()))
                .toList(), auditEventTypes);
    }

    @Test
    @DisplayName("getAuditEventTypes should return all audit event types")
    public void getAuditEventTypes_shouldReturnAllAuditEventTypes() {
        Collection<AuditEventTypeDTO> auditEventTypes = auditEventService.getAuditEventTypes();

        assertNotNull(auditEventTypes);
        assertEquals(Arrays.stream(AuditEventType.values())
                .map(eventType -> new AuditEventTypeDTO(eventType.name(), eventType.toString()))
                .toList(), auditEventTypes);
    }
}
