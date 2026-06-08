package it.eng.datatransfer.router;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DataPlaneRouterTest {

    @Mock
    private DataPlaneRegistrationService registrationService;

    @InjectMocks
    private DataPlaneRouter router;

    private DataPlaneRegistration buildRegistration(String endpoint) {
        return DataPlaneRegistration.Builder.newInstance()
                .endpoint(endpoint)
                .supportedTransferTypes(Set.of(DataTransferFormat.HTTP_PULL.format()))
                .build();
    }

    @Test
    @DisplayName("selectsEndpointForKnownTransferType - one DP registered, returns it")
    public void selectsEndpointForKnownTransferType() {
        DataPlaneRegistration reg = buildRegistration("http://dp1:9090");
        when(registrationService.findByTransferType(DataTransferFormat.HTTP_PULL.format())).thenReturn(List.of(reg));

        Optional<DataPlaneRegistration> result = router.selectDataPlane(DataTransferFormat.HTTP_PULL.format());

        assertTrue(result.isPresent());
        assertEquals("http://dp1:9090", result.get().getEndpoint());
    }

    @Test
    @DisplayName("returnsEmptyForUnknownTransferType - no DPs registered, returns empty")
    public void returnsEmptyForUnknownTransferType() {
        when(registrationService.findByTransferType("Unknown-TYPE")).thenReturn(List.of());

        Optional<DataPlaneRegistration> result = router.selectDataPlane("Unknown-TYPE");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("roundRobinsAcrossMultipleInstances - two DPs, two calls return different endpoints")
    public void roundRobinsAcrossMultipleInstances() {
        DataPlaneRegistration reg1 = buildRegistration("http://dp1:9090");
        DataPlaneRegistration reg2 = buildRegistration("http://dp2:9090");
        when(registrationService.findByTransferType(DataTransferFormat.HTTP_PULL.format())).thenReturn(List.of(reg1, reg2));

        Optional<DataPlaneRegistration> first = router.selectDataPlane(DataTransferFormat.HTTP_PULL.format());
        Optional<DataPlaneRegistration> second = router.selectDataPlane(DataTransferFormat.HTTP_PULL.format());

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertNotEquals(first.get().getEndpoint(), second.get().getEndpoint());
    }
}
