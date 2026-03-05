package it.eng.tools.event;

import it.eng.tools.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private AuditEventListener auditEventListener;

    @BeforeEach
    void setUp() {
        auditEventListener = new AuditEventListener(auditEventRepository);
    }

    // -------------------------------------------------------------------------
    // handleAuditEvent – persistence
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("handleAuditEvent should persist sanitized event to repository")
    void handleAuditEvent_shouldPersistSanitizedEvent() {
        var event = AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.APPLICATION_START)
                .description("test")
                .details(Map.of("key", "value"))
                .build();

        auditEventListener.handleAuditEvent(event);

        verify(auditEventRepository).save(org.mockito.ArgumentMatchers.any(AuditEvent.class));
    }

    // -------------------------------------------------------------------------
    // sanitizeDetails – null event details
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sanitizeDetails: event with null details map is saved unchanged")
    void handleAuditEvent_withNullDetails_savesEventUnchanged() {
        var event = AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.APPLICATION_START)
                .description("no details")
                .build();

        auditEventListener.handleAuditEvent(event);

        var captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertNull(captor.getValue().getDetails());
    }

    @Test
    @DisplayName("sanitizeDetails: event with empty details map is saved unchanged")
    void handleAuditEvent_withEmptyDetails_savesEventUnchanged() {
        var event = AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.APPLICATION_START)
                .description("empty details")
                .details(Map.of())
                .build();

        auditEventListener.handleAuditEvent(event);

        var captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertTrue(captor.getValue().getDetails().isEmpty());
    }

    // -------------------------------------------------------------------------
    // sanitizeDetails – null value inside the details map
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sanitizeDetails: null detail value is stored as null, not as the string \"null\"")
    void sanitizeDetails_nullValue_storedAsNull() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("present", "hello");
        details.put("absent", null);

        var event = AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.APPLICATION_START)
                .description("null value test")
                .details(details)
                .build();

        auditEventListener.handleAuditEvent(event);

        var captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        var savedDetails = captor.getValue().getDetails();
        assertTrue(savedDetails.containsKey("absent"));
        assertNull(savedDetails.get("absent"));
        assertEquals("hello", savedDetails.get("present"));
    }

    // -------------------------------------------------------------------------
    // sanitizeDetails – String passthrough
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sanitizeDetails: String values are stored as-is without re-encoding")
    void sanitizeDetails_stringValue_storedAsIs() {
        var event = AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.APPLICATION_START)
                .description("string passthrough")
                .details(Map.of("msg", "plain text"))
                .build();

        auditEventListener.handleAuditEvent(event);

        var captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        assertEquals("plain text", captor.getValue().getDetails().get("msg"));
    }

    // -------------------------------------------------------------------------
    // sanitizeDetails – complex serialisable object (ZonedDateTime)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sanitizeDetails: ZonedDateTime is serialized to its JSON string representation")
    void sanitizeDetails_zonedDateTime_serializedToJsonString() {
        var zdt = ZonedDateTime.parse("2026-03-05T10:15:30+01:00");

        var event = AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.APPLICATION_START)
                .description("zdt test")
                .details(Map.of("ts", zdt))
                .build();

        auditEventListener.handleAuditEvent(event);

        var captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        Object saved = captor.getValue().getDetails().get("ts");
        assertInstanceOf(String.class, saved);
        // The stored value must be a valid JSON string that Jackson itself produces
        // (not the raw ZonedDateTime toString). Verify round-trip parsability.
        String jsonString = (String) saved;
        assertNotNull(jsonString);
        assertFalse(jsonString.isEmpty());
        // The ISO instant must be contained inside the serialized form
        assertTrue(jsonString.contains("2026-03-05"));
    }

    // -------------------------------------------------------------------------
    // sanitizeDetails – non-serialisable object (fallback to toString)
    // -------------------------------------------------------------------------

    /**
     * A type that deliberately breaks Jackson serialization to exercise the
     * {@code catch (JsonProcessingException)} fallback path in
     * {@code sanitizeDetails}.
     */
    static final class UnserializableValue {
        /** Forces Jackson to fail: the getter throws at serialization time. */
        @com.fasterxml.jackson.annotation.JsonProperty("value")
        public String getValue() {
            throw new RuntimeException("simulated serialization failure");
        }

        @Override
        public String toString() {
            return "UnserializableValue#toString";
        }
    }

    @Test
    @DisplayName("sanitizeDetails: non-serializable value falls back to toString()")
    void sanitizeDetails_nonSerializableValue_fallsBackToToString() {
        var unserializable = new UnserializableValue();

        var event = AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.APPLICATION_START)
                .description("fallback test")
                .details(Map.of("bad", unserializable))
                .build();

        auditEventListener.handleAuditEvent(event);

        var captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        Object saved = captor.getValue().getDetails().get("bad");
        assertEquals("UnserializableValue#toString", saved);
    }

    // -------------------------------------------------------------------------
    // sanitizeDetails – MongoDB compatibility: primitive types
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sanitizeDetails: integer detail is serialized to its JSON numeric string")
    void sanitizeDetails_integerValue_serializedToJsonString() {
        var event = AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.APPLICATION_START)
                .description("int test")
                .details(Map.of("count", 42))
                .build();

        auditEventListener.handleAuditEvent(event);

        var captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        assertEquals("42", captor.getValue().getDetails().get("count"));
    }

    // -------------------------------------------------------------------------
    // handleAuditEvent – exception safety
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("handleAuditEvent does not propagate exceptions thrown by the repository")
    void handleAuditEvent_repositoryException_doesNotPropagate() {
        org.mockito.Mockito.doThrow(new RuntimeException("DB error"))
                .when(auditEventRepository).save(org.mockito.ArgumentMatchers.any());

        var event = AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.APPLICATION_START)
                .description("exception safety")
                .build();

        // Must not throw
        auditEventListener.handleAuditEvent(event);
    }
}

