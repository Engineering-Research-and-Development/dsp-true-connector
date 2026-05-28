package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.DataPlaneNotFoundException;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.repository.DataPlaneRegistrationRepository;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
                .build();
    }

    @Test
    @DisplayName("register saves and returns the registration")
    public void registerSavesAndReturns() {
        DataPlaneRegistration reg = buildRegistration("registered-id");
        when(repository.save(any(DataPlaneRegistration.class))).thenReturn(reg);

        DataPlaneRegistration result = service.register(reg);

        assertNotNull(result);
        assertEquals(reg.getEndpoint(), result.getEndpoint());
        verify(repository).save(reg);
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATAPLANE_REGISTERED), any(String.class), any());
        verify(applicationEventPublisher).publishEvent((Object) argThat(event ->
                hasProperty(event, "changeType", "REGISTERED")
                        && hasProperty(event, "dataplaneId", reg.getId())
                        && hasProperty(event, "endpoint", reg.getEndpoint())));
    }

    @Test
    @DisplayName("register publishes registration changed event when updating an existing endpoint")
    public void registerExistingEndpointPublishesUpdatedEvent() {
        DataPlaneRegistration existing = buildRegistration("existing-id");
        DataPlaneRegistration incoming = buildRegistration("new-id");
        when(repository.findByEndpoint(existing.getEndpoint())).thenReturn(Optional.of(existing));
        when(repository.save(any(DataPlaneRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DataPlaneRegistration result = service.register(incoming);

        assertNotNull(result);
        assertEquals(incoming.getId(), result.getId());
        verify(repository).deleteById(existing.getId());
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATAPLANE_REGISTRATION_UPDATED), any(String.class), any());
        verify(applicationEventPublisher).publishEvent((Object) argThat(event ->
                hasProperty(event, "changeType", "REGISTERED")
                        && hasProperty(event, "dataplaneId", incoming.getId())
                        && hasProperty(event, "endpoint", incoming.getEndpoint())));
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
        DataPlaneRegistration reg = buildRegistration(id);
        when(repository.findById(id)).thenReturn(Optional.of(reg));
        doNothing().when(repository).deleteById(id);

        service.deregister(id);

        verify(repository).deleteById(id);
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATAPLANE_DEREGISTERED), any(String.class), any());
        verify(applicationEventPublisher).publishEvent((Object) argThat(event ->
                hasProperty(event, "changeType", "DEREGISTERED")
                        && hasProperty(event, "dataplaneId", reg.getId())
                        && hasProperty(event, "endpoint", reg.getEndpoint())));
    }

    @Test
    @DisplayName("deregister throws DataPlaneNotFoundException when id does not exist")
    public void deregisterThrowsWhenNotFound() {
        String id = "non-existent-id";
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(DataPlaneNotFoundException.class, () -> service.deregister(id));
        verify(repository, never()).deleteById(any());
        verify(auditEventPublisher).publishEvent(eq(AuditEventType.DATAPLANE_REGISTRATION_NOT_FOUND), any(String.class), any());
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

    private boolean hasProperty(Object target, String accessorName, String expectedValue) {
        try {
            Method accessor = target.getClass().getMethod(accessorName);
            Object actualValue = accessor.invoke(target);
            return expectedValue.equals(String.valueOf(actualValue));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
