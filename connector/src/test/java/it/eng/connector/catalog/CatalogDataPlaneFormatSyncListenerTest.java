package it.eng.connector.catalog;

import it.eng.datatransfer.event.DataPlaneRegistrationChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CatalogDataPlaneFormatSyncListenerTest {

    @Mock
    private CatalogDataPlaneFormatSyncService catalogDataPlaneFormatSyncService;

    @InjectMocks
    private CatalogDataPlaneFormatSyncListener listener;

    @Test
    @DisplayName("handleRegistrationChange reconciles catalog distributions when a dataplane registers")
    void handleRegistrationChangeReconcilesCatalogDistributionsWhenADataPlaneRegisters() {
        DataPlaneRegistrationChangedEvent event = new DataPlaneRegistrationChangedEvent(
                DataPlaneRegistrationChangedEvent.ChangeType.REGISTERED,
                "dp-id",
                "http://dataplane");

        listener.handleRegistrationChange(event);

        verify(catalogDataPlaneFormatSyncService).reconcileCatalogDistributions();
    }

    @Test
    @DisplayName("handleRegistrationChange reconciles catalog distributions when a dataplane deregisters")
    void handleRegistrationChangeReconcilesCatalogDistributionsWhenADataPlaneDeregisters() {
        DataPlaneRegistrationChangedEvent event = new DataPlaneRegistrationChangedEvent(
                DataPlaneRegistrationChangedEvent.ChangeType.DEREGISTERED,
                "dp-id",
                "http://dataplane");

        listener.handleRegistrationChange(event);

        verify(catalogDataPlaneFormatSyncService).reconcileCatalogDistributions();
    }
}
