package it.eng.datatransfer.router;

import it.eng.datatransfer.model.DataPlaneRegistration;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DataPlaneRouterTest {

    @Mock
    private DataPlaneRegistrationService registrationService;

    @InjectMocks
    private DataPlaneRouter router;

    private DataPlaneRegistration buildRegistration(String endpoint) {
        return DataPlaneRegistration.Builder.newInstance()
                .endpoint(endpoint)
                .supportedTransferTypes(Set.of("HttpData-PULL"))
                .build();
    }

    private DataPlaneRegistration buildRegistrationWithProfile(String endpoint, Set<String> profiles) {
        return DataPlaneRegistration.Builder.newInstance()
                .endpoint(endpoint)
                .supportedTransferTypes(Set.of("HttpData-PULL"))
                .transportProfiles(profiles)
                .build();
    }

    @Test
    @DisplayName("selectsEndpointForKnownTransferType - one DP registered, returns it")
    public void selectsEndpointForKnownTransferType() {
        DataPlaneRegistration reg = buildRegistration("http://dp1:9090");
        when(registrationService.findByTransferType("HttpData-PULL")).thenReturn(List.of(reg));

        Optional<DataPlaneRegistration> result = router.selectDataPlane("HttpData-PULL");

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
        when(registrationService.findByTransferType("HttpData-PULL")).thenReturn(List.of(reg1, reg2));

        Optional<DataPlaneRegistration> first = router.selectDataPlane("HttpData-PULL");
        Optional<DataPlaneRegistration> second = router.selectDataPlane("HttpData-PULL");

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertNotEquals(first.get().getEndpoint(), second.get().getEndpoint());
    }

    @Test
    @DisplayName("selectDataPlaneByProfile - only DP advertising the profile is returned")
    public void selectDataPlaneByProfileFiltersOnProfile() {
        DataPlaneRegistration grpcDp = buildRegistrationWithProfile("http://dp-grpc:9090", Set.of("stream:grpc"));
        DataPlaneRegistration httpDp = buildRegistration("http://dp-http:9090");
        when(registrationService.findByTransferType("HttpData-PULL")).thenReturn(List.of(httpDp, grpcDp));

        Optional<DataPlaneRegistration> result = router.selectDataPlane("HttpData-PULL", "proc-1", "stream:grpc");

        assertTrue(result.isPresent());
        assertEquals("http://dp-grpc:9090", result.get().getEndpoint());
    }

    @Test
    @DisplayName("selectDataPlane sticky - same processId always returns the same DP")
    public void selectDataPlaneStickyByProcessId() {
        DataPlaneRegistration reg1 = buildRegistration("http://dp1:9090");
        DataPlaneRegistration reg2 = buildRegistration("http://dp2:9090");
        when(registrationService.findByTransferType("HttpData-PULL")).thenReturn(List.of(reg1, reg2));

        Optional<DataPlaneRegistration> first = router.selectDataPlane("HttpData-PULL", "proc-sticky", null);
        Optional<DataPlaneRegistration> second = router.selectDataPlane("HttpData-PULL", "proc-sticky", null);

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(first.get().getEndpoint(), second.get().getEndpoint(),
                "sticky routing must return the same DP for repeated calls with the same processId");
    }

    @Test
    @DisplayName("selectDataPlane different processIds - may land on different DPs")
    public void selectDataPlaneDifferentProcessIdsCanReturnDifferentDps() {
        DataPlaneRegistration reg1 = buildRegistration("http://dp1:9090");
        DataPlaneRegistration reg2 = buildRegistration("http://dp2:9090");
        when(registrationService.findByTransferType("HttpData-PULL")).thenReturn(List.of(reg1, reg2));

        Optional<DataPlaneRegistration> first = router.selectDataPlane("HttpData-PULL", "proc-A", null);
        Optional<DataPlaneRegistration> second = router.selectDataPlane("HttpData-PULL", "proc-B", null);

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        // With two DPs and two different processIds, round-robin should assign different ones
        assertNotEquals(first.get().getEndpoint(), second.get().getEndpoint());
    }

    @Test
    @DisplayName("selectDataPlane - throws IllegalStateException when no DP supports requested profile")
    public void selectDataPlaneThrowsWhenNoProfileMatch() {
        DataPlaneRegistration httpDp = buildRegistration("http://dp-http:9090");
        when(registrationService.findByTransferType("HttpData-PULL")).thenReturn(List.of(httpDp));

        assertThrows(IllegalStateException.class,
                () -> router.selectDataPlane("HttpData-PULL", "proc-2", "stream:grpc"));
    }

    @Test
    @DisplayName("selectDataPlane - throws when profile requested but no DPs registered at all")
    public void selectDataPlaneThrowsWhenNoDpsAndProfileRequested() {
        when(registrationService.findByTransferType("stream:grpc")).thenReturn(List.of());

        assertThrows(IllegalStateException.class,
                () -> router.selectDataPlane("stream:grpc", "proc-3", "stream:grpc"));
    }

    @Test
    @DisplayName("clearStickyAssignment - removes sticky entry so next call re-selects freely")
    public void clearStickyAssignmentRemovesStickyEntry() {
        DataPlaneRegistration reg1 = buildRegistration("http://dp1:9090");
        DataPlaneRegistration reg2 = buildRegistration("http://dp2:9090");
        when(registrationService.findByTransferType("HttpData-PULL")).thenReturn(List.of(reg1, reg2));

        Optional<DataPlaneRegistration> first = router.selectDataPlane("HttpData-PULL", "proc-clr", null);
        assertTrue(first.isPresent());
        String pinnedEndpoint = first.get().getEndpoint();

        Optional<DataPlaneRegistration> sticky = router.selectDataPlane("HttpData-PULL", "proc-clr", null);
        assertEquals(pinnedEndpoint, sticky.get().getEndpoint(), "should be pinned before clear");

        router.clearStickyAssignment("proc-clr");

        // Counter is at index 1 after first selection, so next free pick returns the other DP
        Optional<DataPlaneRegistration> afterClear = router.selectDataPlane("HttpData-PULL", "proc-clr", null);
        assertTrue(afterClear.isPresent());
        assertNotEquals(pinnedEndpoint, afterClear.get().getEndpoint(),
                "after clearing sticky, round-robin should re-select a different DP");
    }
}
