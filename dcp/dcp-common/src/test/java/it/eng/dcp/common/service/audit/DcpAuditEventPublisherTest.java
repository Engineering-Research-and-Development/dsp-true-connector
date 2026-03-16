package it.eng.dcp.common.service.audit;

import it.eng.dcp.common.audit.DcpAuditEvent;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.audit.DcpAuditProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DcpAuditEventPublisher}.
 *
 * <p>Uses {@link ArgumentCaptor} to verify the exact {@link DcpAuditEvent}
 * passed to {@link ApplicationEventPublisher#publishEvent(Object)}.
 */
@ExtendWith(MockitoExtension.class)
class DcpAuditEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Captor
    private ArgumentCaptor<DcpAuditEvent> eventCaptor;

    private DcpAuditEventPublisher publisher;

    @BeforeEach
    void setUp() {
        var enabledProps = new DcpAuditProperties(); // enabled=true by default
        publisher = new DcpAuditEventPublisher(applicationEventPublisher, enabledProps);
    }

    // =========================================================================
    // publishEvent(type, description, source, holderDid, issuerDid, …)
    // =========================================================================

    @Nested
    @DisplayName("publishEvent(DcpAuditEventType, …) — convenience method")
    class ConveniencePublish {

        @Test
        @DisplayName("publishes event with correct eventType")
        void publishesCorrectEventType() {
            publisher.publishEvent(
                    DcpAuditEventType.CREDENTIAL_SAVED,
                    "Credential saved",
                    "holder",
                    "did:web:holder.example.com",
                    "did:web:issuer.example.com",
                    List.of("MembershipCredential"),
                    "req-001",
                    Map.of("format", "jwt"));

            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            DcpAuditEvent captured = eventCaptor.getValue();

            assertEquals(DcpAuditEventType.CREDENTIAL_SAVED, captured.getEventType());
        }

        @Test
        @DisplayName("publishes event with all supplied fields")
        void publishesAllFields() {
            Map<String, Object> details = Map.of("count", 1);
            publisher.publishEvent(
                    DcpAuditEventType.CREDENTIAL_DELIVERED,
                    "1 credential delivered",
                    "issuer",
                    "did:web:holder.example.com",
                    "did:web:issuer.example.com",
                    List.of("MembershipCredential"),
                    "req-xyz",
                    details);

            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            DcpAuditEvent e = eventCaptor.getValue();

            assertAll(
                    () -> assertEquals(DcpAuditEventType.CREDENTIAL_DELIVERED, e.getEventType()),
                    () -> assertEquals("1 credential delivered", e.getDescription()),
                    () -> assertEquals("issuer", e.getSource()),
                    () -> assertEquals("did:web:holder.example.com", e.getHolderDid()),
                    () -> assertEquals("did:web:issuer.example.com", e.getIssuerDid()),
                    () -> assertEquals(List.of("MembershipCredential"), e.getCredentialTypes()),
                    () -> assertEquals("req-xyz", e.getRequestId()),
                    () -> assertEquals(details, e.getDetails()),
                    () -> assertNotNull(e.getTimestamp())
            );
        }

        @Test
        @DisplayName("publishes event with null optional fields")
        void publishesWithNullOptionalFields() {
            publisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Token validation failed",
                    "verifier",
                    null, null, null, null, null);

            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            DcpAuditEvent e = eventCaptor.getValue();

            assertEquals(DcpAuditEventType.TOKEN_VALIDATION_FAILED, e.getEventType());
            assertNull(e.getHolderDid());
            assertNull(e.getIssuerDid());
        }

        @Test
        @DisplayName("does not propagate exception when publisher throws")
        void doesNotPropagatePublisherException() {
            doThrow(new RuntimeException("Bus failure"))
                    .when(applicationEventPublisher).publishEvent(any());

            assertDoesNotThrow(() -> publisher.publishEvent(
                    DcpAuditEventType.KEY_ROTATED,
                    "Key rotated",
                    "issuer",
                    null, null, null, "alias-new", null));
        }

        @Test
        @DisplayName("publishes exactly once per call")
        void publishedExactlyOnce() {
            publisher.publishEvent(
                    DcpAuditEventType.PRESENTATION_VERIFIED,
                    "Presentation verified",
                    "verifier",
                    "did:web:holder.example.com",
                    null, null, null, null);

            verify(applicationEventPublisher, times(1)).publishEvent(any(DcpAuditEvent.class));
        }

        @Test
        @DisplayName("KEY_ROTATED event carries correct source and requestId")
        void keyRotatedEvent() {
            publisher.publishEvent(
                    DcpAuditEventType.KEY_ROTATED,
                    "Signing key rotated",
                    "holder",
                    null, null, null, "new-alias-42", null);

            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            DcpAuditEvent e = eventCaptor.getValue();

            assertEquals(DcpAuditEventType.KEY_ROTATED, e.getEventType());
            assertEquals("holder", e.getSource());
            assertEquals("new-alias-42", e.getRequestId());
        }
    }

    // =========================================================================
    // publishEvent(DcpAuditEvent) — direct overload
    // =========================================================================

    @Nested
    @DisplayName("publishEvent(DcpAuditEvent) — direct overload")
    class DirectPublish {

        @Test
        @DisplayName("publishes the exact event instance supplied")
        void publishesExactInstance() {
            DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.PRESENTATION_INVALID)
                    .source("verifier")
                    .description("Presentation JWT signature mismatch")
                    .build();

            publisher.publishEvent(event);

            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            assertSame(event, eventCaptor.getValue());
        }

        @Test
        @DisplayName("does not propagate exception when publisher throws")
        void doesNotPropagatePublisherException() {
            DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.CREDENTIAL_REJECTED_BY_ISSUER)
                    .build();
            doThrow(new RuntimeException("Bus failure"))
                    .when(applicationEventPublisher).publishEvent(any());

            assertDoesNotThrow(() -> publisher.publishEvent(event));
        }
    }

    // =========================================================================
    // dcp.audit.enabled = false — publisher must be a no-op
    // =========================================================================

    @Nested
    @DisplayName("When dcp.audit.enabled=false, publisher is a no-op")
    class DisabledPublisher {

        private DcpAuditEventPublisher disabledPublisher;

        @BeforeEach
        void setUp() {
            DcpAuditProperties disabledProps = new DcpAuditProperties();
            disabledProps.setEnabled(false);
            disabledPublisher = new DcpAuditEventPublisher(applicationEventPublisher, disabledProps);
        }

        @Test
        @DisplayName("convenience method does not invoke ApplicationEventPublisher")
        void convenienceMethodSkipsPublish() {
            disabledPublisher.publishEvent(
                    DcpAuditEventType.CREDENTIAL_SAVED,
                    "Credential saved",
                    "holder",
                    null, null, null, null, null);

            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        @DisplayName("direct overload does not invoke ApplicationEventPublisher")
        void directOverloadSkipsPublish() {
            DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.KEY_ROTATED)
                    .source("issuer")
                    .build();

            disabledPublisher.publishEvent(event);

            verifyNoInteractions(applicationEventPublisher);
        }
    }

    // =========================================================================
    // Event type coverage — one assertion per group
    // =========================================================================

    @Nested
    @DisplayName("Event type coverage")
    class EventTypeCoverage {

        private void publish(DcpAuditEventType type) {
            publisher.publishEvent(type, type.toString(), "test", null, null, null, null, null);
        }

        @Test @DisplayName("CREDENTIAL_REQUEST_RECEIVED is publishable")
        void credentialRequestReceived() { publish(DcpAuditEventType.CREDENTIAL_REQUEST_RECEIVED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("CREDENTIAL_APPROVED is publishable")
        void credentialApproved() { publish(DcpAuditEventType.CREDENTIAL_APPROVED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("CREDENTIAL_DENIED is publishable")
        void credentialDenied() { publish(DcpAuditEventType.CREDENTIAL_DENIED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("CREDENTIAL_DELIVERED is publishable")
        void credentialDelivered() { publish(DcpAuditEventType.CREDENTIAL_DELIVERED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("CREDENTIAL_DELIVERY_FAILED is publishable")
        void credentialDeliveryFailed() { publish(DcpAuditEventType.CREDENTIAL_DELIVERY_FAILED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("CREDENTIAL_REVOKED is publishable")
        void credentialRevoked() { publish(DcpAuditEventType.CREDENTIAL_REVOKED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("CREDENTIAL_REQUESTED is publishable")
        void credentialRequested() { publish(DcpAuditEventType.CREDENTIAL_REQUESTED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("CREDENTIAL_OFFER_RECEIVED is publishable")
        void credentialOfferReceived() { publish(DcpAuditEventType.CREDENTIAL_OFFER_RECEIVED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("CREDENTIAL_SAVED is publishable")
        void credentialSaved() { publish(DcpAuditEventType.CREDENTIAL_SAVED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("CREDENTIAL_UNTRUSTED_ISSUER is publishable")
        void credentialUntrustedIssuer() { publish(DcpAuditEventType.CREDENTIAL_UNTRUSTED_ISSUER); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("CREDENTIAL_REJECTED_BY_ISSUER is publishable")
        void credentialRejectedByIssuer() { publish(DcpAuditEventType.CREDENTIAL_REJECTED_BY_ISSUER); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("PRESENTATION_QUERY_RECEIVED is publishable")
        void presentationQueryReceived() { publish(DcpAuditEventType.PRESENTATION_QUERY_RECEIVED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("PRESENTATION_CREATED is publishable")
        void presentationCreated() { publish(DcpAuditEventType.PRESENTATION_CREATED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("PRESENTATION_QUERY_SENT is publishable")
        void presentationQuerySent() { publish(DcpAuditEventType.PRESENTATION_QUERY_SENT); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("PRESENTATION_VERIFIED is publishable")
        void presentationVerified() { publish(DcpAuditEventType.PRESENTATION_VERIFIED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("PRESENTATION_INVALID is publishable")
        void presentationInvalid() { publish(DcpAuditEventType.PRESENTATION_INVALID); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("SELF_ISSUED_TOKEN_VALIDATED is publishable")
        void selfIssuedTokenValidated() { publish(DcpAuditEventType.SELF_ISSUED_TOKEN_VALIDATED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("TOKEN_VALIDATION_FAILED is publishable")
        void tokenValidationFailed() { publish(DcpAuditEventType.TOKEN_VALIDATION_FAILED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("TOKEN_ISSUED is publishable")
        void tokenIssued() { publish(DcpAuditEventType.TOKEN_ISSUED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("IDENTITY_VERIFIED is publishable")
        void identityVerified() { publish(DcpAuditEventType.IDENTITY_VERIFIED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("IDENTITY_VERIFICATION_FAILED is publishable")
        void identityVerificationFailed() { publish(DcpAuditEventType.IDENTITY_VERIFICATION_FAILED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }

        @Test @DisplayName("KEY_ROTATED is publishable")
        void keyRotated() { publish(DcpAuditEventType.KEY_ROTATED); verify(applicationEventPublisher).publishEvent(any(DcpAuditEvent.class)); }
    }

    // =========================================================================
    // Listener unit test (inline — no Spring context needed)
    // =========================================================================

    @Nested
    @DisplayName("DcpAuditEventListener")
    class ListenerTests {

        @Mock
        private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

        @Mock
        private it.eng.dcp.common.repository.DcpAuditEventRepository auditEventRepository;

        @Test
        @DisplayName("handleAuditEvent uses repository for default collection name")
        void savesToDefaultCollectionViaRepository() {
            DcpAuditProperties props = new DcpAuditProperties();
            props.setCollectionName("dcp_audit_events");
            DcpAuditEventListener listener = new DcpAuditEventListener(auditEventRepository, mongoTemplate, props);

            DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.CREDENTIAL_SAVED)
                    .source("holder")
                    .build();

            listener.handleAuditEvent(event);

            ArgumentCaptor<DcpAuditEvent> entityCaptor = ArgumentCaptor.forClass(DcpAuditEvent.class);
            verify(auditEventRepository).save(entityCaptor.capture());
            assertSame(event, entityCaptor.getValue());
            verify(mongoTemplate, never()).save(any(), anyString());
        }

        @Test
        @DisplayName("handleAuditEvent uses MongoTemplate for overridden collection name")
        void savesToOverriddenCollectionViaMongoTemplate() {
            DcpAuditProperties props = new DcpAuditProperties();
            props.setCollectionName("audit_events");
            DcpAuditEventListener listener = new DcpAuditEventListener(auditEventRepository, mongoTemplate, props);

            DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.PRESENTATION_VERIFIED)
                    .source("verifier")
                    .build();

            listener.handleAuditEvent(event);

            ArgumentCaptor<String> collectionCaptor = ArgumentCaptor.forClass(String.class);
            verify(mongoTemplate).save(any(DcpAuditEvent.class), collectionCaptor.capture());
            assertEquals("audit_events", collectionCaptor.getValue());
            verify(auditEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("handleAuditEvent does not propagate repository exception")
        void doesNotPropagateRepositoryException() {
            DcpAuditProperties props = new DcpAuditProperties();
            DcpAuditEventListener listener = new DcpAuditEventListener(auditEventRepository, mongoTemplate, props);

            doThrow(new RuntimeException("Mongo down"))
                    .when(auditEventRepository).save(any());

            DcpAuditEvent event = DcpAuditEvent.Builder.newInstance()
                    .eventType(DcpAuditEventType.KEY_ROTATED)
                    .source("issuer")
                    .build();

            assertDoesNotThrow(() -> listener.handleAuditEvent(event));
        }
    }
}


