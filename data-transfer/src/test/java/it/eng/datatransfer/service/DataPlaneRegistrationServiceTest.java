package it.eng.datatransfer.service;

import it.eng.datatransfer.event.DataPlaneRegistrationChangedEvent;
import it.eng.datatransfer.exceptions.DataPlaneNotFoundException;
import it.eng.datatransfer.exceptions.DataPlaneUnauthorizedException;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.repository.DataPlaneRegistrationRepository;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.security.ApiKeyHasher;
import it.eng.tools.service.AuditEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataPlaneRegistrationServiceTest {

    @Mock
    private DataPlaneRegistrationRepository repository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private ApiKeyHasher apiKeyHasher;

    @Captor
    private ArgumentCaptor<DataPlaneRegistrationChangedEvent> dataPlaneRegistrationChangedEventCaptor;

    @Captor
    private ArgumentCaptor<DataPlaneRegistration> dataPlaneRegistrationCaptor;

    @InjectMocks
    private DataPlaneRegistrationService service;

    private DataPlaneRegistration buildRegistration() {
        return buildRegistration(null);
    }

    private DataPlaneRegistration buildRegistration(String id) {
        return DataPlaneRegistration.Builder.newInstance()
                .id(id)
                .endpoint("http://dataplane.example.com")
                .supportedTransferTypes(Set.of("HttpData-PULL"))
                .apiKey("raw-api-key-1234567890")
                .build();
    }

    @Test
    @DisplayName("register saves and returns the registration")
    public void registerSavesAndReturns() {
        DataPlaneRegistration reg = buildRegistration("registered-id");
        when(apiKeyHasher.hash(reg.getApiKey())).thenReturn("hashed-api-key");
        when(repository.save(any(DataPlaneRegistration.class))).thenReturn(reg);

        DataPlaneRegistration result = service.register(reg);

        assertNotNull(result);
        assertEquals(reg.getEndpoint(), result.getEndpoint());
        verify(repository).save(reg);
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATAPLANE_REGISTERED), any(String.class), any());
        assertPublishedRegistrationChangedEvent(DataPlaneRegistrationChangedEvent.ChangeType.REGISTERED,
                reg.getId(), reg.getEndpoint());
    }

    @Test
    @DisplayName("register hashes API key before persisting")
    public void registerHashesApiKeyBeforePersisting() {
        DataPlaneRegistration reg = buildRegistration("registered-id");
        when(apiKeyHasher.hash(reg.getApiKey())).thenReturn("hashed-api-key");
        when(repository.save(any(DataPlaneRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DataPlaneRegistration result = service.register(reg);

        assertNotNull(result);
        verify(apiKeyHasher).hash(reg.getApiKey());
        verify(repository).save(dataPlaneRegistrationCaptor.capture());
        DataPlaneRegistration saved = dataPlaneRegistrationCaptor.getValue();
        assertEquals("hashed-api-key", saved.getApiKey());
        assertEquals("raw-api-", saved.getApiKeyHint());
    }

    @Test
    @DisplayName("register publishes registration changed event when updating an existing endpoint")
    public void registerExistingEndpointPublishesUpdatedEvent() {
        DataPlaneRegistration existing = buildRegistration("existing-id");
        DataPlaneRegistration incoming = buildRegistration("new-id");
        when(repository.findByEndpoint(existing.getEndpoint())).thenReturn(Optional.of(existing));
        when(apiKeyHasher.hash(incoming.getApiKey())).thenReturn("hashed-api-key");
        when(repository.save(any(DataPlaneRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DataPlaneRegistration result = service.register(incoming);

        assertNotNull(result);
        assertEquals(incoming.getId(), result.getId());
        verify(repository).deleteById(existing.getId());
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATAPLANE_REGISTRATION_UPDATED), any(String.class), any());
        assertPublishedRegistrationChangedEvent(DataPlaneRegistrationChangedEvent.ChangeType.REGISTERED,
                incoming.getId(), incoming.getEndpoint());
    }

    @Test
    @DisplayName("findByTransferType returns matching registrations")
    public void findByTransferTypeReturnsMatchingRegistrations() {
        DataPlaneRegistration reg = buildRegistration();
        when(repository.findBySupportedTransferTypesContaining("HttpData-PULL"))
                .thenReturn(List.of(reg));

        List<DataPlaneRegistration> results = service.findByTransferType("HttpData-PULL");

        assertEquals(1, results.size());
        assertEquals(reg.getEndpoint(), results.get(0).getEndpoint());
        verify(repository).findBySupportedTransferTypesContaining("HttpData-PULL");
    }

    @Test
    @DisplayName("deregister calls deleteById with the given id")
    public void deregisterDeletesById() {
        String id = "test-id-123";
        String rawApiKey = "raw-api-key-1234567890";
        DataPlaneRegistration reg = buildRegistration(id);
        when(repository.findById(id)).thenReturn(Optional.of(reg));
        when(apiKeyHasher.matches(rawApiKey, reg.getApiKey())).thenReturn(true);
        doNothing().when(repository).deleteById(id);

        service.deregister(id, rawApiKey);

        verify(repository).deleteById(id);
        verify(apiKeyHasher).matches(rawApiKey, reg.getApiKey());
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATAPLANE_DEREGISTERED), any(String.class), any());
        assertPublishedRegistrationChangedEvent(DataPlaneRegistrationChangedEvent.ChangeType.DEREGISTERED,
                reg.getId(), reg.getEndpoint());
    }

    @Test
    @DisplayName("deregister throws DataPlaneNotFoundException when id does not exist")
    public void deregisterThrowsWhenNotFound() {
        String id = "non-existent-id";
        String rawApiKey = "raw-api-key-1234567890";
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(DataPlaneNotFoundException.class, () -> service.deregister(id, rawApiKey));
        verify(repository, never()).deleteById(any());
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATAPLANE_REGISTRATION_NOT_FOUND), any(String.class), any());
    }

    @Test
    @DisplayName("findByApiKey hashes before repository lookup")
    public void findByApiKeyHashesBeforeLookup() {
        DataPlaneRegistration reg = buildRegistration("registered-id");
        when(apiKeyHasher.hash("raw-api-key-1234567890")).thenReturn("hashed-api-key");
        when(repository.findByApiKey("hashed-api-key")).thenReturn(Optional.of(reg));

        Optional<DataPlaneRegistration> result = service.findByApiKey("raw-api-key-1234567890");

        assertTrue(result.isPresent());
        assertEquals(reg.getId(), result.get().getId());
        verify(apiKeyHasher).hash("raw-api-key-1234567890");
        verify(repository).findByApiKey("hashed-api-key");
    }

    @Test
    @DisplayName("deregister succeeds when API key matches stored hash")
    public void deregisterSucceedsWithMatchingKey() {
        String id = "test-id-123";
        String rawApiKey = "raw-api-key-1234567890";
        DataPlaneRegistration reg = buildRegistration(id);
        when(repository.findById(id)).thenReturn(Optional.of(reg));
        when(apiKeyHasher.matches(rawApiKey, reg.getApiKey())).thenReturn(true);

        service.deregister(id, rawApiKey);

        verify(repository).deleteById(id);
        verify(apiKeyHasher).matches(rawApiKey, reg.getApiKey());
    }

    @Test
    @DisplayName("deregister throws when API key does not match stored hash")
    public void deregisterThrowsWithMismatchedKey() {
        String id = "test-id-123";
        String rawApiKey = "wrong-raw-api-key";
        DataPlaneRegistration reg = buildRegistration(id);
        when(repository.findById(id)).thenReturn(Optional.of(reg));
        when(apiKeyHasher.matches(rawApiKey, reg.getApiKey())).thenReturn(false);

        assertThrows(DataPlaneUnauthorizedException.class, () -> service.deregister(id, rawApiKey));

        verify(repository, never()).deleteById(any());
        verify(apiKeyHasher).matches(rawApiKey, reg.getApiKey());
    }

    @Test
    @DisplayName("findAll returns all registrations")
    public void findAllReturnsAll() {
        DataPlaneRegistration reg1 = buildRegistration();
        DataPlaneRegistration reg2 = DataPlaneRegistration.Builder.newInstance()
                .endpoint("http://dataplane2.example.com")
                .supportedTransferTypes(Set.of("HttpData-PUSH"))
                .build();
        when(repository.findAll()).thenReturn(List.of(reg1, reg2));

        List<DataPlaneRegistration> results = service.findAll();

        assertEquals(2, results.size());
        verify(repository).findAll();
    }

    private void assertPublishedRegistrationChangedEvent(DataPlaneRegistrationChangedEvent.ChangeType expectedChangeType,
                                                         String expectedDataplaneId,
                                                         String expectedEndpoint) {
        verify(applicationEventPublisher).publishEvent(dataPlaneRegistrationChangedEventCaptor.capture());
        DataPlaneRegistrationChangedEvent event = dataPlaneRegistrationChangedEventCaptor.getValue();
        assertEquals(expectedChangeType, event.changeType());
        assertEquals(expectedDataplaneId, event.dataplaneId());
        assertEquals(expectedEndpoint, event.endpoint());
    }
}
