package it.eng.tools.service;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditEventServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditEventService auditEventService;

    private Map<String, Object> filters;
    private List<AuditEvent> expectedEvents;

    @BeforeEach
    void setUp() {
        filters = new HashMap<>();
        filters.put("eventType", AuditEventType.APPLICATION_START);

        expectedEvents = List.of(
                AuditEvent.Builder.newInstance()
                        .description("Test event")
                        .eventType(AuditEventType.APPLICATION_START)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Test
    void getAuditEvents_shouldReturnEventsFromRepository() {
        when(auditEventRepository.findWithDynamicFilters(filters, AuditEvent.class))
                .thenReturn(expectedEvents);

        Collection<AuditEvent> actualEvents = auditEventService.getAuditEvents(filters);

        assertNotNull(actualEvents);
        assertEquals(expectedEvents, actualEvents);
        verify(auditEventRepository).findWithDynamicFilters(filters, AuditEvent.class);
    }

    @Test
    void getAuditEvents_withEmptyFilters_shouldReturnEventsFromRepository() {
        Map<String, Object> emptyFilters = new HashMap<>();
        when(auditEventRepository.findWithDynamicFilters(emptyFilters, AuditEvent.class))
                .thenReturn(expectedEvents);

        Collection<AuditEvent> actualEvents = auditEventService.getAuditEvents(emptyFilters);

        assertNotNull(actualEvents);
        assertEquals(expectedEvents, actualEvents);
        verify(auditEventRepository).findWithDynamicFilters(emptyFilters, AuditEvent.class);
    }
}
