package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.DataPlaneNotFoundException;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.repository.DataPlaneRegistrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataPlaneRegistrationServiceTest {

    @Mock
    private DataPlaneRegistrationRepository repository;

    @InjectMocks
    private DataPlaneRegistrationService service;

    private DataPlaneRegistration buildRegistration() {
        return DataPlaneRegistration.Builder.newInstance()
                .endpoint("http://dataplane.example.com")
                .supportedTransferTypes(Set.of("HttpData-PULL"))
                .build();
    }

    @Test
    @DisplayName("register saves and returns the registration")
    public void registerSavesAndReturns() {
        DataPlaneRegistration reg = buildRegistration();
        when(repository.save(any(DataPlaneRegistration.class))).thenReturn(reg);

        DataPlaneRegistration result = service.register(reg);

        assertNotNull(result);
        assertEquals(reg.getEndpoint(), result.getEndpoint());
        verify(repository).save(reg);
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
        DataPlaneRegistration reg = buildRegistration();
        when(repository.findById(id)).thenReturn(Optional.of(reg));
        doNothing().when(repository).deleteById(id);

        service.deregister(id);

        verify(repository).deleteById(id);
    }

    @Test
    @DisplayName("deregister throws DataPlaneNotFoundException when id does not exist")
    public void deregisterThrowsWhenNotFound() {
        String id = "non-existent-id";
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(DataPlaneNotFoundException.class, () -> service.deregister(id));
        verify(repository, never()).deleteById(any());
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
}
