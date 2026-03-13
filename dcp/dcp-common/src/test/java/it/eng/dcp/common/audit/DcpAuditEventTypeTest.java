package it.eng.dcp.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DcpAuditEventType} and {@link DcpAuditEvent}.
 */
class DcpAuditEventTypeTest {

    // =========================================================================
    // DcpAuditEventType
    // =========================================================================

    @Nested
    @DisplayName("DcpAuditEventType")
    class EventTypeTests {

        @Test
        @DisplayName("toString() returns the human-readable label")
        void toString_returnsLabel() {
            assertEquals("Credential request received",
                    DcpAuditEventType.CREDENTIAL_REQUEST_RECEIVED.toString());
            assertEquals("Signing key rotated",
                    DcpAuditEventType.KEY_ROTATED.toString());
            assertEquals("Presentation verified",
                    DcpAuditEventType.PRESENTATION_VERIFIED.toString());
        }

        @Test
        @DisplayName("fromLabel() resolves by human-readable label")
        void fromLabel_byLabel() {
            assertSame(DcpAuditEventType.CREDENTIAL_SAVED,
                    DcpAuditEventType.fromLabel("Credential saved"));
            assertSame(DcpAuditEventType.CREDENTIALS_PROCESSED,
                    DcpAuditEventType.fromLabel("Credentials processed"));
            assertSame(DcpAuditEventType.CREDENTIAL_MESSAGE_RECEIVED,
                    DcpAuditEventType.fromLabel("Credential message received"));
            assertSame(DcpAuditEventType.ISSUER_METADATA_FETCHED,
                    DcpAuditEventType.fromLabel("Issuer metadata fetched"));
            assertSame(DcpAuditEventType.CREDENTIAL_REQUEST_FAILED,
                    DcpAuditEventType.fromLabel("Credential request failed"));
            assertSame(DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    DcpAuditEventType.fromLabel("Token validation failed"));
            assertSame(DcpAuditEventType.KEY_ROTATED,
                    DcpAuditEventType.fromLabel("Signing key rotated"));
        }

        @Test
        @DisplayName("All 26 enum constants are present")
        void allConstantsPresent() {
            assertEquals(26, DcpAuditEventType.values().length);
        }

        @Test
        @DisplayName("fromLabel() resolves by enum constant name")
        void fromLabel_byEnumName() {
            assertSame(DcpAuditEventType.CREDENTIAL_APPROVED,
                    DcpAuditEventType.fromLabel("CREDENTIAL_APPROVED"));
            assertSame(DcpAuditEventType.PRESENTATION_INVALID,
                    DcpAuditEventType.fromLabel("PRESENTATION_INVALID"));
        }

        @Test
        @DisplayName("fromLabel() returns null for unknown value")
        void fromLabel_unknownReturnsNull() {
            assertNull(DcpAuditEventType.fromLabel("nonexistent label"));
            assertNull(DcpAuditEventType.fromLabel(""));
            assertNull(DcpAuditEventType.fromLabel(null));
        }


        @Test
        @DisplayName("Each constant has a unique label")
        void uniqueLabels() {
            long uniqueLabels = java.util.Arrays.stream(DcpAuditEventType.values())
                    .map(DcpAuditEventType::toString)
                    .distinct()
                    .count();
            assertEquals(DcpAuditEventType.values().length, uniqueLabels,
                    "Every DcpAuditEventType must have a unique label");
        }

        @Test
        @DisplayName("fromLabel() and toString() are inverse operations for every constant")
        void fromLabelAndToStringAreInverse() {
            for (DcpAuditEventType type : DcpAuditEventType.values()) {
                assertSame(type, DcpAuditEventType.fromLabel(type.toString()),
                        "Round-trip failed for: " + type.name());
            }
        }

        @Test
        @DisplayName("JSON serialization uses the human-readable label")
        void jsonSerializationUsesLabel() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(DcpAuditEventType.CREDENTIAL_SAVED);
            assertEquals("\"Credential saved\"", json);
        }

        @Test
        @DisplayName("JSON deserialization resolves from human-readable label")
        void jsonDeserializationFromLabel() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            DcpAuditEventType type = mapper.readValue("\"Credential saved\"", DcpAuditEventType.class);
            assertSame(DcpAuditEventType.CREDENTIAL_SAVED, type);
        }
    }

    // =========================================================================
    // DcpAuditEvent Builder
    // =========================================================================

    @Nested
    @DisplayName("DcpAuditEvent.Builder")
    class EventBuilderTests {

        @Test
        @DisplayName("build() succeeds with minimum required fields")
        void build_minimumFields() {
            DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.CREDENTIAL_SAVED)
                    .build();

            assertNotNull(event);
            assertEquals(DcpAuditEventType.CREDENTIAL_SAVED, event.getEventType());
            assertNotNull(event.getTimestamp(), "timestamp must be set by build()");
        }

        @Test
        @DisplayName("build() sets timestamp to current time")
        void build_setsTimestamp() {
            long before = System.currentTimeMillis();
            DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.KEY_ROTATED)
                    .build();
            long after = System.currentTimeMillis();

            long ts = event.getTimestamp().toEpochMilli();
            assertTrue(ts >= before && ts <= after,
                    "timestamp should be between before and after build()");
        }

        @Test
        @DisplayName("build() propagates all optional fields")
        void build_allFields() {
            Map<String, Object> details = Map.of("format", "jwt", "count", 2);
            DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.CREDENTIAL_DELIVERED)
                    .description("Delivered 2 credentials")
                    .source("issuer")
                    .holderDid("did:web:holder.example.com")
                    .issuerDid("did:web:issuer.example.com")
                    .credentialTypes(List.of("MembershipCredential"))
                    .requestId("req-abc-123")
                    .details(details)
                    .build();

            assertEquals(DcpAuditEventType.CREDENTIAL_DELIVERED, event.getEventType());
            assertEquals("Delivered 2 credentials", event.getDescription());
            assertEquals("issuer", event.getSource());
            assertEquals("did:web:holder.example.com", event.getHolderDid());
            assertEquals("did:web:issuer.example.com", event.getIssuerDid());
            assertEquals(List.of("MembershipCredential"), event.getCredentialTypes());
            assertEquals("req-abc-123", event.getRequestId());
            assertEquals(details, event.getDetails());
        }

        @Test
        @DisplayName("build() throws ValidationException when eventType is null")
        void build_throwsWhenEventTypeNull() {
            assertThrows(ValidationException.class, () ->
                    DcpAuditEvent.Builder.newInstance().build());
        }

        @Test
        @DisplayName("Two builds from separate Builder instances are independent")
        void build_independentInstances() {
            DcpAuditEvent e1 = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.CREDENTIAL_SAVED)
                    .source("holder")
                    .build();
            DcpAuditEvent e2 = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.KEY_ROTATED)
                    .source("issuer")
                    .build();

            assertNotSame(e1, e2);
            assertEquals(DcpAuditEventType.CREDENTIAL_SAVED, e1.getEventType());
            assertEquals(DcpAuditEventType.KEY_ROTATED, e2.getEventType());
            assertEquals("holder", e1.getSource());
            assertEquals("issuer", e2.getSource());
        }
    }
}

