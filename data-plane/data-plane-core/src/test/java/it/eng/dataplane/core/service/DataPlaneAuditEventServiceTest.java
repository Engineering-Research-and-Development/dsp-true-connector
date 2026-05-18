package it.eng.dataplane.core.service;

import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.dataplane.core.model.DataPlaneAuditEvent;
import it.eng.dataplane.core.model.DataPlaneAuditEventType;
import it.eng.dataplane.core.repository.DataPlaneAuditEventRepository;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataPlaneAuditEventService}.
 */
@ExtendWith(MockitoExtension.class)
class DataPlaneAuditEventServiceTest {

    @Mock
    private DataPlaneAuditEventRepository repository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private DataPlaneProperties properties;

    @InjectMocks
    private DataPlaneAuditEventService service;

    // ─── saveEvent ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saveEvent persists an audit event with all provided fields")
    void saveEvent_persistsEventWithAllFields() {
        when(properties.getEndpoint()).thenReturn("http://dp:9090");

        service.saveEvent(DataPlaneAuditEventType.DATAFLOW_STARTED,
                "proc-1", "HttpData-PULL", "Data flow started",
                Map.of("key", "val"));

        ArgumentCaptor<DataPlaneAuditEvent> captor = ArgumentCaptor.forClass(DataPlaneAuditEvent.class);
        verify(repository).save(captor.capture());
        DataPlaneAuditEvent saved = captor.getValue();
        assertEquals(DataPlaneAuditEventType.DATAFLOW_STARTED, saved.getEventType());
        assertEquals("proc-1", saved.getProcessId());
        assertEquals("HttpData-PULL", saved.getTransferType());
        assertEquals("Data flow started", saved.getDescription());
        assertEquals("http://dp:9090", saved.getSource());
    }

    @Test
    @DisplayName("saveEvent with null details does not throw")
    void saveEvent_withNullDetails_doesNotThrow() {
        when(properties.getEndpoint()).thenReturn("http://dp:9090");

        assertDoesNotThrow(() -> service.saveEvent(
                DataPlaneAuditEventType.DP_REGISTRATION_SUCCESS,
                null, null, "Registered", null));

        verify(repository).save(any());
    }

    @Test
    @DisplayName("saveEvent silently swallows repository exceptions")
    void saveEvent_swallowsRepositoryException() {
        when(properties.getEndpoint()).thenReturn("http://dp:9090");
        when(repository.save(any())).thenThrow(new RuntimeException("DB unavailable"));

        assertDoesNotThrow(() -> service.saveEvent(
                DataPlaneAuditEventType.DATAFLOW_FAILED,
                "proc-1", "HttpData-PULL", "Failed", null));
    }

    // ─── getAuditEvents ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getAuditEvents returns page from MongoTemplate with no filters")
    void getAuditEvents_noFilters_returnsAllEvents() {
        DataPlaneAuditEvent event = DataPlaneAuditEvent.Builder.newInstance()
                .eventType(DataPlaneAuditEventType.DATAFLOW_STARTED).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(mongoTemplate.count(any(Query.class), eq(DataPlaneAuditEvent.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(DataPlaneAuditEvent.class))).thenReturn(List.of(event));

        Page<DataPlaneAuditEvent> result = service.getAuditEvents(Map.of(), pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("getAuditEvents with null filter map returns all events")
    void getAuditEvents_nullFilters_returnsAllEvents() {
        Pageable pageable = PageRequest.of(0, 20);
        when(mongoTemplate.count(any(Query.class), eq(DataPlaneAuditEvent.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(DataPlaneAuditEvent.class))).thenReturn(List.of());

        Page<DataPlaneAuditEvent> result = service.getAuditEvents(null, pageable);

        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("getAuditEvents with eventType filter applies criteria")
    void getAuditEvents_withEventTypeFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        when(mongoTemplate.count(any(Query.class), eq(DataPlaneAuditEvent.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(DataPlaneAuditEvent.class))).thenReturn(List.of());

        Page<DataPlaneAuditEvent> result = service.getAuditEvents(
                Map.of("eventType", "Data flow started"), pageable);

        assertEquals(0, result.getTotalElements());
        verify(mongoTemplate).count(any(Query.class), eq(DataPlaneAuditEvent.class));
    }

    @Test
    @DisplayName("getAuditEvents with all three filters applies all criteria")
    void getAuditEvents_allFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        when(mongoTemplate.count(any(Query.class), eq(DataPlaneAuditEvent.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(DataPlaneAuditEvent.class))).thenReturn(List.of());

        service.getAuditEvents(
                Map.of("eventType", "Data flow started",
                        "processId", "proc-1",
                        "transferType", "HttpData-PULL"),
                pageable);

        verify(mongoTemplate).count(any(Query.class), eq(DataPlaneAuditEvent.class));
    }

    // ─── getById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById delegates to repository and returns Optional")
    void getById_returnsEventFromRepository() {
        DataPlaneAuditEvent event = DataPlaneAuditEvent.Builder.newInstance()
                .eventType(DataPlaneAuditEventType.DATAFLOW_COMPLETED).build();
        when(repository.findById("id-1")).thenReturn(Optional.of(event));

        Optional<DataPlaneAuditEvent> result = service.getById("id-1");

        assertTrue(result.isPresent());
        assertEquals(event, result.get());
    }

    @Test
    @DisplayName("getById returns empty when not found")
    void getById_returnsEmptyWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        Optional<DataPlaneAuditEvent> result = service.getById("missing");

        assertTrue(result.isEmpty());
    }

    // ─── getEventTypes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEventTypes returns all DataPlaneAuditEventType values as name/description pairs")
    void getEventTypes_returnsAllTypes() {
        Collection<Map<String, String>> types = service.getEventTypes();

        assertEquals(DataPlaneAuditEventType.values().length, types.size());
        types.forEach(entry -> {
            assertNotNull(entry.get("name"));
            assertNotNull(entry.get("description"));
        });
    }

    @Test
    @DisplayName("getEventTypes includes DATAFLOW_STARTED with correct description")
    void getEventTypes_includesDataflowStarted() {
        Collection<Map<String, String>> types = service.getEventTypes();

        boolean found = types.stream()
                .anyMatch(t -> "DATAFLOW_STARTED".equals(t.get("name"))
                        && "Data flow started".equals(t.get("description")));
        assertTrue(found);
    }
}
